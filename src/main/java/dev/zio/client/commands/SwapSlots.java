/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

public record SwapSlots(
   int sourceSlot,
   int targetSlot
) {

   public boolean isInvalid() {
      return this.sourceSlot == -1
         || this.targetSlot == -1;
   }

   public static final SwapSlots INVALID = new SwapSlots(-1, -1);

}
