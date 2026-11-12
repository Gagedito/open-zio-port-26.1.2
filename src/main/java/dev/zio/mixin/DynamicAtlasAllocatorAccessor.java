package dev.zio.mixin;

import java.util.Map;
import net.minecraft.client.gui.render.DynamicAtlasAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DynamicAtlasAllocator.class)
public interface DynamicAtlasAllocatorAccessor {
   @Accessor("usedSlotByKey")
   Map<?, ?> getUsedSlots();
}
