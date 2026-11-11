/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.integrations.baritone;

import dev.zio.client.NamedFeature;

import net.minecraft.core.BlockPos;

public interface BaritoneBridge {
   void pathElytraTo(BlockPos position);

   void setGoal(BlockPos position);

   boolean isPathingOrCalculating();

   boolean isInSelection(BlockPos position);

   boolean isPauseRequested();

   BlockPos getGoalPosition();

   void pathTo(BlockPos position);

   void cancelPathing();

   boolean isAvailable();

   void requestPause(NamedFeature feature);

   void releasePause(NamedFeature feature);

   void pathForward();
}
