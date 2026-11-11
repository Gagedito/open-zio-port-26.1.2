/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

public final class SlotCapacity {
   private final int slotIndex;
   private final int capacity;
   private int count;

   public SlotCapacity(int slotIndex, int capacity, int count) {
      this.slotIndex = slotIndex;
      this.count = count;
      this.capacity = capacity;
   }

   public int getSlotIndex() {
      return this.slotIndex;
   }

   public int getCapacity() {
      return this.capacity;
   }

   public int getCount() {
      return this.count;
   }

   public void setCount(int count) {
      int capacity = this.getCapacity();
      this.count = Math.min(count, capacity);
   }

}
