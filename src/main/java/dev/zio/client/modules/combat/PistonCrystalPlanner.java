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
import dev.zio.client.settings.options.BlockSampleMode;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.world.WorldUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class PistonCrystalPlanner {
   private final PistonCrystalModule module;

   PistonCrystalPlanner(PistonCrystalModule module) {
      this.module = module;
   }

   PistonCrystalPlan findPlan(Player target) {
      for (int height = 0; height < 3; height++) {
         PistonCrystalPlan plan = this.findPlan(target, height);
         if (plan != null) {
            return plan;
         }
      }
      return null;
   }

   private PistonCrystalPlan findPlan(Player target, int height) {
      PistonCrystalPlan selected = null;
      double score = Double.MAX_VALUE;
      for (Candidate candidate : this.candidates(target)) {
         BlockPos position = candidate.position().offset(0, height, 0);
         Direction outward = candidate.outward();
         double candidateScore = -ClientAccess.zioMinecraft.player.getEyePosition().distanceTo(position.getCenter());
         if (candidateScore < score) {
            if (!this.hasVerticalClearance(position, outward.getOpposite(), height)) {
               return null;
            }
            PistonCrystalPlan plan = this.buildPlan(position, outward);
            if (plan != null) {
               selected = plan;
               score = candidateScore;
            }
         }
      }
      return selected;
   }

   private PistonCrystalPlan buildPlan(BlockPos crystalPosition, Direction outward) {
      if (!WorldUtils.isBedrockOrObsidian(crystalPosition.below()) || !WorldUtils.isReplaceable(crystalPosition)
         || this.module.hasBlockingEntities(this.module.crystalBounds(crystalPosition), this::blocksCrystal)) {
         return null;
      }
      BlockPos base = crystalPosition.below();
      List<Direction> directions = BlockInteractionUtils.getStrictDirections(base);
      Direction face = directions.isEmpty() ? Direction.UP : directions.getFirst();
      Vec3 crystalHit = base.getCenter().relative(face, 0.5D);
      if (ClientAccess.zioMinecraft.player.getEyePosition().distanceTo(crystalHit) > this.module.placeRangeSetting.getValue()) {
         return null;
      }
      Vec3 attackPosition = crystalPosition.getCenter().relative(outward.getOpposite(), 0.02D);
      if (attackPosition.distanceTo(ClientAccess.zioMinecraft.player.getEyePosition()) > this.module.attackRangeSetting.getValue()) {
         return null;
      }
      PistonPlacement placement = this.findPiston(crystalPosition, outward);
      if (placement == null) {
         return null;
      }
      return new PistonCrystalPlan(crystalPosition, new BlockHitResult(crystalHit, face, base, false), placement.position(),
         outward.getOpposite(), placement.hit(), placement.powerPosition(), placement.powerHit());
   }

   private boolean hasVerticalClearance(BlockPos position, Direction inward, int height) {
      for (int y = 0; y < height; y++) {
         if (!WorldUtils.isReplaceable(position.relative(inward).above(y + 1))) {
            return false;
         }
      }
      return true;
   }

   private PistonPlacement findPiston(BlockPos crystalPosition, Direction outward) {
      PistonPlacement selected = null;
      double distance = Double.MAX_VALUE;
      for (BlockPos position : this.pistonPositions(crystalPosition, outward)) {
         double candidateDistance = ClientAccess.zioMinecraft.player.getEyePosition().distanceTo(position.getCenter());
         if (candidateDistance >= distance) {
            continue;
         }
         Direction inward = outward.getOpposite();
         if (position.relative(inward).equals(crystalPosition) && WorldUtils.isPlacementOccupied(position.relative(inward, 2))) {
            continue;
         }
         BlockHitResult hit = this.findHit(position, this.module.rotateSetting.getValue() ? outward : null, true, (support, direction) -> {
            var state = ClientAccess.zioMinecraft.level.getBlockState(support);
            Block block = state.getBlock();
            return !state.isAir() && !(block instanceof PistonBaseBlock) && !(block instanceof PistonHeadBlock)
               && !(block instanceof MovingPistonBlock) && !(block instanceof FireBlock);
         });
         if (hit == null) {
            continue;
         }
         PowerPlacement power = this.module.getPowerItem() == Items.REDSTONE_TORCH
            ? this.findTorch(position, inward, crystalPosition) : this.findRedstone(position, inward, crystalPosition);
         if (power != null) {
            selected = new PistonPlacement(position, hit, power.position(), power.hit());
            distance = candidateDistance;
         }
      }
      return selected;
   }

   private List<BlockPos> pistonPositions(BlockPos crystalPosition, Direction outward) {
      List<BlockPos> positions = new ArrayList<>();
      int dx = outward.getStepX();
      int dz = outward.getStepZ();
      int minX = dx == 0 ? -1 : Math.min(0, dx);
      int maxX = dx == 0 ? 1 : Math.max(0, dx);
      int minZ = dz == 0 ? -1 : Math.min(0, dz);
      int maxZ = dz == 0 ? 1 : Math.max(0, dz);
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            for (int y = 0; y <= 1; y++) {
               if (x == 0 && y == 0 && z == 0) {
                  continue;
               }
               BlockPos position = crystalPosition.offset(x, y, z);
               if (!this.isPistonAngleValid(position) || !this.allowsPistonHead(position.relative(outward.getOpposite()))
                  || this.module.hasBlockingEntities(new AABB(position), this::isPlayer)) {
                  continue;
               }
               var state = ClientAccess.zioMinecraft.level.getBlockState(position);
               Block block = state.getBlock();
               if (block instanceof PistonBaseBlock || block instanceof MovingPistonBlock || block instanceof FireBlock || state.canBeReplaced()) {
                  positions.add(position);
               }
            }
         }
      }
      return positions;
   }

   private Set<Candidate> candidates(LivingEntity target) {
      int height = (int)target.getEyeHeight(target.getPose());
      Set<Candidate> candidates = new HashSet<>();
      for (BlockPos occupied : PlayerStateUtils.getOccupiedBlockPositions(target)) {
         if (ClientAccess.zioMinecraft.level.getBlockState(occupied).canBeReplaced()) {
            for (Direction outward : Direction.Plane.HORIZONTAL) {
               BlockPos position = occupied.relative(outward);
               if (!BoundingBoxUtils.intersectsBlock(target.getBoundingBox(), position)) {
                  candidates.add(new Candidate(position.offset(0, height, 0), outward));
               }
            }
         }
      }
      return candidates;
   }

   private PowerPlacement findTorch(BlockPos piston, Direction facing, BlockPos crystalPosition) {
      PowerPlacement selected = null;
      double distance = Double.MAX_VALUE;
      for (Direction direction : Direction.values()) {
         if (direction == facing || direction == Direction.DOWN) {
            continue;
         }
         BlockPos position = piston.relative(direction);
         if (position.equals(crystalPosition)) {
            continue;
         }
         double candidateDistance = ClientAccess.zioMinecraft.player.getEyePosition().distanceTo(position.getCenter());
         if (candidateDistance >= distance) {
            continue;
         }
         var state = ClientAccess.zioMinecraft.level.getBlockState(position);
         if (!state.canBeReplaced() && !(state.getBlock() instanceof RedstoneTorchBlock) && !(state.getBlock() instanceof FireBlock)) {
            continue;
         }
         BlockHitResult hit = this.findHit(position, null, false, (support, side) -> {
            if (side == Direction.UP && !WorldUtils.isPlacementOccupied(position.below()) || support.equals(piston)) {
               return false;
            }
            Block block = ClientAccess.zioMinecraft.level.getBlockState(support).getBlock();
            return !(block instanceof TorchBlock) && !(block instanceof PistonBaseBlock) && !(block instanceof PistonHeadBlock);
         });
         if (hit != null) {
            selected = new PowerPlacement(position, hit);
            distance = candidateDistance;
         }
      }
      return selected;
   }

   private PowerPlacement findRedstone(BlockPos piston, Direction facing, BlockPos crystalPosition) {
      PowerPlacement selected = null;
      double distance = Double.MAX_VALUE;
      for (Direction direction : Direction.values()) {
         if (direction == facing) {
            continue;
         }
         BlockPos position = piston.relative(direction);
         if (position.equals(crystalPosition) || position.getY() < crystalPosition.getY()) {
            continue;
         }
         double candidateDistance = ClientAccess.zioMinecraft.player.getEyePosition().distanceTo(position.getCenter());
         if (candidateDistance >= distance) {
            continue;
         }
         var state = ClientAccess.zioMinecraft.level.getBlockState(position);
         if (!state.canBeReplaced() && !state.is(Blocks.REDSTONE_BLOCK) || this.module.hasBlockingEntities(new AABB(position), this::isPlayer)) {
            continue;
         }
         BlockHitResult hit = this.findHit(position, null, true, (support, side) -> true);
         if (hit != null) {
            selected = new PowerPlacement(position, hit);
            distance = candidateDistance;
         }
      }
      return selected;
   }

   private BlockHitResult findHit(BlockPos position, Direction facing, boolean allowAir, BiPredicate<BlockPos, Direction> predicate) {
      boolean strict = BlockInteractionUtils.isStrictDirectionEnabled();
      if (this.module.airPlaceSetting.getValue() && allowAir) {
         List<Direction> directions = BlockInteractionUtils.getStrictDirectionsOrFallback(position, Direction.UP);
         return new BlockHitResult(position.getCenter(), directions.getFirst(), position, false);
      }
      for (Direction direction : Direction.values()) {
         BlockPos support = position.relative(direction);
         if (support.getY() >= WorldUtils.getTopY() || !WorldUtils.isPlacementOccupied(support)
            || predicate != null && !predicate.test(support, direction)
            || strict && !BlockInteractionUtils.getStrictDirections(support).contains(direction.getOpposite())) {
            continue;
         }
         Vec3 point = this.selectHitPoint(support, direction.getOpposite(), facing, this.module.placeRangeSetting.getValue());
         if (point != null) {
            return new BlockHitResult(point, direction.getOpposite(), support, false);
         }
      }
      return null;
   }

   private Vec3 selectHitPoint(BlockPos position, Direction side, Direction facing, float range) {
      for (Vec3 point : BlockSampleMode.X8.sampleFace(position, side)) {
         if (point.distanceTo(ClientAccess.zioMinecraft.player.getEyePosition()) > range) {
            continue;
         }
         if (facing == null) {
            return point;
         }
         float[] rotations = RotationManager.getRotationsToPosition(point);
         if (Math.abs(rotations[1]) <= MathConstants.FORTY_FIVE_DEGREES && Direction.fromYRot(rotations[0]) == facing) {
            return point;
         }
      }
      return null;
   }

   private boolean isPistonAngleValid(BlockPos position) {
      Vec3 eyes = ClientAccess.zioMinecraft.player.getEyePosition();
      double dx = eyes.x - position.getX() - 0.5D;
      double dz = eyes.z - position.getZ() - 0.5D;
      return Math.sqrt(dx * dx + dz * dz) > Math.abs(eyes.y - position.getY() - 0.5D);
   }

   private boolean allowsPistonHead(BlockPos position) {
      Block block = ClientAccess.zioMinecraft.level.getBlockState(position).getBlock();
      return block instanceof AirBlock || block instanceof FireBlock || block instanceof PistonHeadBlock
         || block instanceof MovingPistonBlock || block instanceof RedstoneTorchBlock;
   }

   private boolean isPlayer(Entity entity) {
      return !entity.isSpectator() && entity instanceof Player;
   }

   private boolean blocksCrystal(Entity entity) {
      return !entity.isSpectator() && (entity instanceof ItemEntity && entity.tickCount > 3 || entity instanceof LivingEntity);
   }

   private record Candidate(BlockPos position, Direction outward) { }
   private record PowerPlacement(BlockPos position, BlockHitResult hit) { }
   private record PistonPlacement(BlockPos position, BlockHitResult hit, BlockPos powerPosition, BlockHitResult powerHit) { }
}
