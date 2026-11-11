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
import net.minecraft.core.Direction;

public final class MultiPlayerGameModeStartDestroyBlockEvent extends CancellableEvent {
   private final BlockPos pos;
   private final Direction direction;

   public MultiPlayerGameModeStartDestroyBlockEvent(BlockPos pos, Direction direction) {
      this.pos = pos;
      this.direction = direction;
   }

   public BlockPos getPos() {
      return this.pos;
   }

   public Direction getDirection() {
      return this.direction;
   }
}
