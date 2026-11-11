/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import dev.zio.client.ClientAccess;

import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

public final class GuiDrawUtils implements ClientAccess {
   static final MeshBuffer quadBuffer = MeshBuffer.create();
   static final MeshBuffer lineBuffer = MeshBuffer.create();
   static float lineWidth = 1.0F;

   public static void flush() {
      flush(true);
   }

   public static VertexConsumer getLineVertices() {
      return lineBuffer;
   }

   private GuiDrawUtils() {
   }

   static {
      quadBuffer.setFormat(
         Mode.QUADS, DefaultVertexFormat.POSITION_COLOR
      );
      lineBuffer.setFormat(
         Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH
      );
   }

   private static void fillVerticalStrip(
      PoseStack poseStack, float x, float top, float bottom, float thickness, Color color
   ) {
      fillRect(poseStack, x, top + thickness, x + thickness, bottom, color);
   }

   private static void drawTexturedQuad(
      PoseStack poseStack, Identifier texture, int left, int right, int top, int bottom, float minU, float maxU, float minV, float maxV, int color
   ) {
      RenderType renderType = ClientRenderTypes.image(texture, false);
      Matrix4f matrix = poseStack.last().pose();
      VertexConsumer vertices = ClientAccess.zioMinecraft.renderBuffers()
         .bufferSource()
         .getBuffer(renderType);
      vertices.addVertex(matrix, left, top, 0.0F).setUv(minU, minV).setColor(color);
      vertices.addVertex(matrix, left, bottom, 0.0F).setUv(minU, maxV).setColor(color);
      vertices.addVertex(matrix, right, bottom, 0.0F).setUv(maxU, maxV).setColor(color);
      vertices.addVertex(matrix, right, top, 0.0F).setUv(maxU, minV).setColor(color);
   }

   public static void drawItem(
      PoseStack poseStack, ItemStack stack, float x, float y, float z
   ) {
      drawItem(poseStack, stack, x, y, z, true);
   }

   public static void drawTexture(
      PoseStack poseStack, Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color
   ) {
      drawTexturedQuad(
         poseStack, texture, x, x + width, y, y + height, (u + 0.0F) / textureWidth, (u + regionWidth) / textureWidth, (v + 0.0F) / textureHeight, (v + regionHeight) / textureHeight, color
      );
   }

   public static void drawTexture(
      PoseStack poseStack, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, int color
   ) {
      drawTexture(
         poseStack, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color
      );
   }

   public static void drawItem(
      PoseStack poseStack, ItemStack stack, float x, float y, float z, boolean flushItemBuffers
   ) {
      // 26.1.2: ruta vanilla exacta (GuiGraphicsExtractor.item). El item se
      // resuelve con ItemDisplayContext.GUI y se registra en GuiRenderState;
      // GuiItemAtlas lo hornea a una textura de slot y el blit final se pinta
      // en (x, y, x+16, y+16) EXACTO en el endFrame del GuiRenderer. La ruta
      // previa (captura de pose translate(x+8,y+8,150)+scale(16,-16) y submit
      // en el dren) pintaba la tinta corrida ~+4px dentro de la celda y con
      // shade oscuro: la geometria/matematica del bake de items cambio en
      // 26.1.2 y la pose 3D del 1.21.x ya no es la del render final.
      // El historial git conserva drawItemDeferred y la variante inmediata
      // (captura de pose+normal, Lighting setup, flush de buffers).
      if (stack.isEmpty()) {
         return;
      }

      Matrix4f matrix = poseStack.last().pose();
      float originX = matrix.m00() * x + matrix.m10() * y + matrix.m30();
      float originY = matrix.m01() * x + matrix.m11() * y + matrix.m31();
      GuiRenderState guiState = ClientAccess.zioMinecraft.gameRenderer.getGameRenderState().guiRenderState;
      TrackingItemStackRenderState itemState = new TrackingItemStackRenderState();
      ClientAccess.zioMinecraft.getItemModelResolver()
         .updateForTopItem(
            itemState,
            stack,
            ItemDisplayContext.GUI,
            ClientAccess.zioMinecraft.level,
            null,
            0
         );
      guiState.addItem(
         new GuiItemRenderState(
            new Matrix3x2f(),
            itemState,
            Math.round(originX),
            Math.round(originY),
            null
         )
      );
   }

   private void legacyDrawItem(
      PoseStack poseStack, ItemStack stack, float x, float y, float z, boolean flushItemBuffers
   ) {
      if (GuiDeferredDraws.deferEnabled) {
         // 26.1.2: dentro de extractGui solo se construye estado; el pintado
         // real al target ocurre en el dren (drawDeferredClientGraphics). Se
         // captura la matriz base del caller y el lighting+submit+flush se
         // rejuguan en el dren para conservar el orden frente a quads/texto
         // (los items se pintaban en fase extract y quedaban bajo el fondo
         // translucido/blur de los paneles: "items sombreados").
         // Fase extract: solo construccion de estado (model resolver).
         drawItemDeferred(poseStack, stack, x, y, z, flushItemBuffers);
         return;
      }

      if (!stack.isEmpty()) {
         ItemStackRenderState renderState = new ItemStackRenderState();
         SubmitNodeStorage submitNodes = ClientAccess.zioMinecraft.gameRenderer.getSubmitNodeStorage();
         ClientAccess.zioMinecraft.getItemModelResolver()
            .updateForTopItem(
               renderState,
               stack,
               ItemDisplayContext.GUI,
               ClientAccess.zioMinecraft.level,
               null,
               0
            );
         int depthScale = 1;
         if (renderState.usesBlockLight()) {
            ClientAccess.zioMinecraft.gameRenderer
               .getLighting()
               .setupFor(Entry.ITEMS_3D);
            depthScale = 16;
         } else {
            ClientAccess.zioMinecraft.gameRenderer
               .getLighting()
               .setupFor(Entry.ITEMS_FLAT);
         }

         poseStack.pushPose();
         poseStack.translate(x + 8.0F, y + 8.0F, 150.0F + z);

         try {
            poseStack.scale(16.0F, -16.0F, depthScale);
            renderState.submit(
               poseStack,
               submitNodes,
               15728880,
               OverlayTexture.NO_OVERLAY,
               0
            );
            if (flushItemBuffers) {
               RenderUtils.INSTANCE
                  .flushEntities();
            }
         } catch (Throwable failure) {
            throw new ReportedException(CrashReport.forThrowable(failure, "Rendering item"));
         }

         poseStack.popPose();
      }
   }

   private static void drawItemDeferred(
      PoseStack poseStack, ItemStack stack, float x, float y, float z, boolean flushItemBuffers
   ) {
      if (stack.isEmpty()) {
         return;
      }

      ItemStackRenderState renderState = new ItemStackRenderState();
      ClientAccess.zioMinecraft.getItemModelResolver()
         .updateForTopItem(
            renderState,
            stack,
            ItemDisplayContext.GUI,
            ClientAccess.zioMinecraft.level,
            null,
            0
         );
      boolean blockLight = renderState.usesBlockLight();
      Entry entry = blockLight ? Entry.ITEMS_3D : Entry.ITEMS_FLAT;
      int depthScale = blockLight ? 16 : 1;
      // 26.1.2: capturar la matriz FINAL (incluida la NORMAL) aplicando las
      // ops del baseline sobre el caller y restaurando: los items 3D
      // (armadura, totem) sombreados salieron del mulPose puro, que no
      // transforma normales (PoseStack nueva => normales identidad).
      poseStack.pushPose();
      poseStack.translate(x + 8.0F, y + 8.0F, 150.0F + z);
      poseStack.scale(16.0F, -16.0F, depthScale);
      Matrix4f finalPose = new Matrix4f(poseStack.last().pose());
      Matrix3f finalNormal = new Matrix3f(poseStack.last().normal());
      poseStack.popPose();
      GuiDeferredDraws.submitTask(() -> {
         ClientAccess.zioMinecraft.gameRenderer.getLighting().setupFor(entry);
         PoseStack drawPose = new PoseStack();
         drawPose.last().pose().set(finalPose);
         drawPose.last().normal().set(finalNormal);
         try {
            renderState.submit(
               drawPose,
               ClientAccess.zioMinecraft.gameRenderer.getSubmitNodeStorage(),
               15728880,
               OverlayTexture.NO_OVERLAY,
               0
            );
            if (flushItemBuffers) {
               RenderUtils.INSTANCE.flushEntities();
            }
         } catch (Throwable failure) {
            throw new ReportedException(CrashReport.forThrowable(failure, "Rendering item"));
         }
      });
   }

   public static void drawDurabilityBar(
      PoseStack poseStack, ItemStack stack, float x, float y
   ) {
      // 26.1.2: misma matematica de rects que GuiGraphicsExtractor.itemBar
      // ((x+2, y+13) backdrop 13x2 negro + barra de color alta 1px con
      // getBarWidth()), pero via ColoredRectangleRenderState en GuiRenderState
      // para que el sistema de estratos la pinte DESPUES del icono (los fills
      // del dren corren antes del blit del icono del endFrame y quedaban
      // tapados). El poseStack queda por firma; los rects son absolutos.
      if (!stack.isBarVisible()) {
         return;
      }

      GuiRenderState guiState = ClientAccess.zioMinecraft.gameRenderer.getGameRenderState().guiRenderState;
      int barX = Math.round(x) + 2;
      int barY = Math.round(y) + 13;
      guiState.addGuiElement(
         new ColoredRectangleRenderState(
            RenderPipelines.GUI,
            TextureSetup.noTexture(),
            new Matrix3x2f(),
            barX,
            barY,
            barX + 13,
            barY + 2,
            0xFF000000,
            0xFF000000,
            null
         )
      );
      int barColor = ARGB.opaque(stack.getBarColor());
      guiState.addGuiElement(
         new ColoredRectangleRenderState(
            RenderPipelines.GUI,
            TextureSetup.noTexture(),
            new Matrix3x2f(),
            barX,
            barY,
            barX + stack.getBarWidth(),
            barY + 1,
            barColor,
            barColor,
            null
         )
      );
   }

   public static void fillGradientRect(
      Matrix4f matrix, float x, float y, float width, float height, int topLeftColor, int topRightColor, int bottomRightColor, int bottomLeftColor
   ) {
      quadBuffer.addVertex(matrix, x, y + height, 0.0F)
         .setColor(bottomLeftColor);
      quadBuffer.addVertex(matrix, x + width, y + height, 0.0F)
         .setColor(bottomRightColor);
      quadBuffer.addVertex(matrix, x + width, y, 0.0F)
         .setColor(topRightColor);
      quadBuffer.addVertex(matrix, x, y, 0.0F)
         .setColor(topLeftColor);
   }

   public static void fillHorizontalGradient(
      Matrix4f matrix, float x, float y, float width, float height, int... colors
   ) {
      if (colors.length >= 2) {
         float segmentWidth = width / colors.length;

         for (int index = 1; index < colors.length; index++) {
            float segmentEnd = index == colors.length - 1 ? width : segmentWidth * index;
            int rightColor = colors[index];
            int leftColor = colors[index - 1];
            quadBuffer.addVertex(matrix, x + segmentEnd, y, 0.0F)
               .setColor(rightColor);
            quadBuffer.addVertex(
                  matrix, x + segmentWidth * (index - 1), y, 0.0F
               )
               .setColor(leftColor);
            quadBuffer.addVertex(
                  matrix, x + segmentWidth * (index - 1), y + height, 0.0F
               )
               .setColor(leftColor);
            quadBuffer.addVertex(
                  matrix, x + segmentEnd, y + height, 0.0F
               )
               .setColor(rightColor);
         }
      }
   }

   public static void drawText(
      PoseStack poseStack, String text, int x, int y, int color, boolean shadow
   ) {
      if (shadow) {

         FontRenderer initializedFontRenderer = FontRenderer.INSTANCE;
      }
      ClientAccess.zioMinecraft.font
         .drawInBatch(
            text,
            x,
            y,
            color,
            shadow,
            poseStack.last().pose(),
            FontRenderer.INSTANCE
               .getRenderer()
               .getBufferSource(),
            DisplayMode.NORMAL,
            0,
            15728880
         );
   }

   public static void drawLineRect(
      PoseStack poseStack, float left, float top, float right, float bottom, Color color
   ) {
      int packedColor = color.hashCode();
      if (color.getAlpha() != 0) {
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), left, bottom, 0.0F, right, bottom, 0.0F, lineWidth, packedColor);
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), right, bottom, 0.0F, right, top, 0.0F, lineWidth, packedColor);
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), right, top, 0.0F, left, top, 0.0F, lineWidth, packedColor);
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), left, top, 0.0F, left, bottom, 0.0F, lineWidth, packedColor);
      }
   }

   public static void drawRectOutline(
      PoseStack poseStack, float left, float top, float right, float bottom, Color color
   ) {
      fillHorizontalStrip(poseStack, left, right, top, 1.0F, color);
      fillVerticalStrip(poseStack, right, top, bottom, 1.0F, color);
      fillHorizontalStrip(poseStack, left, right, bottom, 1.0F, color);
      fillVerticalStrip(poseStack, left, top, bottom, 1.0F, color);
   }

   public static void drawItemCooldown(
      PoseStack poseStack, ItemStack stack, float x, float y
   ) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      float partialTick = ClientAccess.zioMinecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
      float cooldown = player == null ? 0.0F : player.getCooldowns().getCooldownPercent(stack, partialTick);
      if (cooldown > 0.0F) {
         float top = y + Mth.floor(16.0F * (1.0F - cooldown));
         float bottom = top + Mth.ceil(16.0F * cooldown);
         fillRect(
            poseStack,
            x,
            top,
            x + 16.0F,
            bottom,
            0x7FFFFFFF
         );
      }
   }

   public static void fillRect(
      PoseStack poseStack, float left, float top, float right, float bottom, int color
   ) {
      if (ARGB.alpha(color) != 0) {
         quadBuffer.addVertex(
               poseStack.last().pose(), left, bottom, 0.0F
            )
            .setColor(color);
         quadBuffer.addVertex(
               poseStack.last().pose(), right, bottom, 0.0F
            )
            .setColor(color);
         quadBuffer.addVertex(
               poseStack.last().pose(), right, top, 0.0F
            )
            .setColor(color);
         quadBuffer.addVertex(
               poseStack.last().pose(), left, top, 0.0F
            )
            .setColor(color);
      }
   }

   public static void fillVerticalGradient(
      PoseStack poseStack, float x, float y, float width, float height, int topColor, int bottomColor
   ) {
      fillGradientRect(
         poseStack.last().pose(), x, y, width, height, topColor, topColor, bottomColor, bottomColor
      );
   }

   public static void fillRect(
      PoseStack poseStack, float left, float top, float right, float bottom, Color color
   ) {
      fillRect(poseStack, left, top, right, bottom, color.hashCode());
   }

   private static void fillHorizontalStrip(
      PoseStack poseStack, float left, float right, float y, float thickness, Color color
   ) {
      fillRect(poseStack, left, y, right + thickness, y + thickness, color);
   }

   public static void fillHorizontalGradient(
      PoseStack poseStack, float x, float y, float width, float height, int leftColor, int rightColor
   ) {
      fillGradientRect(
         poseStack.last().pose(), x, y, width, height, leftColor, rightColor, rightColor, leftColor
      );
   }

   public static void flush(boolean flushText) {
      flush(ClientRenderTypes.gui(), flushText);
   }

   public static void flush(RenderType quadRenderType, boolean flushText) {
      MeshDrawContext.setRenderType(
         quadRenderType
      );
      quadBuffer.draw();
      MeshDrawContext.setRenderType(
         ClientRenderTypes.worldLines()
      );
      lineBuffer.draw();
      if (flushText) {
         FontRenderer.INSTANCE
            .flush();
      }
   }

}
