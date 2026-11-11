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
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.BlockUpdateAppliedEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.combat.ExtraBreakTarget;
import dev.zio.client.utils.movement.WallsVelocityHandler;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.modules.PlacementModule;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.combat.BlockRaycastClassifier;
import dev.zio.client.utils.combat.BlockRaycastOverride;
import dev.zio.client.settings.options.BlockerTargetOffsets;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.managers.WorldStateManager;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.exploit.MultiTaskModule;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FeetPlaceModule extends PlacementModule {
   private static AntiCheatModule antiCheat;
   private static SpeedMineModule speedMine;
   private final Setting<Boolean> fireworkSetting;
   private final Setting<SequentialMode> sequentialSetting;
   private final Setting<Boolean> centerSetting;
   private final Setting<Boolean> flattenSetting;
   private final Setting<Boolean> coverSetting;
   private final Setting<Boolean> smartFaceSetting;
   private final Setting<BlockIgnoreMode> blockIgnoreSetting;
   private final Setting<Boolean> onlyObbySetting;
   private final Setting<Boolean> instantSetting;
   private final FeetPlaceFireworkSupport fireworkSupport;
   private boolean recentPositionCorrection;
   private boolean sequentialPlacementComplete;
   private int nearbyCrystalCount;
   private boolean centered;

   @Override
   protected BlockPos getPlacementOrigin() {


      if (!EntityUtils.hasCollisionBelow(ClientAccess.zioMinecraft.player)) {
         double x = ClientAccess.zioMinecraft.player.getX();

         double y = ClientAccess.zioMinecraft.player.getY();

         double z = ClientAccess.zioMinecraft.player.getZ();
         return BlockPos.containing(x, y, z);
      } else {
         return super.getPlacementOrigin();
      }
   }

   @Override
   protected boolean disableForJumpOrSleep() {
      return this.recentPositionCorrection
         ? false
         : super.disableForJumpOrSleep();
   }

   @Override
   protected boolean shouldDiscardAttackedCrystal() {
      if (!this.instantSetting.getValue()) {
         if (!RotationManager.isMovementSyncEnabled()) {
            return false;
         }
      }

      return true;
   }

   @Override
   protected int getPlacementRotationPriority() {
      return 999;
   }

   @Override
   protected InventoryUtils.SwapSlot findPlacementSwapSlot() {
      if (this.onlyObbySetting.getValue()) {
         Item obsidian = Items.OBSIDIAN;

         return InventoryUtils.findSwapSlot(obsidian, false);
      } else {
         return super.findPlacementSwapSlot();
      }
   }

   static {
      antiCheat = (AntiCheatModule)ClientContext.getModule(AntiCheatModule.class);
      speedMine = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
   }

   @Override
   public void onEnable() {
      this.nearbyCrystalCount = 0;
      if (!this.isWorldUnavailable()) {
         this.centered = false;

         this.countNearbyCrystals();
      }

      super.onEnable();

      this.fireworkSupport.onEnable();
   }

   private void countNearbyCrystals() {
      Set<BlockPos> occupiedPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.zioMinecraft.player);

      Iterable<Entity> renderEntities = ClientAccess.zioMinecraft.level.entitiesForRendering();
      Iterator<Entity> entities = renderEntities.iterator();

      while (entities.hasNext()) {
         Entity entity = entities.next();
         if (entity instanceof EndCrystal) {
            BlockPos crystalBase = entity.blockPosition().below();
            if (occupiedPositions.contains(crystalBase)) {
               this.nearbyCrystalCount++;
            }
         }
      }
   }

   private boolean isIgnoredMiningPosition(BlockPos position) {
      if (!speedMine.isToggled()) {
         return false;
      }

      BlockPos miningPosition = speedMine.getRebreakPos();
      return miningPosition == null ? false : miningPosition.equals(position);
   }

   private boolean isIgnoredBlockPosition(BlockPos position, Set<BlockPos> enemyPositions) {
      return switch (this.blockIgnoreSetting.getValue()) {
         case NONE -> false;
         case MINING -> this.isIgnoredMiningPosition(position);
         case ENEMY -> enemyPositions.contains(position);
         case BOTH -> this.isIgnoredMiningPosition(position) || enemyPositions.contains(position);
      };
   }

   private Set<BlockPos> collectEnemyPositions() {
      Set<BlockPos> positions = new HashSet<>();
      for (Player player : ClientAccess.zioMinecraft.level.players()) {
         if (!player.isAlive() || player == ClientAccess.zioMinecraft.player
            || player.distanceTo(ClientAccess.zioMinecraft.player) > 4.0F
            || ClientContext.socialRegistry.isFriendPlayer(player)) {
            continue;
         }
         for (BlockPos occupied : PlayerStateUtils.getOccupiedBlockPositions(player)) {
            BlockState state = ClientAccess.zioMinecraft.level.getBlockState(occupied);
            if (state.isAir() || BlockRaycastClassifier.IGNORE_LOW_RESISTANCE.test(occupied, state) == BlockRaycastOverride.IGNORE) {
               for (Vec3i offset : BlockerTargetOffsets.SURROUND.getOffsets()) {
                  positions.add(occupied.offset(offset));
               }
            }
         }
      }
      return positions;
   }

   private int getPlayerHeightInBlocks() {
      double height = ClientAccess.zioMinecraft.player.getBbHeight();
      return (int)Math.ceil(height);
   }

   private boolean shouldSkipFlattenPosition(BlockPos position) {
      BlockPos placementPosition = ClientContext.worldStateManager.getUnconfirmedBreak();

      if (ClientContext.holeManager.intersectsHole(position)) {
         if (!ClientAccess.zioMinecraft.player.onGround()) {
            return true;
         }
      }

      if (PlayerStateUtils.isLocalLowProfile()) {
         if (!antiCheat.is2b2tEnabled()) {
            return true;
         }
      }

      if (!position.equals(placementPosition)) {
         if (speedMine.getExtraBreakTarget() != null) {
            ExtraBreakTarget miningAction = speedMine.getExtraBreakTarget();

            if (position.equals(miningAction.getPosition())) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private Comparator<BlockPos> getPlacementComparator() {
      double latency = ClientContext.movementSpeedTracker.getCurrentSpeed();

      Comparator<BlockPos> directionComparator;
      if (latency <= 10.0D) {
         Function<BlockPos, Float> playerYawKey = position -> {
            float playerYaw = ClientAccess.zioMinecraft.player.getYRot();
            BlockPos targetPosition = position;
            float capturedYaw = playerYaw;

            return this.getPlacementPriority(capturedYaw, targetPosition);
         };
         directionComparator = Comparator.comparing(playerYawKey);
      } else {
         Function<BlockPos, Float> movementYawKey = position -> {
            float movementYaw = MovementPhysics.getMovementYaw();

            BlockPos targetPosition = position;
            float capturedYaw = movementYaw;

            return this.getYawPlacementCost(capturedYaw, targetPosition);
         };
         directionComparator = Comparator.comparing(movementYawKey);
      }

      Function<BlockPos, Boolean> pendingPlacementKey = candidate -> {
         WorldStateManager worldState = ClientContext.worldStateManager;

         BlockPos targetPosition = candidate;

         boolean pendingPlacement = worldState.hasPendingPlacement(targetPosition);
         return pendingPlacement;
      };
      Comparator<BlockPos> pendingPlacementComparator = Comparator.comparing(pendingPlacementKey);
      return pendingPlacementComparator.thenComparing(directionComparator);
   }

   public static boolean centerPlayer() {
      if (!ClientAccess.zioMinecraft.player.onGround()) {
         return false;
      }

      if (ClientContext.holeManager.isInHole()) {
         return false;
      }

      Set<BlockPos> occupiedPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.zioMinecraft.player);
      int occupiedPositionCount = occupiedPositions.size();

      if (occupiedPositionCount <= 4) {
         return false;
      }

      if (WallsVelocityHandler.hasRaisedPlayerCollision()) {
         return false;
      }

      Vec3 originalPosition = ClientAccess.zioMinecraft.player.position();
      Vec3 centerPosition = new Vec3(
         Math.floor(ClientAccess.zioMinecraft.player.getX()) + 0.5D,
         ClientAccess.zioMinecraft.player.getY(),
         Math.floor(ClientAccess.zioMinecraft.player.getZ()) + 0.5D
      );
      Vec3 centerDelta = centerPosition.subtract(originalPosition);
      double distance = centerDelta.length();

      double baseMoveSpeed = MovementPhysics.getBaseMoveSpeed(false);

      double maximumStep = baseMoveSpeed + 0.02D;
      if (distance > maximumStep) {
         double stepFraction = maximumStep / distance;
         Vec3 remainingDelta = centerPosition.subtract(originalPosition);
         Vec3 stepDelta = remainingDelta.scale(stepFraction);
         centerPosition = originalPosition.add(stepDelta);
      }

      ClientAccess.zioMinecraft.player.setPos(centerPosition);
      double movedDistance = originalPosition.distanceTo(centerPosition);

      return movedDistance <= 0.01D;
   }

   @Override
   protected void runPlacementCycle() {


      if (!ClientAccess.zioMinecraft.player.isPassenger()) {
         ElapsedTimer timer = ClientContext.serverStateManager.getTeleportAckTimer();

         this.recentPositionCorrection = !timer.hasElapsed(
            100L
         );
         if (this.recentPositionCorrection) {
            LocalPlayer heightPlayer = ClientAccess.zioMinecraft.player;
            this.placementBaselineY = heightPlayer.getY();
         }

         try {
            if (this.fireworkSetting.getValue()) {
               MultiTaskModule multiTask = multiTaskModule;

               if (!multiTask.isItemUseRestricted()) {
                  Minecraft client = ClientAccess.zioMinecraft;
                  Runnable processFireworks = () -> {
                     FeetPlaceFireworkSupport support = this.fireworkSupport;
                     Setting<Boolean> placementAirPlaceSettingSnapshot = this.placementAirPlaceSetting;

                     boolean airPlaceEnabled = placementAirPlaceSettingSnapshot.getValue();
                     Setting<Boolean> raytraceSetting = this.placementRaytraceSetting;

                     boolean raytraceEnabled = raytraceSetting.getValue();

                     support.processFireworks(airPlaceEnabled, raytraceEnabled);
                  };

                  client.execute(processFireworks);
               }
            }

            super.runPlacementCycle();
         } catch (Throwable ignoredFailure) {
         }
      }
   }

   private List<BlockPos> collectPlacementPositions(List<Integer> visitedHashes, BlockPos anchor, int offsetY) {
      InventoryUtils.SwapSlot blockSlot = this.findPlacementSwapSlot();
      Block defaultPlacementBlock = Blocks.OBSIDIAN;

      BlockState placementState = defaultPlacementBlock.defaultBlockState();
      if (blockSlot.isFound()) {
         ItemStack placementStack = blockSlot.getStack();

         if (placementStack.getItem() instanceof BlockItem blockItem) {
            Block placementBlock = blockItem.getBlock();
            placementState = placementBlock.defaultBlockState();
         }
      }

      List<BlockPos> placements = new ArrayList<>();
      int anchorHashCode = anchor.hashCode();
      Integer anchorHash = anchorHashCode;
      visitedHashes.add(anchorHash);

      Iterator<Vec3> offsetIterator = this.getRelativePlacementOffsets(offsetY).iterator();

      while (offsetIterator.hasNext()) {
         Vec3 offset = offsetIterator.next();
         BlockPos blockOffset = BlockPos.containing(offset);
         BlockPos position = anchor.offset(blockOffset);

         if (this.flattenSetting.getValue()) {
            double flattenOffsetY = offset.y;

            if (flattenOffsetY == -1.0D) {
               if (this.shouldSkipFlattenPosition(position)) {
                  continue;
               }
            }
         }

         if (this.coverSetting.getValue()) {
            double coverOffsetY = offset.y();

            if (coverOffsetY == this.getPlayerHeightInBlocks()) {
               if (PlayerStateUtils.isLocalLowProfile()) {
                  if (!RotationManager.is2b2tMode()) {
                     continue;
                  }
               }
            }
         }

         BlockState collisionState = placementState;
         ClientLevel collisionLevel = ClientAccess.zioMinecraft.level;
         BlockPos collisionOrigin = BlockPos.ZERO;
         VoxelShape collisionShape = collisionState.getCollisionShape(collisionLevel, collisionOrigin);
         AABB workingBounds;
         if (collisionShape.isEmpty()) {
            workingBounds = new AABB(position);
         } else {
            workingBounds = collisionShape.bounds();
            workingBounds = BoundingBoxUtils.placeAtBlock(workingBounds, position);
         }

         AABB placementBounds = workingBounds;

         this.breakInstantlyMineableBlock(position);

         BlockState positionState = ClientAccess.zioMinecraft.level.getBlockState(position);
         boolean occupied = !positionState.canBeReplaced();

         if (ClientContext.worldStateManager.hasActiveGlassUpdate(position)) {
            occupied = false;
         }

         if (occupied) {
            workingBounds = ClientAccess.zioMinecraft.player.getBoundingBox();
            AABB positionBounds = new AABB(position);

            if (!BoundingBoxUtils.hasVolumeOverlap(workingBounds, positionBounds)) {
               continue;
            }
         }

         ClientLevel entityLevel = ClientAccess.zioMinecraft.level;
         Class<LivingEntity> entityType = LivingEntity.class;
         Predicate<LivingEntity> blocksPlacement = entity -> {
            if (entity != ClientAccess.zioMinecraft.player) {
               LivingEntity candidateEntity = entity;

               AABB entityBounds = candidateEntity.getBoundingBox();
               if (!BoundingBoxUtils.hasVolumeOverlap(placementBounds, entityBounds)) {
                  return false;
               }
            }

            return true;
         };
         List<LivingEntity> blockingEntities = entityLevel.getEntitiesOfClass(entityType, placementBounds, blocksPlacement);
         if (!blockingEntities.isEmpty()) {
            int positionHashCode = position.hashCode();
            Integer positionHash = positionHashCode;
            if (!visitedHashes.contains(positionHash) && offset.y == offsetY) {
               placements.addAll(this.collectPlacementPositions(visitedHashes, position, offsetY));
               continue;
            }
         }

         if (!occupied) {
            boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();
            Direction placementFace = BlockInteractionUtils.findSupportDirection(position, strictDirection);
            if (placementFace == null) {
               Setting<Boolean> placementAirPlaceSettingSnapshot = this.placementAirPlaceSetting;

               if (!placementAirPlaceSettingSnapshot.getValue()) {
                  boolean strictSupportDirection = BlockInteractionUtils.isStrictDirectionEnabled();

                  BlockPos supportPosition = BlockInteractionUtils.findSupportPosition(
                     position, 2, true, strictSupportDirection
                  );
                  if (supportPosition != null) {
                     placements.add(supportPosition);
                  }
               }
            }

            placements.add(position);
         }
      }

      return placements;
   }

   @Override
   protected void sortPlacementPositions(List positions) {


      positions.sort(this.getPlacementComparator());
   }

   private List<Vec3> getRelativePlacementOffsets(int offsetY) {
      List<Vec3> offsets = new ArrayList<>(List.of(
         new Vec3(-1.0D, offsetY, 0.0),
         new Vec3(1.0D, offsetY, 0.0),
         new Vec3(0.0, offsetY, 1.0D),
         new Vec3(0.0, offsetY, -1.0D)
      ));

      if (this.nearbyCrystalCount > 3) {
         if (this.smartFaceSetting.getValue()) {
            Stream<Vec3> offsetStream = offsets.stream();
            Function<Vec3, Vec3> raiseOffset = offset -> {
               return offset.add(0.0D, 1.0D, 0.0D);
            };
            Stream<Vec3> raisedOffsetStream = offsetStream.map(raiseOffset);
            List<Vec3> raisedOffsets = raisedOffsetStream.toList();
            offsets.addAll(raisedOffsets);
         }
      }

      if (this.flattenSetting.getValue()) {
         Vec3 flattenOffset = new Vec3(0.0, offsetY - 1, 0.0);
         offsets.add(flattenOffset);
      }

      if (this.coverSetting.getValue()) {
         Vec3 coverOffset = new Vec3(0.0, offsetY + this.getPlayerHeightInBlocks(), 0.0);
         offsets.add(coverOffset);
      }

      return offsets;
   }

   private float getPlacementPriority(float yaw, BlockPos position) {
      int positionY = position.getY();

      BlockPos playerBlockPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      if (positionY == playerBlockPosition.getY()) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;

         if (this.hasNontrivialSurroundCollision(player)) {
            BlockPos distanceOrigin = PlayerStateUtils.getLocalRoundedBlockPosition();
            double targetX = position.getX();
            double targetY = position.getY();
            double targetZ = position.getZ();
            return (float)distanceOrigin.distToLowCornerSqr(targetX, targetY, targetZ);
         }
      }

      float capturedYaw = yaw;
      return this.getYawPlacementCost(capturedYaw, position);
   }

   @EventHandler
   private void runSequentialPlacement(BlockUpdateAppliedEvent event) {
      if (this.sequentialSetting.getValue() != SequentialMode.NONE
         && (this.sequentialSetting.getValue() == SequentialMode.STRONG || !this.sequentialPlacementComplete)) {
         this.runPlacementCycle();

         this.sequentialPlacementComplete = this.hasPlacedThisCycle();
      }
   }

   @EventHandler
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      this.sequentialPlacementComplete = false;
      if (!this.centered) {
         if (this.centerSetting.getValue()) {
            if (!ClientAccess.zioMinecraft.player.isPassenger()) {
               this.centered = centerPlayer();
            }
         }
      }
   }

   @Override
   public List getPlacementPositions() {


      Boolean fireworkValue = this.fireworkSetting.getValue();
      Set<BlockPos> trackedPositions;
      if (fireworkValue) {
         trackedPositions = EntityUtils.getFireworkPositions();
      } else {
         trackedPositions = Collections.emptySet();
      }

      Set<BlockPos> fireworkPositions = trackedPositions;
      List<Integer> visitedHashes = new ArrayList<>();

      BlockPos placementOrigin = this.getPlacementOrigin();
      List<BlockPos> positions = this.collectPlacementPositions(visitedHashes, placementOrigin, 0);
      Set<BlockPos> enemyPositions = this.blockIgnoreSetting.getValue() == BlockIgnoreMode.ENEMY
         || this.blockIgnoreSetting.getValue() == BlockIgnoreMode.BOTH ? this.collectEnemyPositions() : Collections.emptySet();
      Predicate<BlockPos> discardPosition = position -> {
         if (!this.isIgnoredBlockPosition(position, enemyPositions)) {
            if (this.fireworkSetting.getValue()) {
               if (this.fireworkSupport.isTrackedFireworkPosition(fireworkPositions, position)) {
                  return true;
               }
            }

            return false;
         } else {
            return true;
         }
      };
      positions.removeIf(discardPosition);
      return positions;
   }

   @EventHandler
   private void observeSurroundPackets(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket positionPacket) {
         Vec3 playerPosition = ClientAccess.zioMinecraft.player.position();

         PositionMoveRotation packetChange = positionPacket.change();
         Vec3 packetPosition = packetChange.position();
         double distanceSquared = playerPosition.distanceToSqr(packetPosition);

         if (distanceSquared > 9.0D) {
            PositionMoveRotation freshPacketChange = positionPacket.change();
            this.placementBaselineY = freshPacketChange.position().y;

            ElapsedTimer correctionTimer = ClientContext.serverStateManager.getTeleportAckTimer();

            correctionTimer.reset();
         }
      }
      if (event.getPacket() instanceof ClientboundAddEntityPacket entityPacket) {
         if (entityPacket.getType() == EntityType.END_CRYSTAL) {
            if (this.smartFaceSetting.getValue()) {
               double spawnX = entityPacket.getX();
               double spawnY = entityPacket.getY();
               double spawnZ = entityPacket.getZ();
               BlockPos crystalPosition = BlockPos.containing(spawnX, spawnY, spawnZ);
               Minecraft client = ClientAccess.zioMinecraft;
               Runnable countSpawnedCrystal = () -> {
                  Set<BlockPos> spawnSurroundPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.zioMinecraft.player);
                  BlockPos crystalBase = crystalPosition.below();
                  if (spawnSurroundPositions.contains(crystalBase)) {
                     this.nearbyCrystalCount++;
                  }
               };

               client.execute(countSpawnedCrystal);
            }
         }
      }

      if (this.fireworkSetting.getValue()) {
         this.fireworkSupport.observeSpawnPacket(event);
      }
   }

   private float getYawPlacementCost(float yaw, BlockPos position) {
      Vec3 targetCenter = position.getCenter();

      float targetYaw = RotationManager.getRotationsToPosition(targetCenter)[0];

      float yawDifference = Mth.degreesDifferenceAbs(yaw, targetYaw);
      float height = position.getY();

      return yawDifference + height * 0.001F;
   }

   private boolean hasNontrivialSurroundCollision(LivingEntity entity) {
      Set<BlockPos> occupiedPositions = PlayerStateUtils.getOccupiedBlockPositions(entity);
      Iterator<BlockPos> positions = occupiedPositions.iterator();

      while (positions.hasNext()) {
         BlockPos position = positions.next();

         BlockState blockState = ClientAccess.zioMinecraft.level.getBlockState(position);
         ClientLevel collisionLevel = ClientAccess.zioMinecraft.level;
         BlockPos collisionOrigin = BlockPos.ZERO;
         VoxelShape shape = blockState.getCollisionShape(collisionLevel, collisionOrigin);
         if (!shape.isEmpty()) {
            AABB bounds = shape.bounds();

            if (!BoundingBoxUtils.hasUnitDimensions(bounds)) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   protected boolean shouldHandleCrystalSpawn() {
      if (RotationManager.isMovementSyncEnabled()) {
         return this.instantSetting.getValue();
      } else {
         return ClientContext.crystalInteractionManager.isInstantAttackEnabled();
      }
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      antiCheat = (AntiCheatModule)modules.getModule(
         AntiCheatModule.class
      );
      speedMine = (SpeedMineModule)modules.getModule(
         SpeedMineModule.class
      );
   }

   public FeetPlaceModule() {
      super("FeetPlace", "Surrounds your feet with obby.", ModuleCategory.COMBAT, new String[]{"surround"});

      this.fireworkSetting = this.add(new BooleanSetting("Firework", false));
      this.sequentialSetting = this.add(new EnumSetting<>("Sequential", SequentialMode.NONE));
      this.centerSetting = this.add(new BooleanSetting("Center", true));
      this.flattenSetting = this.add(new BooleanSetting("Flatten", true));
      this.coverSetting = this.add(new BooleanSetting("Cover", false));
      this.smartFaceSetting = this.add(new BooleanSetting("SmartFace", false));
      this.blockIgnoreSetting = this.add(new EnumSetting<>("BlockIgnore", BlockIgnoreMode.NONE));
      this.onlyObbySetting = this.add(new BooleanSetting("OnlyObby", false));
      this.instantSetting = this.add(new BooleanSetting("Instant", false));
      this.fireworkSupport = new FeetPlaceFireworkSupport();

      this.setDisableOnRespawn(true);
   }

   private enum BlockIgnoreMode implements NamedEntry {
      NONE("None"), MINING("Mining"), ENEMY("Enemy"), BOTH("Both");

      private final String displayName;

      BlockIgnoreMode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   private enum SequentialMode implements NamedEntry {
      NONE("None"),
      STRICT("Strict"),
      STRONG("Strong");

      private final String displayName;

      SequentialMode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }
}
