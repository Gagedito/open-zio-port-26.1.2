/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import dev.zio.client.utils.combat.DamageUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.world.WorldUtils;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.combat.AutoMineModule;
import dev.zio.client.modules.combat.AutoMineTargetSelector;
import dev.zio.client.modules.player.SpeedMineModule;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AutoMineTerrainProvider extends AutoMineProviderBase {
   private static SpeedMineModule speedMine;

   private boolean hasClearColumn(BlockPos position) {
      BlockPos abovePosition = position.above();
      if (!WorldUtils.isAir(abovePosition)) {
         return false;
      }

      AABB columnBounds = new AABB(position).expandTowards(0.0, 1.0D, 0.0);
      ClientLevel level = ClientAccess.zioMinecraft.level;
      Iterator<Entity> entities = level.entitiesForRendering().iterator();
      while (entities.hasNext()) {
         Entity entity = entities.next();
         if (!entity.isRemoved() && entity.isAlive() && !(entity instanceof EndCrystal)) {
            if (entity.getBoundingBox().intersects(columnBounds)) {
               return false;
            }
         }
      }
      return true;
   }

   public AutoMineTerrainProvider(AutoMineModule module) {
      super(module);
   }

   static {
      speedMine = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
   }

   private BlockPos selectTerrainCandidate(Player target, BlockPos basePosition, BlockPos referencePosition) {
      Set<BlockPos> occupiedPositions = PlayerStateUtils.getSurroundPositions(target);
      BlockPos minimumPosition = basePosition.offset(-2, -1, -2);
      BlockPos maximumPosition = basePosition.offset(2, -1, 2);
      List<TerrainMiningCandidate> scoredCandidates = BlockPos.betweenClosedStream(minimumPosition, maximumPosition)
         .map(BlockPos::immutable)
         .filter(position -> !occupiedPositions.contains(position.above()))
         .filter(position -> WorldUtils.isReplaceable(position) || this.canMinePosition(position))
         .filter(position -> this.hasClearColumn(position.above()) || WorldUtils.isAir(position))
         .map(position -> TerrainMiningCandidate.create(position, referencePosition, target))
         .toList();
      if (scoredCandidates.stream().anyMatch(candidate ->
         WorldUtils.isReplaceable(candidate.position()) && candidate.damage() >= 6.0D)) {
         return null;
      }

      TerrainMiningCandidate selectedCandidate = scoredCandidates.stream()
         .max(Comparator.comparingDouble(TerrainMiningCandidate::getPriorityScore)).orElse(null);
      if (selectedCandidate == null || WorldUtils.isAir(selectedCandidate.position())) {
         return null;
      }
      if (!selectedCandidate.currentlyMining() && selectedCandidate.damage() <= 4.0D) {
         return null;
      }
      if (!WorldUtils.isBedrockOrObsidian(selectedCandidate.position())
         && !WorldUtils.isReplaceable(selectedCandidate.position())) {
         return selectedCandidate.position();
      }
      return null;
   }

   @Override
   public void collectCandidates(AutoMineCandidates candidates) {
      AutoMineTargetSelector targetSelector = this.autoMine.targetSelector;
      Player target = targetSelector.selectTarget();
      if (target == null || this.hasNoCollisionBelow(target)) {
         return;
      }

      BlockPos targetPosition = BlockPos.containing(target.position());
      ClientLevel level = ClientAccess.zioMinecraft.level;
      Block baseBlock = level.getBlockState(targetPosition).getBlock();
      boolean resistantBase = baseBlock.getExplosionResistance() >= 6e+02F && baseBlock != Blocks.AIR;
      boolean occupiedPolicy = PlayerStateUtils.getSurroundPositions(target).stream()
         .anyMatch(position -> !WorldUtils.isBedrockOrObsidian(position));
      if (resistantBase && !occupiedPolicy) {
         return;
      }

      if (candidates.getPrimaryPosition() == null) {
         return;
      }
      if (candidates.getPrimaryPosition().getY() != targetPosition.getY()) {
         return;
      }

      BlockPos referencePosition = occupiedPolicy ? candidates.getSecondaryPosition() : candidates.getPrimaryPosition();
      BlockPos candidatePosition = this.selectTerrainCandidate(target, targetPosition, referencePosition);
      if (candidatePosition == null) {
         return;
      }

      int priority;
      if (occupiedPolicy) {
         priority = 700;
      } else {
         priority = 600;
      }
      candidates.offer(priority, (Consumer<AutoMineCandidates>)output -> {
         if (occupiedPolicy) {
            output.setPrimaryPosition(candidatePosition);
         } else {
            output.setSecondaryPosition(candidatePosition);
         }
      });
   }

   @Override
   public boolean isEnabled() {
      return this.autoMine.terrainSetting.getValue();
   }

   public static void bindModules(ModuleRegistry registry) {
      speedMine = (SpeedMineModule)registry.getModule(SpeedMineModule.class);
   }

   private record TerrainMiningCandidate(BlockPos position, double damage, boolean currentlyMining) {
      static TerrainMiningCandidate create(BlockPos position, BlockPos referencePosition, Player target) {
         Vec3 elevatedCenter = position.getCenter().add(0.0, 0.5D, 0.0);
         AABB targetBounds = target.getBoundingBox();
         BlockPos ignoredSupport = position.below();
         float damage = DamageUtils.getExplosionDamage(
            elevatedCenter, target, targetBounds, 6.0D, true, referencePosition, ignoredSupport
         );
         SpeedMineModule presenceSpeedMine = AutoMineTerrainProvider.speedMine;
         boolean currentlyMining = false;
         if (presenceSpeedMine.getExtraBreakTarget() != null) {
            currentlyMining = AutoMineTerrainProvider.speedMine.getExtraBreakTarget().getPosition().equals(position);
         }
         return new TerrainMiningCandidate(position, damage, currentlyMining);
      }

      public double getPriorityScore() {
         return this.currentlyMining ? Double.MAX_VALUE : this.damage;
      }
   }
}
