/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.world.WorldUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class AntiPhaseFullBlockPlanner implements AntiPhasePlacementPlanner {

   @Override
   public AntiPhasePlacement findPlacement(
      Player target
   ) {

      BlockPos targetPosition = PlayerStateUtils.getRoundedBlockPosition((net.minecraft.world.entity.LivingEntity)(target));
      boolean strictDirections = BlockInteractionUtils.isStrictDirectionEnabled();
      Direction supportDirection = BlockInteractionUtils.findSupportDirection(targetPosition, strictDirections);
      if (supportDirection != null && WorldUtils.isAir(targetPosition)) {
         return new AntiPhasePlacement(
            targetPosition.relative(supportDirection), supportDirection.getOpposite(),
            this.getPlacementBounds(targetPosition, supportDirection)
         );
      }

      return null;
   }

   protected AABB getPlacementBounds(BlockPos position, Direction supportDirection) {
      return new AABB(position);
   }

}
