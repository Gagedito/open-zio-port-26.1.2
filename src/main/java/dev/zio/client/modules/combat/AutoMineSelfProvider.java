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
import dev.zio.client.utils.inventory.AutoMineProviderBase;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.inventory.AutoMineCandidates;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.settings.Setting;

import java.util.Iterator;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AutoMineSelfProvider extends AutoMineProviderBase {
   private final ElapsedTimer recentTimer = new ElapsedTimer();
   private BlockPos recentPosition;
   private int recentPriority;

   private boolean isClosedUpperTrapdoor(BlockPos position) {
      BlockPos testedPosition = position;

      BlockState state = ClientAccess.zioMinecraft.level.getBlockState(testedPosition);

      if (!(state.getBlock() instanceof TrapDoorBlock)) {
         return false;
      }

      BooleanProperty openProperty = TrapDoorBlock.OPEN;

      Boolean open = state.getValue(openProperty);
      if (open) {
         return false;
      }

      EnumProperty halfProperty = TrapDoorBlock.HALF;

      return state.getValue(halfProperty) != Half.BOTTOM;
   }

   public AutoMineSelfProvider(AutoMineModule module) {
      super(module);
   }

   private BlockPos selectFaceCandidate() {
      Setting<Boolean> faceSetting = this.autoMine.faceSetting;

      if (!faceSetting.getValue()) {
         return null;
      }

      if (PlayerStateUtils.isLocalLowProfile()) {
         return null;
      }

      BlockPos eyePosition = BlockPos.containing((Position)ClientAccess.zioMinecraft.player.getEyePosition());

      BlockState eyeState = ClientAccess.zioMinecraft.level.getBlockState(eyePosition);

      if (!eyeState.isAir()) {
         Block eyeBlock = eyeState.getBlock();
         float resistance = eyeBlock.getExplosionResistance();

         if (!(resistance < 600.0F)) {
            if (this.canMinePosition(eyePosition)) {
               BlockPos feetPosition = ClientAccess.zioMinecraft.player.blockPosition();

               BlockState feetState = ClientAccess.zioMinecraft.level.getBlockState(feetPosition);
               if (!feetState.isAir()) {
                  if (!this.canMinePosition(feetPosition)) {
                     return null;
                  }
               }

               return eyePosition;
            }
         }
      }

      return null;
   }

   @Override
   public void collectCandidates(AutoMineCandidates candidates) {
      BlockPos facePosition = this.selectFaceCandidate();
      AutoMineTargetSelector targetSelector = this.autoMine.targetSelector;

      if (targetSelector.selectTarget() != null || facePosition != null) {
         if (!ClientAccess.zioMinecraft.player.isFallFlying()) {
            this.offerRecentCandidate(candidates);

            AABB playerBounds = ClientAccess.zioMinecraft.player.getBoundingBox();
            Setting<Boolean> onlyCrawlSetting = this.autoMine.onlyCrawlSetting;

            if (onlyCrawlSetting.getValue()) {
               if (!PlayerStateUtils.isLocalLowProfile() && facePosition == null) {
                  return;
               }
            }

            AABB boundsSnapshot = playerBounds;
            BlockPos candidatePosition = this.selectSelfCandidate(boundsSnapshot);
            int basePriority;
            if (!PlayerStateUtils.isLocalLowProfile() && facePosition == null) {
               basePriority = 100;
            } else {
               basePriority = 1500;
            }

            int priority = basePriority;
            Setting<Boolean> tryCrawlSetting = this.autoMine.tryCrawlSetting;

            if (tryCrawlSetting.getValue()) {
               if (!PlayerStateUtils.isLocalLowProfile() && candidatePosition != null) {
                  long candidateY = candidatePosition.getY();

                  double playerY = ClientAccess.zioMinecraft.player.getY();
                  if (candidateY == Math.round(playerY)) {
                     priority = 1000;
                  }
               }
            }

            if (candidatePosition == null) {
               return;
            }

            Consumer<AutoMineCandidates> candidateUpdate = output -> {
               BlockPos primaryPosition = candidatePosition;
               AutoMineCandidates primaryOutput = output;
               primaryOutput.setPrimaryPosition(primaryPosition);
            };
            candidates.offer(priority, candidateUpdate);
            this.recentPosition = candidatePosition;
            this.recentPriority = priority;

            this.recentTimer.reset();
            return;
         }
      }
   }

   private boolean hasGroundedResistantSupport(BlockPos supportPosition) {
      BlockPos testedPosition = supportPosition;

      BlockState state = ClientAccess.zioMinecraft.level.getBlockState(testedPosition);

      Block block = state.getBlock();
      float resistance = block.getExplosionResistance();

      if (resistance < 600.0F) {
         return false;
      }

      return ClientAccess.zioMinecraft.player.onGround();
   }

   private void offerRecentCandidate(AutoMineCandidates candidates) {
      Setting<Boolean> instantSetting = this.autoMine.instantSetting;

      if (instantSetting.getValue()) {
         if (!this.recentTimer.hasElapsed(150L)
            && this.recentPosition != null) {
            int retainedPriority = this.recentPriority;
            Consumer<AutoMineCandidates> recentUpdate = output -> {
               BlockPos primaryPosition = this.recentPosition;
               AutoMineCandidates primaryOutput = output;
               primaryOutput.setPrimaryPosition(primaryPosition);
            };
            candidates.offer(retainedPriority, recentUpdate);
            return;
         }
      }
   }

   @Override
   public boolean isEnabled() {
      Setting<Boolean> enabledSetting = this.autoMine.selfSetting;

      return enabledSetting.getValue();
   }

   private BlockPos selectSelfCandidate(AABB playerBounds) {
      BlockPos basePosition = PlayerStateUtils.getLocalRoundedBlockPosition();

      BlockPos belowPosition = basePosition.below();

      AABB boundsSnapshot = playerBounds;
      BlockPos overheadPosition = this.getOverheadPosition(boundsSnapshot);

      BlockPos facePosition = this.selectFaceCandidate();
      Setting<Boolean> tryCrawlSetting = this.autoMine.tryCrawlSetting;

      if (tryCrawlSetting.getValue()) {
         BlockPos baseCandidate = basePosition;

         if (this.canMinePosition(baseCandidate)) {
            if (!PlayerStateUtils.isLocalLowProfile()) {
               BlockPos recentBase = basePosition;
               if (this.isAlreadyMining(recentBase)) {
                  return basePosition;
               }

               BlockPos supportPosition = overheadPosition.below();
               if (this.hasGroundedResistantSupport(supportPosition)) {
                  return basePosition;
               }
            }
         }
      }

      BlockPos belowCandidate = belowPosition;

      belowSelection:
      if (this.canMinePosition(belowCandidate)) {
         BlockPos recentBelow = belowPosition;
         if (!this.isAlreadyMining(recentBelow)) {
            if (!PlayerStateUtils.isLocalLowProfile()) {
               break belowSelection;
            }
         }

         if (this.shouldMineDown()) {
            return belowPosition;
         }
      }

      BlockPos belowOverhead = overheadPosition.below();

      if (this.isClosedUpperTrapdoor(belowOverhead)) {
         if (PlayerStateUtils.isLocalLowProfile()) {
            overheadPosition = overheadPosition.below();
         }
      }

      Setting<Boolean> finalCrawlSetting = this.autoMine.tryCrawlSetting;

      if (finalCrawlSetting.getValue()) {
         if (PlayerStateUtils.isLocalLowProfile()) {
            return null;
         }
      }

      if (facePosition != null) {
         overheadPosition = facePosition;
      }

      return this.canMinePosition(overheadPosition) ? overheadPosition : null;
   }

   private BlockPos getOverheadPosition(AABB playerBounds) {
      Vec3 positionSnapshot = ClientAccess.zioMinecraft.player.position();

      double heightOffset = playerBounds.getYsize()
         - BoundingBoxUtils.blockCornerInset;

      Vec3 overheadSnapshot = positionSnapshot.add(0.0, heightOffset, 0.0);
      BlockPos headPosition = BlockPos.containing((Position)overheadSnapshot);
      return headPosition.above();
   }

   private boolean shouldMineDown() {
      float resistance;
      Setting<Boolean> downSetting = this.autoMine.downSetting;

      if (!downSetting.getValue()) {
         return false;
      }

      Setting<Boolean> downPrioritySetting = this.autoMine.downPrioritySetting;

      if (downPrioritySetting.getValue()) {
         return true;
      }

      ClientLevel levelSnapshot = ClientAccess.zioMinecraft.level;

      BlockPos positionSnapshot = PlayerStateUtils.getLocalRoundedBlockPosition();
      BlockPos basePosition = positionSnapshot;
      ClientLevel probeLevel = levelSnapshot;
      BlockPos probePosition = basePosition.below(2);

      if (((LevelReader)probeLevel).isEmptyBlock(probePosition)) {
         return false;
      }

      Iterator<BlockPos> occupiedIterator = PlayerStateUtils.getSurroundPositions(ClientAccess.zioMinecraft.player).iterator();

      do {
         if (!occupiedIterator.hasNext()) {
            return true;
         }

         BlockPos occupiedPosition = occupiedIterator.next();
         levelSnapshot = ClientAccess.zioMinecraft.level;

         BlockPos belowPosition = occupiedPosition.below();

         BlockState state = levelSnapshot.getBlockState(belowPosition);

         if (state.isAir()) {
            break;
         }

         Block block = state.getBlock();
         resistance = block.getExplosionResistance();
      } while (!(resistance < 600.0F));

      return false;
   }
}
