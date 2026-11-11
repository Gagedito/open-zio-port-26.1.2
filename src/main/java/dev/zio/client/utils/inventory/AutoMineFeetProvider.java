/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.world.WorldUtils;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.combat.AutoCrystalModule;
import dev.zio.client.modules.combat.AutoMineModule;
import dev.zio.client.modules.combat.AutoMineMiningTracker;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.SpeedMineRebreakChoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class AutoMineFeetProvider extends AutoMineProviderBase {
   private static AutoCrystalModule autoCrystal;
   private static SpeedMineModule speedMine;
   private static final List<Vec3i> crystalOffsets;
   private final Set<BlockPos> protectedPositions = new HashSet<>();

   private boolean canPlaceCrystal(BlockPos position) {
      boolean requireUpperAir = autoCrystal.requireUpperAirSetting.getValue();
      Setting<Boolean> lowerHitBoxSetting = autoCrystal.lowerHitBoxSetting;
      boolean lowerHitBox = lowerHitBoxSetting.getValue();
      boolean checkRotation = !RotationManager.isMovementSyncEnabled();
      return BlockInteractionUtils.canPlaceCrystal(position, requireUpperAir, true, false, false, lowerHitBox, checkRotation);
   }

   private boolean isProtectedSelfPosition(BlockPos position) {
      Setting<Boolean> safeSetting = this.autoMine.safeSetting;
      if (safeSetting.getValue()) {
         Iterator<BlockPos> protectedIterator = this.protectedPositions.iterator();

         while (protectedIterator.hasNext()) {
            if (protectedIterator.next().equals(position)) {
               return true;
            }
         }
      }

      AABB boundsSnapshot = ClientAccess.zioMinecraft.player.getBoundingBox();
      double playerY = ClientAccess.zioMinecraft.player.getY();
      AABB insetBounds = boundsSnapshot.setMaxY(playerY).deflate(0.0, -1.0D, 0.0);

      List<BlockPos> occupiedPositions = BlockPos.betweenClosedStream(insetBounds)
         .map(BlockPos::immutable).filter(occupiedPosition -> {
            ClientLevel level = ClientAccess.zioMinecraft.level;

            return level.getBlockState(occupiedPosition).getBlock().getExplosionResistance() >= 6e+02F;
         }).toList();

      return occupiedPositions.size() <= (2) && occupiedPositions.contains(position);
   }

   private boolean hasAdjacentCrystalBase(BlockPos position) {
      Iterator<Vec3i> offsets = crystalOffsets.iterator();
      while (offsets.hasNext()) {
         Vec3i offset = offsets.next();
         BlockPos supportPosition = position.offset(offset).below();
         if (this.canPlaceCrystal(supportPosition)) {
            return true;
         }
      }
      return false;
   }

   private boolean intersectsBedrock(Player target) {
      AABB targetBounds = target.getBoundingBox();
      double targetY = target.getY();
      AABB footBounds = targetBounds.setMaxY(targetY);
      return BlockPos.betweenClosedStream(footBounds).map(BlockPos::immutable).allMatch(position -> {
         ClientLevel level = ClientAccess.zioMinecraft.level;
         BlockState state = level.getBlockState(position);
         Block bedrock = Blocks.BEDROCK;
         return state.is(bedrock);
      });
   }

   public AutoMineFeetProvider(AutoMineModule module) {
      super(module);
   }

   static {
      autoCrystal = (AutoCrystalModule)ClientContext.getModule(AutoCrystalModule.class);
      speedMine = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
      Vec3i eastOffset = new Vec3i(1, 0, 0);
      Vec3i southOffset = new Vec3i(0, 0, 1);
      Vec3i upOffset = new Vec3i(0, 1, 0);
      Vec3i northOffset = new Vec3i(0, 0, -1);
      Vec3i westOffset = new Vec3i(-1, 0, 0);

      crystalOffsets = List.of(eastOffset, southOffset, upOffset, northOffset, westOffset);
   }

   @Override
   protected boolean canMinePosition(BlockPos position) {
      if (this.isProtectedSelfPosition(position)) {
         return false;
      }

      return WorldUtils.getBlock(position) != Blocks.COBWEB && super.canMinePosition(position);
   }

   private void refreshProtectedPositions() {
      this.protectedPositions.clear();
      boolean movementMode = RotationManager.isMovementSyncEnabled();
      LocalPlayer playerSnapshot = ClientAccess.zioMinecraft.player;

      Iterator<BlockPos> occupiedIterator = PlayerStateUtils.getSurroundPositions(playerSnapshot).iterator();

      while (occupiedIterator.hasNext()) {
         BlockPos occupiedPosition = occupiedIterator.next();

         if (!PlayerStateUtils.isLocalLowProfile() && !movementMode) {
            this.protectedPositions.add(occupiedPosition.above());
         }

         this.protectedPositions.add(occupiedPosition);
      }

      if (!movementMode) {
         double playerHeight = ClientAccess.zioMinecraft.player.getBoundingBox().getYsize();
         int upperOffset = (int)Math.ceil(playerHeight);
         LocalPlayer upperPlayer = ClientAccess.zioMinecraft.player;
         Stream<BlockPos> upperPositions = PlayerStateUtils.getOccupiedBlockPositions(upperPlayer).stream().map((Function<BlockPos, BlockPos>)position -> {
               return position.above(upperOffset);
            });
         Set<BlockPos> protectedConsumer = this.protectedPositions;
         upperPositions.forEach((java.util.function.Consumer<net.minecraft.core.BlockPos>)protectedConsumer::add);
      }
   }

   private Direction[] getPerpendicularDirections(Direction direction) {
      return switch (direction) {
         case NORTH, SOUTH -> {
            Direction[] perpendicularDirections = new Direction[2];
            perpendicularDirections[0] = Direction.EAST;
            perpendicularDirections[1] = Direction.WEST;
            yield perpendicularDirections;
         }
         case EAST, WEST -> {
            Direction[] perpendicularDirections = new Direction[2];
            perpendicularDirections[0] = Direction.NORTH;
            perpendicularDirections[1] = Direction.SOUTH;
            yield perpendicularDirections;
         }
         default -> new Direction[0];
      };
   }

   @Override
   public void collectCandidates(AutoMineCandidates candidates) {
      this.refreshProtectedPositions();
      Player target = this.autoMine.targetSelector.selectTarget();
      Setting<Boolean> fishSetting = this.autoMine.fishSetting;
      Boolean fishEnabled = fishSetting.getValue();
      boolean fishPriority = false;
      if (fishEnabled && candidates.getPriority() ==
         (400)) {
         fishPriority = candidates.getSecondaryPosition() != null;
      }
      if (target == null) {
         return;
      }
      if (this.hasNoCollisionBelow(target)) {
         return;
      }
      if (this.intersectsBedrock(target) && !fishPriority) {
         return;
      }
      net.minecraft.world.phys.Vec3 targetCoordinates = target.position();
      BlockPos targetPosition = BlockPos.containing(targetCoordinates);
      ArrayList<BlockPos> footPositions = new ArrayList<>(PlayerStateUtils.getSurroundPositions(target));
      ClientLevel level = ClientAccess.zioMinecraft.level;
      Block baseBlock = level.getBlockState(targetPosition).getBlock();
      boolean suppressPreviousSelection = baseBlock.getExplosionResistance() >= 6e+02F && baseBlock != Blocks.AIR;
      if (this.autoMine.miningTracker.hasRecentDelayElapsed(500L)) {
         AutoMineMiningTracker currentTrackerSnapshot = this.autoMine.miningTracker;
         if (targetPosition.equals(currentTrackerSnapshot.getCurrentPosition())) {
            suppressPreviousSelection = false;
         }
      } else {
         suppressPreviousSelection = false;
      }
      AutoMineFeetProvider.FeetMiningPair selectedPair = this.selectFeetPair(target, footPositions, suppressPreviousSelection, fishPriority);
      if (selectedPair.primaryPosition == null) {
         return;
      }
      Consumer<AutoMineCandidates> updateSelectedPair = output -> {
         if (output.getSecondaryPosition() == null) {
            BlockPos secondarySnapshot = selectedPair.secondaryPosition;
            output.setSecondaryPosition(secondarySnapshot);
         }
         BlockPos primarySnapshot = selectedPair.primaryPosition;
         output.setPrimaryPosition(primarySnapshot);
      };
      candidates.offer(600, updateSelectedPair);
   }

   private AutoMineFeetProvider.FeetMiningPair selectFeetPair(
      Player target, List<BlockPos> footPositions, boolean suppressPreviousBreak, boolean fishPriority
   ) {
      boolean lowerBedrockCandidates = footPositions.stream().allMatch((Predicate<BlockPos>)position -> {
               return WorldUtils.getBlock(position)
                  == Blocks.BEDROCK;
            })
         && fishPriority;

      BlockPos selfPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      LocalPlayer selfPlayer = ClientAccess.zioMinecraft.player;
      int selfOccupiedCount = PlayerStateUtils.getOccupiedBlockPositions(selfPlayer).size();
      BlockPos primaryPosition = null;
      BlockPos secondaryPosition = null;
      boolean primaryIsCurrentMining = false;
      footPositions.sort(Comparator.comparing((Function<BlockPos, Double>)position -> {
               AABB boundsSnapshot = ClientAccess.zioMinecraft.player.getBoundingBox();
               AABB blockBounds = new AABB(position);

               if (boundsSnapshot.intersects(blockBounds)) {
                  return 9999.0D;
               }

               LocalPlayer occupiedPlayerSnapshot = ClientAccess.zioMinecraft.player;

               if (PlayerStateUtils.getSurroundPositions(occupiedPlayerSnapshot).contains(position)) {
                  return 999.0D;
               }

               boolean hasCrystalBase = false;
               Iterator<Vec3i> offsets = crystalOffsets.iterator();

               while (offsets.hasNext()) {
                  Vec3i offset = offsets.next();

                  if (offset.getY() == 0) {
                     BlockPos adjacentPosition = position.offset(offset);
                     BlockPos supportSnapshot = adjacentPosition.below();
                     if (this.canPlaceCrystal(supportSnapshot)) {
                        hasCrystalBase = true;
                        break;
                     }
                  }
               }

               net.minecraft.world.phys.Vec3 distancePosition = ClientAccess.zioMinecraft.player.position();

               double distanceScore = position.distToCenterSqr(distancePosition);
               if (!hasCrystalBase) {
                  double maximumRangeDouble = (speedMine.rangeSetting.getMaximumValue()).floatValue();

                  distanceScore += Math.pow(maximumRangeDouble, 2.0D);
               }

               return -distanceScore;
            }));

      Iterator<BlockPos> positions = footPositions.iterator();

      while (positions.hasNext()) {
         BlockPos position = positions.next();

         if (!this.isAlreadyMining(position)
            && selfPosition.below().equals(position) && selfOccupiedCount == 1) {
            continue;
         }

         if (lowerBedrockCandidates) {
            position = position.below();
         }

         long recentDelayMillis = RotationManager.isMovementSyncEnabled() ? 500L : 1000L;
         boolean retainPreviousBreak = false;
         if (WorldUtils.isAir(position)) {
            SpeedMineModule previousSnapshot = speedMine;
            retainPreviousBreak = position.equals(previousSnapshot.getMiningPos());
         }
         boolean instantRebreak = speedMine.rebreakSetting.getValue() == SpeedMineRebreakChoice.INSTANT;
         boolean fastRebreak = false;
         if (this.isAlreadyMining(position) && !WorldUtils.isAir(position)) {
            fastRebreak = speedMine.rebreakSetting.getValue() == SpeedMineRebreakChoice.FAST;
         }

         if (suppressPreviousBreak) {
            retainPreviousBreak = false;
         }

         if (speedMine.hasCrystalDelayElapsed(recentDelayMillis) && instantRebreak) {
            Setting<Boolean> asyncSetting = speedMine.asyncSetting;
            Boolean async = asyncSetting.getValue();
            if (!async) {
               retainPreviousBreak = false;
            }
         }

         if (!(this.hasAdjacentCrystalBase(position) || fishPriority || instantRebreak || fastRebreak)) {
            continue;
         }
         if (!(this.canMinePosition(position) || retainPreviousBreak)) {
            continue;
         }
         if (this.isAlreadyMining(position)) {
            primaryPosition = position;
            primaryIsCurrentMining = true;
         } else if (primaryIsCurrentMining) {
            secondaryPosition = position;
         } else {
            secondaryPosition = primaryPosition;
            primaryPosition = position;
         }
      }

      if (primaryPosition != null) {
         Setting<Boolean> cornersSetting = this.autoMine.cornersSetting;
         if (cornersSetting.getValue()) {
            if (this.autoMine.isExtraBreakEnabled()) {
               BlockPos adjacentPosition = this.findAdjacentCandidate(target, primaryPosition);
               if (adjacentPosition != null) {
                  secondaryPosition = adjacentPosition;
               }
            }
         }
      }

      return new AutoMineFeetProvider.FeetMiningPair(primaryPosition, secondaryPosition);
   }

   private BlockPos findAdjacentCandidate(Player target, BlockPos referencePosition) {
      net.minecraft.world.phys.Vec3 targetCoordinates = target.position();
      BlockPos targetPosition = BlockPos.containing(targetCoordinates);
      Direction matchedDirection = null;
      Iterator<Direction> directions = Plane.HORIZONTAL.iterator();
      while (directions.hasNext()) {
         Direction direction = directions.next();
         if (targetPosition.relative(direction).equals(referencePosition)) {
            matchedDirection = direction;
            break;
         }
      }
      if (matchedDirection == null) {
         return null;
      }

      for (Direction perpendicularDirection : this.getPerpendicularDirections(matchedDirection)) {
         BlockPos adjacentPosition = referencePosition.relative(perpendicularDirection);
         if (WorldUtils.isAir(adjacentPosition)) {
            BlockPos supportPosition = adjacentPosition.below();
            if (WorldUtils.isBedrockOrObsidian(supportPosition)) {
               return null;
            }
         }
         if (this.canMinePosition(adjacentPosition)) {
            BlockPos lowerPosition = adjacentPosition.below();
            if (WorldUtils.isBedrockOrObsidian(lowerPosition)) {
               return adjacentPosition;
            }
         }
      }
      return null;
   }

   @Override
   public boolean isEnabled() {
      return this.autoMine.feetSetting.getValue();
   }

   public static void bindModules(ModuleRegistry registry) {
      autoCrystal = (AutoCrystalModule)registry.getModule(
         AutoCrystalModule.class
      );
      speedMine = (SpeedMineModule)registry.getModule(
         SpeedMineModule.class
      );
   }

   private record FeetMiningPair(
      BlockPos primaryPosition,
      BlockPos secondaryPosition
   ) {
   }
}
