/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import dev.zio.client.events.WorldRenderEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;

public class SpeedMineRenderQueue {
   private final Map<BlockPos, SpeedMineBlockRenderState> blockRenderStates = new HashMap<>();

   public void remove(BlockPos blockPos) {
      this.blockRenderStates.remove(blockPos);
   }

   public void setProgress(BlockPos blockPos, float progress) {
      SpeedMineBlockRenderState renderState = this.blockRenderStates.computeIfAbsent(
         blockPos, pos -> new SpeedMineBlockRenderState(pos, progress)
      );

      renderState.setProgress(progress);
   }

   public void setProgressTwice(BlockPos blockPos, float progress) {
      SpeedMineBlockRenderState renderState = this.blockRenderStates.computeIfAbsent(
         blockPos, pos -> new SpeedMineBlockRenderState(pos, progress)
      );

      renderState.setProgress(progress);

      renderState.setProgress(progress);
   }

   public void clear() {
      this.blockRenderStates.clear();
   }

   public void render(WorldRenderEvent event) {
      BlockPos fadedBlockPos = null;
      Iterator<SpeedMineBlockRenderState> states = this.blockRenderStates.values().iterator();

      while (states.hasNext()) {
         SpeedMineBlockRenderState renderState = states.next();

         renderState.render(event);

         if (renderState.isFadedOut()) {

            fadedBlockPos = renderState.getBlockPos();
         }
      }

      if (fadedBlockPos != null) {
         this.blockRenderStates.remove(fadedBlockPos);
      }
   }

   public void snapshotProgress() {
      this.blockRenderStates.values().forEach(SpeedMineBlockRenderState::snapshotProgress);
   }
}
