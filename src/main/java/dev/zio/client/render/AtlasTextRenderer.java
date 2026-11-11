/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.client.FontsModule;
import dev.zio.client.settings.options.FontsStyleChoice;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import java.awt.Font;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;

public final class AtlasTextRenderer {
   private static final float ATLAS_SCALE = 0.25F;
   private static final Map<Character, ChatFormatting> FORMATTING_CODES = new Char2ObjectOpenHashMap<>();
   private static final FontsModule FONTS = (FontsModule)ClientContext.getModule(FontsModule.class);
   protected static final MeshBuffer glyphMesh = MeshBuffer.create();
   private static final ByteBufferBuilder byteBuffer = new ByteBufferBuilder(2048);
   private static final MultiBufferSource bufferSource = AtlasTextRenderer::getBuffer;
   private static final Map<RenderType, MeshBuffer> textBuffers = new HashMap<>();
   private static RenderType lastRenderType;
   private static int fontFlushCounter;

   static {
      for (ChatFormatting format : ChatFormatting.values()) {
         FORMATTING_CODES.put(format.getChar(), format);
      }
   }

   private final AtlasStringSplitter splitter = new AtlasStringSplitter(this);
   private final FontAtlas atlas;
   private float cursorX;
   private float cursorY;

   public AtlasTextRenderer(FontAtlas atlas) {
      this.atlas = atlas;
   }

   public void flush() {
      if (glyphMesh.isBuilding()) {
         boolean antiAlias = FONTS.antiAliasSetting.getValue();
         MeshDrawContext.setRenderType(ClientRenderTypes.font(FontAtlas.ATLAS_TEXTURE, antiAlias));
         MeshBuffer.draw(glyphMesh);
      }
      finishBuffers();
   }
   public MultiBufferSource getBufferSource() {
      return bufferSource;
   }

   public AtlasStringSplitter getSplitter() {
      return this.splitter;
   }

   public int drawCentered(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x - this.getWidth(text) / 2.0F, (float)y, argb, true);
   }

   public int getHeight() {
      return (int)(this.atlas.getHeight() * ATLAS_SCALE);
   }

   public static void finishBuffers() {
      // 26.1.2: antes immediateBuffers.endBatch() (draw inmediato — en la fase
      // extract ya no persiste, el texto se perdia). Cada RenderType acumulado
      // se somete por MeshDrawContext; si hay dren activo va a la cola del
      // GuiRenderer-endFrame y si no se dibuja inmediato (fase mundo).
      for (Map.Entry<RenderType, MeshBuffer> entry : textBuffers.entrySet()) {
         if (entry.getValue().isBuilding()) {
            MeshDrawContext.setRenderType(entry.getKey());
            MeshBuffer.draw(entry.getValue());
         }
      }
      // diagnostico temporal 26.1.2 (bug HUD text): cuantos pools por flush.
      fontFlushCounter++;
      if (fontFlushCounter % 300 == 0 && !textBuffers.isEmpty()) {
         System.err.println(
            "[zio-font] flushes=" + fontFlushCounter + " entries=" + textBuffers.size()
         );
      }
      textBuffers.clear();
      lastRenderType = null;
   }

   public int draw(PoseStack poses, String text, float x, float y, int argb) {
      return this.draw(poses, text, x, y, argb, true);
   }

   public int draw(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x, (float)y, argb, true);
   }

   public int draw(PoseStack poses, FormattedCharSequence sequence, float x, float y, int argb, boolean shadow) {
      int endX;
      if (shadow) {
         endX = new FormattedTextRenderer(poses, argb, 0.25F, this.atlas)
            .draw(sequence, x + FontRenderer.getShadowOffset(), y + FontRenderer.getShadowOffset());
         endX = Math.max(endX, new FormattedTextRenderer(poses, argb, 1.0F, this.atlas).draw(sequence, x, y));
      } else {
         endX = new FormattedTextRenderer(poses, argb, 1.0F, this.atlas).draw(sequence, x, y);
      }
      return endX;
   }

   public int draw(PoseStack poses, String text, float x, float y, int argb, boolean shadow) {
      if (!shadow) {
         return this.drawPass(poses, text, x, y, argb, false);
      }
      int shadowEnd = this.drawPass(
         poses, text, x + FontRenderer.getShadowOffset(), y + FontRenderer.getShadowOffset(), argb, true
      );
      return Math.max(shadowEnd, this.drawPass(poses, text, x, y, argb, false));
   }

   public static AtlasTextRenderer create(String fontName, int fontSize, FontsStyleChoice style, Set<FontLanguage> languages) {
      CharArraySet characters = new CharArraySet(estimateCharacterCount(languages));
      for (FontLanguage language : languages) {
         language.getRange().addTo(characters);
      }
      int awtStyle = switch (style) {
         case PLAIN -> Font.PLAIN;
         case BOLD -> Font.BOLD;
         case ITALIC -> Font.ITALIC;
         case BOLDITALIC -> Font.BOLD | Font.ITALIC;
      };
      FontAtlas atlas = new FontAtlas(new Font(fontName, awtStyle, fontSize));
      atlas.buildAtlas(characters);
      atlas.uploadTexture();
      return new AtlasTextRenderer(atlas);
   }

   private void renderString(PoseStack poses, String text, int baseColor) {
      int color = baseColor;
      poses.pushPose();
      try {
         poses.translate(FontRenderer.getTranslate(), FontRenderer.getShift(), 0.0F);
         poses.scale(ATLAS_SCALE, ATLAS_SCALE, 1.0F);
         if (!glyphMesh.isBuilding()) {
            glyphMesh.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
         }
         boolean formattingCode = false;
         for (char character : text.toCharArray()) {
            if (formattingCode) {
               formattingCode = false;
               ChatFormatting format = FORMATTING_CODES.get(Character.toLowerCase(character));
               if (format == null || format == ChatFormatting.RESET) {
                  color = baseColor;
               } else if (format.getColor() != null) {
                  color = format.getColor();
               }
            } else if (character == '\u00a7') {
               formattingCode = true;
            } else {
               this.cursorX += this.atlas.drawGlyph(poses, glyphMesh, character, this.cursorX, this.cursorY, color);
            }
         }
      } finally {
         poses.popPose();
      }
   }

   public float getCharacterWidth(char character) {
      return this.atlas.getGlyphWidth(character) * ATLAS_SCALE;
   }

   private int drawPass(PoseStack poses, String text, float x, float y, int argb, boolean shadowPass) {
      if (text == null) {
         return 0;
      }
      if ((argb >>> 24) == 0) {
         argb |= 0xFF000000;
      }
      if (shadowPass) {
         int alpha = argb & 0xFF000000;
         argb = alpha | ((argb & 0x00FCFCFC) >>> 2);
      }
      this.cursorX = x / ATLAS_SCALE;
      this.cursorY = y / ATLAS_SCALE;
      this.renderString(poses, text, argb);
      return (int)(this.cursorX * ATLAS_SCALE);
   }

   public int drawWithoutShadow(PoseStack poses, String text, float x, float y, int argb) {
      return this.draw(poses, text, x, y, argb, false);
   }

   public int drawWithoutShadow(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x, (float)y, argb, false);
   }

   private static int estimateCharacterCount(Collection<FontLanguage> languages) {
      int count = 512;
      if (languages.contains(FontLanguage.CHINESE)) {
         count += 2500;
      }
      if (languages.contains(FontLanguage.JAPANESE)) {
         count += 3000;
      }
      return count;
   }

   public int getWidth(String text) {
      if (text == null) {
         return 0;
      }
      float width = 0.0F;
      boolean formattingCode = false;
      for (char character : text.toCharArray()) {
         if (formattingCode) {
            formattingCode = false;
         } else if (character == '\u00a7') {
            formattingCode = true;
         } else {
            width += this.atlas.getGlyphWidth(character);
         }
      }
      return (int)(width * ATLAS_SCALE + 1.0F);
   }

   public int drawCenteredWithoutShadow(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x - this.getWidth(text) / 2.0F, (float)y, argb, false);
   }

   private static VertexConsumer getBuffer(RenderType renderType) {
      // paridad con el quirk del baseline (cambio de tipo limpia los buffers
      // para conservar el interleave de call-sites texto→quad→texto), pero
      // acumulando en buffers deferibles en vez del BufferSource vanilla.
      if (lastRenderType != null && lastRenderType != renderType) {
         GuiDrawUtils.flush();
      }

      lastRenderType = renderType;
      // 26.1.2: el extract ya no pinta; viene muestreado por MeshDrawContext
      // (dren en GameRenderer.render / draw inmediato fuera del extract).
      MeshBuffer buffer = textBuffers.get(renderType);
      if (buffer == null) {
         buffer = MeshBuffer.create(2048);
         buffer.begin(renderType.mode(), renderType.format());
         textBuffers.put(renderType, buffer);
      }
      return buffer;
   }
}
