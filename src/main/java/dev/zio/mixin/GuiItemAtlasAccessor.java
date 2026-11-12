package dev.zio.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.render.DynamicAtlasAllocator;
import net.minecraft.client.gui.render.GuiItemAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiItemAtlas.class)
public interface GuiItemAtlasAccessor {
   @Accessor("allocator")
   DynamicAtlasAllocator getAllocator();

   @Accessor("textureView")
   GpuTextureView getTextureView();

   @Accessor("slotTextureSize")
   int getSlotTextureSize();

   @Accessor("textureSize")
   int getTextureSize();
}
