/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import net.minecraft.core.BlockPos;

public final class CrystalPlacementEvent extends CancellableEvent {
   private final BlockPos pos;

   public CrystalPlacementEvent(BlockPos pos) {
      this.pos = pos;
   }

   public BlockPos getPos() {
      return this.pos;
   }
}
