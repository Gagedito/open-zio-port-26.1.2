/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.managers.WorldStateManager;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class PusherTargetSelector implements ClientAccess {
   private static final List<Block> excludedHeadBlocks;
   private final PusherModule module;

   private boolean isValidTarget(Player target) {
      BlockPos position = target.blockPosition();
      ClientLevel headLevelSnapshot = ClientAccess.zioMinecraft.level;
      BlockPos headPosition = position.above();

      BlockState headState = headLevelSnapshot.getBlockState(headPosition);

      if (!excludedHeadBlocks.contains(headState.getBlock())) {
         float headResistance = headState.getDestroySpeed(ClientAccess.zioMinecraft.level, position.above());

         if (headResistance != -1.0F) {
            WorldStateManager blockTrackerSnapshot = ClientContext.worldStateManager;

            BlockPos clearanceSnapshot = position.above(2);

            if (!blockTrackerSnapshot.isPlacementOccupied(clearanceSnapshot)) {
               if (this.module.onlyHoleSetting.getValue()) {
                  if (!ClientContext.holeManager.intersectsHole(position)) {
                     return false;
                  }
               }

               if (this.module.onlySafeSetting.getValue()) {
                  if (!ClientContext.holeManager.intersectsHole(position)) {
                     headLevelSnapshot = ClientAccess.zioMinecraft.level;

                     BlockState safeState = headLevelSnapshot.getBlockState(target.blockPosition());
                     Block safeBlock = safeState.getBlock();
                     float safeResistance = safeBlock.getExplosionResistance();

                     if (safeResistance < 600.0F) {
                        return false;
                     }
                  }
               }

               int occupiedCount = PlayerStateUtils.getSurroundPositions(target).size();

               return occupiedCount <= 6;
            }
         }
      }

      return false;
   }

   PusherTargetSelector(PusherModule module) {
      this.module = module;
   }

   static {
      Block respawnAnchor = Blocks.RESPAWN_ANCHOR;
      Block reinforcedDeepslate = Blocks.REINFORCED_DEEPSLATE;
      Block cryingObsidian = Blocks.CRYING_OBSIDIAN;
      Block obsidian = Blocks.OBSIDIAN;
      excludedHeadBlocks = List.of(obsidian, cryingObsidian, reinforcedDeepslate, respawnAnchor);
   }

   public Player selectTarget() {
      Optional<AbstractClientPlayer> selectedSnapshot = ClientAccess.zioMinecraft.level.players().stream().filter((Predicate<AbstractClientPlayer>)candidate -> {
            if (candidate.isAlive() && candidate != ClientAccess.zioMinecraft.player) {
               AABB bounds = candidate.getBoundingBox();
               double height = bounds.getYsize();

               if (height > 1.0D) {
                  if (EntityUtils.hasCollisionBelow(candidate)) {
                     float distance = ClientAccess.zioMinecraft.player.distanceTo(candidate);
                     PusherModule moduleSnapshot = this.module;

                     if (distance <= moduleSnapshot.getRange()) {
                        if (!ClientContext.socialRegistry.isFriendPlayer(candidate)) {
                           return true;
                        }
                     }
                  }
               }
            }

            return false;
         }).filter((Predicate<AbstractClientPlayer>)candidate -> {
            if (this.module.ignoreNakedSetting.getValue()) {
               if (!PlayerStateUtils.hasArmor(candidate)) {
                  return false;
               }
            }

            return true;
         }).filter(this::isValidTarget).min(Comparator.comparing((Function<AbstractClientPlayer, Float>)candidate -> {
            float yawSnapshot = ClientAccess.zioMinecraft.player.getYRot();

            float candidateYaw = RotationManager.getRotationsToEntity(candidate)[0];

            float yawDifference = Mth.degreesDifferenceAbs(yawSnapshot, candidateYaw);
            return yawDifference;
         }));
      return selectedSnapshot.orElse(null);
   }
}
