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
import dev.zio.client.events.ClientLevelRemoveEntityEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.utils.combat.BlockRaycastContext;
import dev.zio.client.modules.PlacementModule;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.combat.RaycastUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.utils.math.MathUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class AntiPearlModule extends PlacementModule {
   protected final Setting<Float> rangeSetting;
   protected final Setting<Float> predictSetting;
   protected final Setting<Boolean> airborneSetting;
   protected final Setting<Boolean> tryCrawlSetting;
   private final Map<BlockPos, Long> placementExpiryByPosition;
   private final Map<Integer, AbstractClientPlayer> nearbyPlayerByPearlId;
   private final Map<Integer, PendingPlacement> pendingPlacementByPearlId;

   private void prependSupportPositions(List<BlockPos> positions) {
      List<BlockPos> supportPositions = null;
      Iterator<BlockPos> positionsIterator = positions.iterator();

      while (positionsIterator.hasNext()) {
         BlockPos position = positionsIterator.next();

         boolean directionCheck = BlockInteractionUtils.isStrictDirectionEnabled();

         List<BlockPos> candidateSupports = BlockInteractionUtils.findSupportPath(
            position, 2, true, directionCheck
         );
         if (!candidateSupports.isEmpty()) {
            supportPositions = candidateSupports;
            break;
         }
      }

      if (supportPositions != null) {
         positions.addAll(0, supportPositions);
      }
   }

   @Override
   public void onToggle() {
      this.nearbyPlayerByPearlId.clear();

      this.pendingPlacementByPearlId.clear();
   }

   private void refreshAirbornePearls() {
      Iterable renderableEntities = ClientAccess.zioMinecraft.level.entitiesForRendering();
      Iterator entities = renderableEntities.iterator();

      while (entities.hasNext()) {
         Entity entity = (Entity)entities.next();
         if (entity instanceof ThrownEnderpearl) {
            Vec3 positionSnapshot = entity.position();

            Vec3 movementSnapshot = MathUtils.getPositionDelta(entity);
            int pearlId = entity.getId();
            this.predictBlockingPositions(positionSnapshot, movementSnapshot, pearlId);
         }
      }
   }

   private void processPearlSpawn(int pearlId, Vec3 position, Vec3 movement) {
      double verticalMotion = movement.y;

      if (!(verticalMotion < -1.4D)) {
         this.associateNearbyPlayer(position, pearlId);
         this.predictBlockingPositions(position, movement, pearlId);
      }
   }

   private boolean hasPlacementDirection(List<BlockPos> positions) {
      Iterator<BlockPos> positionsIterator = positions.iterator();

      while (positionsIterator.hasNext()) {
         BlockPos position = positionsIterator.next();

         boolean directionCheck = BlockInteractionUtils.isStrictDirectionEnabled();

         if (BlockInteractionUtils.findSupportDirection(position, directionCheck) != null) {
            return true;
         }
      }

      return false;
   }

   private void associateNearbyPlayer(Vec3 pearlPosition, int pearlId) {
      double nearestDistance = 999.0D;
      AbstractClientPlayer nearestPlayer = null;

      List levelPlayers = ClientAccess.zioMinecraft.level.players();
      Iterator players = levelPlayers.iterator();

      while (players.hasNext()) {
         AbstractClientPlayer player = (AbstractClientPlayer)players.next();

         Vec3 eyePositionSnapshot = player.getEyePosition();
         double distance = eyePositionSnapshot.distanceTo(pearlPosition);

         if (!(distance > 3.0D) && distance < nearestDistance) {
            nearestDistance = distance;
            nearestPlayer = player;
         }
      }

      if (nearestPlayer != null) {
         if (nearestPlayer != ClientAccess.zioMinecraft.player) {
            if (!ClientContext.socialRegistry.isFriendPlayer(nearestPlayer)) {
               Map<Integer, AbstractClientPlayer> playerMap = this.nearbyPlayerByPearlId;
               Integer playerPearlId = pearlId;
               playerMap.put(playerPearlId, nearestPlayer);
            }
         }
      }
   }

   private void predictBlockingPositions(Vec3 position, Vec3 movement, int pearlId) {
      Map<Integer, AbstractClientPlayer> playerMap = this.nearbyPlayerByPearlId;
      Integer trackedPearlId = pearlId;
      if (playerMap.get(trackedPearlId) == null) {
         return;
      }
      Map<Integer, PendingPlacement> pendingMap = this.pendingPlacementByPearlId;
      Integer pendingPearlId = pearlId;
      if (pendingMap.get(pendingPearlId) != null) {
         return;
      }

      double predictionScale = (this.predictSetting.getValue()).floatValue();
      Vec3 scaledMovement = movement.scale(predictionScale);
      Vec3 predictedPosition = position.add(scaledMovement);
      BlockRaycastContext.Builder contextBuilderSnapshot = new BlockRaycastContext.Builder(position, predictedPosition);
      Block blockMode = Block.COLLIDER;

      BlockHitResult hitResult = RaycastUtils.raycast(contextBuilderSnapshot.withBlockMode(blockMode).build());
      if (Type.MISS != hitResult.getType()) {
         if (!this.tryCrawlSetting.getValue()) {
            return;
         }
         predictedPosition = hitResult.getLocation();
      }

      AABB predictionBounds = AABB.ofSize(predictedPosition, 0.1D, 0.1D, 0.1D);
      Stream<BlockPos> boundsPositions = BlockPos.betweenClosedStream(predictionBounds);
      Function<BlockPos, BlockPos> immutablePosition = BlockPos::immutable;
      Stream<BlockPos> positionStream = boundsPositions.map(immutablePosition);
      Collector<BlockPos, ?, List<BlockPos>> positionCollector = Collectors.toList();
      List<BlockPos> blockingPositions = positionStream.collect(positionCollector);

      if (!this.hasPlacementDirection(blockingPositions)) {
         Setting<Boolean> airPlaceSettingRef = this.placementAirPlaceSetting;

         if (!airPlaceSettingRef.getValue()) {
            this.prependSupportPositions(blockingPositions);
         }
      }

      if (this.tryCrawlSetting.getValue()) {
         this.raiseCrawlPositions(blockingPositions);
      }

      Map<Integer, PendingPlacement> placementMap = this.pendingPlacementByPearlId;
      Integer boxedId = pearlId;
      AntiPearlModule.PendingPlacement pendingPlacement = new AntiPearlModule.PendingPlacement(
         blockingPositions, System.currentTimeMillis() + 100L
      );
      placementMap.put(boxedId, pendingPlacement);
   }

   private void raiseCrawlPositions(List<BlockPos> positions) {
      ArrayList<BlockPos> lowerPositions = new ArrayList<>();
      HashSet<BlockPos> raisedPositions = new HashSet<>();
      for (BlockPos position : positions) {
         if (!WorldUtils.isReplaceable(position)) {
            continue;
         }
         BlockPos below = position.below();
         if (!WorldUtils.isReplaceable(below)) {
            lowerPositions.add(position);
            raisedPositions.add(position.above());
         }
      }
      if (!lowerPositions.isEmpty()) {
         positions.removeAll(lowerPositions);
      }
      if (!raisedPositions.isEmpty()) {
         positions.addAll(raisedPositions);
      }
   }

   @Override
   protected void onPlacementAttempt(BlockPos position, boolean placementFailed) {
      if (this.tryCrawlSetting.getValue()) {
         Map<BlockPos, Long> placementExpiries = this.placementExpiryByPosition;
         BlockPos immutablePosition = position.immutable();
         long expiresAt = System.currentTimeMillis() + 250L;
         placementExpiries.put(immutablePosition, expiresAt);
      }
   }

   @EventHandler
   public void onClientLevelRemoveEntityEvent(ClientLevelRemoveEntityEvent event) {
      Map<Integer, AbstractClientPlayer> playerMap = this.nearbyPlayerByPearlId;
      int entityId = event.getId();
      Integer removedPearlId = entityId;
      playerMap.remove(removedPearlId);
   }

   @Override
   public List<BlockPos> getPlacementPositions() {
      if (this.airborneSetting.getValue()) {
         this.refreshAirbornePearls();
      }

      ArrayList<BlockPos> positions = new ArrayList<>();

      Collection<PendingPlacement> placementEntries = this.pendingPlacementByPearlId.values();
      Consumer<AntiPearlModule.PendingPlacement> appendPositions = pendingPlacement -> {
         AntiPearlModule.PendingPlacement pendingArgument = pendingPlacement;
         List<BlockPos> pendingPositions = pendingArgument.positions();
         positions.addAll(pendingPositions);
      };
      placementEntries.forEach(appendPositions);

      Collection<PendingPlacement> expiryEntries = this.pendingPlacementByPearlId.values();
      Predicate<AntiPearlModule.PendingPlacement> isExpired = pendingPlacement -> {
         long now = System.currentTimeMillis();
         AntiPearlModule.PendingPlacement pendingArgument = pendingPlacement;
         return now > pendingArgument.expiresAtMillis();
      };
      expiryEntries.removeIf(isExpired);
      Collection<Long> placementExpiries = this.placementExpiryByPosition.values();
      placementExpiries.removeIf(expiresAt -> System.currentTimeMillis() > expiresAt);
      Predicate<BlockPos> isUnavailable = position -> {
         BlockPos positionArgument = position;

         Vec3 positionCenterSnapshot = positionArgument.getCenter();

         double distance = positionCenterSnapshot.distanceTo(ClientAccess.zioMinecraft.player.getEyePosition());

         Float range = this.rangeSetting.getValue();
         return distance > range.floatValue() || this.placementExpiryByPosition.containsKey(position);
      };
      positions.removeIf(isUnavailable);
      return positions;
   }

   @EventHandler
   public void onPearlSpawnPacket(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundAddEntityPacket spawnPacket) {
         if (spawnPacket.getType() == EntityType.ENDER_PEARL) {
            Vec3 spawnPosition = new Vec3(spawnPacket.getX(), spawnPacket.getY(), spawnPacket.getZ());
            var taskExecutor = ClientAccess.zioMinecraft;
            Runnable processSpawn = () -> {
               int queuedIdSnapshot = spawnPacket.getId();
               Vec3 queuedMovement = spawnPacket.getMovement();
               this.processPearlSpawn(queuedIdSnapshot, spawnPosition, queuedMovement);
            };

            taskExecutor.execute(processSpawn);
         }
      }
   }

   public AntiPearlModule() {
      super("AntiPearl", "Stops ender pearls mid-air.", ModuleCategory.COMBAT, new String[]{"pearlblocker"});
      this.rangeSetting = this.add(new NumberSetting<>("Range", 5.0F, 1.0F, 6.0F).withUnit("m"));
      this.predictSetting = this.add(new NumberSetting<>("Predict", 3.0F, 2.0F, 4.0F));
      this.airborneSetting = this.add(new BooleanSetting("Airborne", false));
      this.tryCrawlSetting = this.add(new BooleanSetting("TryCrawl", false));
      this.placementExpiryByPosition = new HashMap<>();
      this.nearbyPlayerByPearlId = new HashMap<>();
      this.pendingPlacementByPearlId = new HashMap<>();
      Setting<Boolean> settingSnapshot = this.placementAttackSetting;
      Boolean attackDefaultBoxed = false;

      settingSnapshot.setValue(attackDefaultBoxed);
      settingSnapshot = this.placementJumpSetting;
      Boolean jumpDefaultSnapshot = false;

      settingSnapshot.setDefaultValue(jumpDefaultSnapshot);

      this.ignoreMissingPlacementBlocks();
   }

   record PendingPlacement(
      List<BlockPos> positions,
      long expiresAtMillis
   ) {}
}
