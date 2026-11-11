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

public class MultiPlayerGameModeDestroyBlockEvent extends CancellableEvent {
   public BlockPos pos;

   public MultiPlayerGameModeDestroyBlockEvent(BlockPos pos) {
      this.pos = pos;
   }

   public BlockPos getPos() {
      return this.pos;
   }
}
