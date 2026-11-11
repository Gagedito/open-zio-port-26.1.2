/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
   @Accessor("hoveredSlot")
   Slot zio$getFocusedSlot();

   @Accessor("leftPos")
   int getX();

   @Accessor("topPos")
   int getY();

   @Accessor("imageWidth")
   int getBgWidth();

   @Accessor("imageHeight")
   int getBgHeight();
}
