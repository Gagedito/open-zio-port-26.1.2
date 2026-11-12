package dev.zio.client.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Puente entre el GuiItemAtlas vanilla y el sistema de RenderType de zio.
 */
public final class GuiItemAtlasTexture extends AbstractTexture {
   public static final GuiItemAtlasTexture INSTANCE = new GuiItemAtlasTexture();
   public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
      "zio",
      "textures/item_atlas.png"
   );

   private GuiItemAtlasTexture() {
   }

   public void updateFrom(GpuTextureView textureView) {
      this.textureView = textureView;
   }

   @Override
   public void close() {
   }
}
