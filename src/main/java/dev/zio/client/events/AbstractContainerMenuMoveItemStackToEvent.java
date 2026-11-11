/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import net.minecraft.world.inventory.Slot;

public final class AbstractContainerMenuMoveItemStackToEvent extends CancellableEvent {
   private final Slot slot;

   public AbstractContainerMenuMoveItemStackToEvent(Slot slot) {
      this.slot = slot;
   }

   public Slot getSlot() {
      return this.slot;
   }
}
