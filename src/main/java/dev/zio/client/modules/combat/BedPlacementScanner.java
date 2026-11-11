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
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.combat.DamageUtils;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.settings.options.BlockSampleMode;

import java.util.Iterator;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BedPlacementScanner implements ClientAccess {
   private static final MutableBlockPos scanPosition;
   private final BedAuraModule module;
   private Direction bedDirection;
   private Vec3 hitPoint;
   private float range;

   public BedPlacementScanner(BedAuraModule module) {
      this.module = module;
   }

   static {
      scanPosition = new MutableBlockPos();
   }

   public BedPlacementCandidate scanPlacement(LivingEntity target) {
      Float rangeValue = this.module.placeRangeSetting.getValue();
      this.range = rangeValue;

      Vec3 targetPosition = target.position();

      BedPlacementCandidate bestPlacement = null;
      float offsetX = -2.0F;

      while (offsetX < 2.0F) {
         float offsetY = -2.0F;

         while (true) {
            if (!(offsetY < 3.0F)) {
               offsetX += 1.0F;
               break;
            }

            float offsetZ = -2.0F;

            while (offsetZ < 2.0F) {
               this.hitPoint = null;
               MutableBlockPos mutablePosition = scanPosition;
               double scanXSnapshot = targetPosition.x + offsetX;
               double scanYSnapshot = targetPosition.y + offsetY;
               double scanZ = targetPosition.z + offsetZ;
               double scanY = scanYSnapshot;

               mutablePosition.set(scanXSnapshot, scanY, scanZ);
               MutableBlockPos candidatePosition = scanPosition;
               LivingEntity candidateTarget = target;

               BedPlacementCandidate candidate = this.evaluatePlacement(candidateTarget, candidatePosition);
               if (candidate != null
                  && (bestPlacement == null || candidate.targetDamage() >= bestPlacement.targetDamage())) {
                  bestPlacement = candidate;
               }

               offsetZ += 1.0F;
            }

            offsetY += 1.0F;
         }
      }

      return bestPlacement;
   }

   private Direction selectSupportDirection(
      Direction facing, BlockPos position, boolean requireSupport
   ) {
      boolean airPlace = this.module.airPlaceSetting.getValue();
      for (Direction direction : Direction.values()) {
         BlockPos adjacentPosition = position.relative(direction);
         float adjacentY = adjacentPosition.getY();

         if (!(adjacentY >= WorldUtils.getTopY())) {
            if (WorldUtils.isPlacementOccupied(adjacentPosition) || airPlace) {
               if (requireSupport) {
                  List supportDirections = BlockInteractionUtils.getStrictDirections(adjacentPosition);
                  if (!supportDirections.contains(direction.getOpposite())) {
                     continue;
                  }
               }

               if (!this.module.rotateSetting.getValue()) {
                  return direction;
               }

               this.hitPoint = this.selectHitPoint(direction, facing, position);
               if (this.hitPoint != null) {
                  return direction;
               }
            }
         }
      }
      return null;
   }

   private Vec3 selectHitPoint(
      Direction supportDirection, Direction facing, BlockPos position
   ) {
      Direction sampleDirection = supportDirection;
      BlockPos samplePosition = position;

      Iterator samples = this.sampleHitPoints(samplePosition, sampleDirection).iterator();

      while (samples.hasNext()) {
         Vec3 sample = (Vec3)samples.next();

         if (!(sample.distanceTo(ClientAccess.zioMinecraft.player.getEyePosition()) > this.range)) {
            double yaw = RotationManager.getRotationsToPosition(sample)[0];
            Direction sampleFacing = Direction.fromYRot(yaw);
            if (facing == sampleFacing) {
               return sample;
            }
         }
      }

      return null;
   }

   private BlockHitResult createHitResult(Direction direction, BlockPos position) {
      hitPolicy: {
         if (this.module.airPlaceSetting.getValue()) {
            BlockPos adjacentPosition = position.relative(direction);
            if (!WorldUtils.isPlacementOccupied(adjacentPosition)) {
               break hitPolicy;
            }
         }

         if (this.module.liquidPlaceSetting.getValue()) {
            if (WorldUtils.hasFluid(position)) {
               break hitPolicy;
            }
         }

         return new BlockHitResult(this.hitPoint, direction.getOpposite(), position.relative(direction), false);
      }

      BlockPos pointPosition = position;
      Direction pointFacing = this.bedDirection;
      BedPlacementScanner pointSelector = this;
      this.hitPoint = pointSelector.selectHitPoint(
         null, pointFacing, pointPosition
      );
      return new BlockHitResult(this.hitPoint, direction, position, false);
   }

   private BedPlacementCandidate evaluatePlacement(LivingEntity target, BlockPos headPosition) {
      BlockPos statePosition = headPosition;

      BlockState state = ClientAccess.zioMinecraft.level.getBlockState(statePosition);
      if (!state.canBeReplaced()) {
         BedPart headPart = BedPart.HEAD;
         BlockPos headCheckPosition = headPosition;

         if (!this.module.isBedPart(headCheckPosition, headPart)) {
            return null;
         }
      }

      BlockPos footPosition = null;
      Direction supportDirection = null;
      Plane horizontalPlane = Plane.HORIZONTAL;
      Iterator directions = horizontalPlane.iterator();

      while (directions.hasNext()) {
         Direction direction = (Direction)directions.next();

         BlockPos candidateFoot = headPosition.relative(direction);
         boolean secondaryPlacementFlag = false;

         if (!BlockInteractionUtils.canPlaceAt(candidateFoot, true, secondaryPlacementFlag)) {
            BedPart footPart = BedPart.FOOT;
            BlockPos footPartPosition = candidateFoot;

            if (!this.module.isBedPart(footPartPosition, footPart)) {
               continue;
            }
         }

         Direction supportFacingSnapshot = direction.getOpposite();

         boolean requireSupport = BlockInteractionUtils.isStrictDirectionEnabled();
         BlockPos supportPosition = candidateFoot;
         Direction supportFacing = supportFacingSnapshot;

         supportDirection = this.selectSupportDirection(supportFacing, supportPosition, requireSupport);
         if (supportDirection != null) {
            Direction facingSource = direction;
            this.bedDirection = facingSource.getOpposite();
            footPosition = candidateFoot;
            break;
         }
      }

      if (footPosition == null) {
         return null;
      }

      Vec3 eyeSnapshot = ClientAccess.zioMinecraft.player.getEyePosition();
      AABB footBounds = new AABB(footPosition);

      if (eyeSnapshot.distanceTo(BoundingBoxUtils.clampToBounds(eyeSnapshot, footBounds)) > this.range) {
         return null;
      }

      Vec3 explosionCenterSnapshot = headPosition.getCenter();

      AABB damageBounds = this.module.getDamageBounds(target);
      LivingEntity damageTarget = target;

      float targetDamage = DamageUtils.getBedDamage(explosionCenterSnapshot, damageTarget, damageBounds);

      Float minimumDamage = this.module.minDamageSetting.getValue();
      if (targetDamage < minimumDamage) {
         return null;
      }

      explosionCenterSnapshot = headPosition.getCenter();
      LocalPlayer selfPlayer = ClientAccess.zioMinecraft.player;

      float selfDamage = DamageUtils.getBedDamage(explosionCenterSnapshot, selfPlayer);

      Float maximumDamage = this.module.maxSelfDamageSetting.getValue();
      if (selfDamage > maximumDamage) {
         return null;
      }

      if (this.hitPoint == null) {
         if (!this.module.rotateSetting.getValue()) {
            Vec3 fallbackCenterSnapshot = footPosition.getCenter();

            Direction fallbackDirection = supportDirection;
            Vec3 fallbackCenter = fallbackCenterSnapshot;
            this.hitPoint = fallbackCenter.relative(fallbackDirection, 0.5D);
         }
      }

      return new BedPlacementCandidate(
         footPosition, headPosition.immutable(), targetDamage, selfDamage, this.createHitResult(supportDirection, footPosition)
      );
   }

   private List sampleHitPoints(BlockPos position, Direction direction) {
      if (direction == null) {
         BlockPos centerPosition = position;
         BlockSampleMode samplingMode = BlockSampleMode.X8;

         return samplingMode.sampleBlock(centerPosition);
      } else {
         BlockSampleMode samplingModeSnapshot = BlockSampleMode.X8;

         BlockPos facePositionSnapshot = position.relative(direction);

         Direction faceDirection = direction.getOpposite();

         return samplingModeSnapshot.sampleFace(facePositionSnapshot, faceDirection);
      }
   }
}
