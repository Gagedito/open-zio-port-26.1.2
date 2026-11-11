/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.world;

import net.minecraft.core.BlockPos;

public interface BlockBreakingState {
   BlockPos getCurrentBreakingBlock();

   void setCurrentBreakingBlock(BlockPos position);

   float getBreakingProgress();

   void setBreakingProgress(float progress);

   boolean isBreakingBlock();

   void setBreakingBlock(boolean breaking);
}
