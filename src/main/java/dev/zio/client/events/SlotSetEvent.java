/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import net.minecraft.world.item.ItemStack;

public final class SlotSetEvent extends CancellableEvent {
   private final ItemStack itemStack;

   public SlotSetEvent(ItemStack itemStack) {
      this.itemStack = itemStack;
   }

   public ItemStack getItemStack() {
      return this.itemStack;
   }
}
