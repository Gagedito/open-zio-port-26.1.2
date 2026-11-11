/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.world;

import dev.zio.client.ClientAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record BlockStateSnapshot(
   BlockState state,
   BlockPos position,
   long capturedAtMillis
) implements ClientAccess {

   public boolean hasExpired() {
      return System.currentTimeMillis() - this.capturedAtMillis >= 500L;
   }

   public static BlockState captureState(BlockPos position) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      return level.getBlockState(position);
   }
}
