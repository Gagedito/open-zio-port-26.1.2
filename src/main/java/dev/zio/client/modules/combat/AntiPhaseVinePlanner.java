/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.utils.combat.BoundingBoxUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class AntiPhaseVinePlanner extends AntiPhaseLadderPlanner {

   @Override
   protected boolean shouldRejectPlacement(
      BlockPos position, Player target, AntiPhasePlacementShape shape
   ) {
      AABB targetBoundsSnapshot = BoundingBoxUtils.getInterpolationBounds(target);
      AABB shapeBounds = shape.bounds();
      AABB worldBounds = shapeBounds.move(position);
      return targetBoundsSnapshot.intersects(worldBounds);
   }

}
