/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import java.util.Set;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.PlayerIsStayingOnGroundSurfaceEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.CrystalInteractionManager;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.combat.BlockRaycastContext;
import dev.zio.client.render.FeatureBoxRenderer;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.managers.TickRateController;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.combat.RaycastUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.ScaffoldSwapChoice;
import dev.zio.client.settings.options.ListFilterMode;

import dev.zio.mixin.BlockBehaviourAccessor;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ScaffoldModule extends Module {
   private static AntiCheatModule antiCheatModule = (AntiCheatModule)ClientContext.getModule(AntiCheatModule.class);
   private final Setting<ScaffoldMode> modeSetting;
   private final Setting<Integer> sizeSetting;
   private final Setting<Integer> frequencySetting;
   private final Setting<Set<Block>> blocksSetting;
   private final Setting<ListFilterMode> selectionSetting;
   private final Setting<ScaffoldSwapChoice> swapSetting;
   private final Setting<Integer> delaySetting;
   final Setting<Float> offsetSetting;
   public final Setting<TowerChoice> towerSetting;
   public final Setting<Boolean> safeWalkSetting;
   final Setting<Boolean> airPlaceSetting;
   final Setting<Boolean> tellySetting;
   private final Setting<Boolean> rotateSetting;
   public final Setting<Boolean> attackSetting;
   final Setting<Boolean> renderSetting;
   private final Setting<Float> lineWidthSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;
   final Setting<Boolean> fadeSetting;
   private final Setting<Float> fadeTimeSetting;
   private final ElapsedTimer targetTimeoutTimer;
   private final ElapsedTimer rotationTimeoutTimer;
   private final ElapsedTimer placementDelayTimer;
   private final ElapsedTimer inventoryClickTimer;
   private final ElapsedTimer attackTimer;
   private final List<ScaffoldPlacementTarget> placementTargets;
   private float[] cachedRotations;
   private int lastTowerBlockY;
   int blockSlot;
   double tellyBaseY;
   private float savedJumpStrength;

   @Override
   public void onEnable() {
      if (!this.isWorldUnavailable()) {
         this.tellyBaseY = ClientAccess.zioMinecraft.player.getY();
      }
   }

   @Override
   public void onToggle() {
      ElapsedTimer timer = this.targetTimeoutTimer;
      timer.reset();
      timer = this.rotationTimeoutTimer;

      timer.reset();
      this.placementTargets.clear();
      this.cachedRotations = null;
      this.lastTowerBlockY = 0;
      this.blockSlot = -1;
   }

   private float getJumpStrength() {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      Holder jumpAttribute = Attributes.JUMP_STRENGTH;
      return (float)player.getAttribute(jumpAttribute).getBaseValue();
   }

   private void tickPlacement() {
      ClientContext.playerTickController.releaseMultiplier(this);
      if (this.savedJumpStrength > 0.0F) {
         float savedStrength = this.savedJumpStrength;

         this.setJumpStrength(savedStrength);

         this.savedJumpStrength = -1.0F;
      }

      if (ClientAccess.zioMinecraft.player.isHandsBusy()) {
         return;
      }
      ElapsedTimer timer = this.inventoryClickTimer;
      if (!timer.hasElapsed(50L)) {
         if (ClientAccess.zioMinecraft.player.onGround()) {
            if (antiCheatModule.movementSyncSetting.getValue()) {
               return;
            }
         }
      }

      if (!MovementPhysics.isLocalPlayerMoving() || ClientAccess.zioMinecraft.player.horizontalCollision) {
         this.tellyBaseY = ClientAccess.zioMinecraft.player.getY();
      }

      this.selectBlockSlot();

      List<ScaffoldPlacementTarget> targets;
      if (this.modeSetting.getValue() == ScaffoldMode.MULTI) {
         targets = ScaffoldPlacementTarget.findMultiFor(this, this.sizeSetting.getValue(), this.frequencySetting.getValue());
      } else {
         ScaffoldPlacementTarget target = ScaffoldPlacementTarget.findFor(this);
         targets = target == null ? List.of() : List.of(target);
      }
      this.placementTargets.clear();
      this.placementTargets.addAll(targets);
      boolean missingUsableBlock = true;
      if (this.blockSlot != -1) {
         Inventory blockInventory = ClientAccess.zioMinecraft.player.getInventory();
         int currentBlockSlot = this.blockSlot;
         missingUsableBlock = blockInventory.getItem(currentBlockSlot).isEmpty();
      }

      if (this.swapSetting.getValue() == ScaffoldSwapChoice.NONE) {
         ItemStack heldStack = ClientAccess.zioMinecraft.player.getMainHandItem();

         if (this.scoreBlockStack(heldStack) == 0) {
            missingUsableBlock = true;
         }
      }

      if (missingUsableBlock) {
         this.placementTargets.clear();
      } else {
         for (ScaffoldPlacementTarget target : this.placementTargets) {
            if (this.attackBlockingEntity(target)) {
               break;
            }
         }

         this.updateTower();
         timer = this.placementDelayTimer;
         long delayMillis = (this.delaySetting.getValue()).intValue();
         if (timer.hasElapsed(delayMillis)) {
            for (ScaffoldPlacementTarget target : this.placementTargets) {
               if (this.canPlaceAt(target.position(), this.blockSlot)) {
                  ScaffoldSwapChoice placementSwap = this.swapSetting.getValue();
                  int placementSlot = this.blockSlot;
                  placementSwap.runWithSlot(placementSlot, () -> this.placeCurrentTarget(target));
               }
            }
         }
      }

      this.updatePlacementRotation();
      timer = this.targetTimeoutTimer;

      if (timer.hasElapsed(150L)) {
         timer = this.targetTimeoutTimer;

         timer.reset();
         this.placementTargets.clear();
      }
   }

   private enum ScaffoldMode implements NamedEntry {
      SINGLE("Single"), MULTI("Multi");
      private final String name;
      ScaffoldMode(String name) { this.name = name; }
      @Override public String getName() { return this.name; }
   }

   private void placeCurrentTarget(ScaffoldPlacementTarget target) {
      if (this.renderSetting.getValue()) {
         FeatureBoxRenderer boxRenderer = ClientContext.featureBoxRenderer;

         BlockPos renderPosition = target.position();
         boxRenderer.addBox(this, renderPosition);
      }

      ScaffoldPlacementTarget placementTargetSnapshot = target;
      ScaffoldPlacementGeometry geometry = ScaffoldPlacementGeometry.fromTarget(
         placementTargetSnapshot, true
      );

      Vec3 aimPosition = geometry.aimPosition();
      double playerY = ClientAccess.zioMinecraft.player.getY();
      double fractionPlayerY = ClientAccess.zioMinecraft.player.getY();
      float supportHeight = (float)(playerY - Math.floor(fractionPlayerY));

      if (geometry.supportDirection()
         == Direction.DOWN) {
         Axis verticalAxis = Axis.Y;

         double targetY = target.position().getY();
         aimPosition = aimPosition.with(verticalAxis, targetY);
      } else {
         VoxelShape supportShape = geometry.supportShape();
         if (!(supportShape.isEmpty())) {
            VoxelShape heightShape = geometry.supportShape();
            double playerFraction = supportHeight;
            double shapeTop = heightShape.bounds().maxY;

            double aimOffsetY = Math.max(shapeTop, playerFraction) - 0.05D;
            aimPosition = aimPosition.add(0.0, aimOffsetY, 0.0);
         } else {
            double emptyShapeOffsetY = MathConstants.HALF;
            aimPosition = aimPosition.add(0.0, emptyShapeOffsetY, 0.0);
         }
      }

      if (placementTargetSnapshot.airPlace()) {
         aimPosition = placementTargetSnapshot.position().getCenter();
         geometry = new ScaffoldPlacementGeometry(geometry.supportDirection(), aimPosition, Shapes.empty());
      }

      InteractionHand hand = InteractionHand.MAIN_HAND;

      placementTargetSnapshot = target;

      BlockPos blockPosition = placementTargetSnapshot.position();

      Direction supportDirection = geometry.supportDirection();
      Boolean airPlaceEnabled = this.airPlaceSetting.getValue();
      BlockInteractionUtils.placeBlock(
         blockPosition, aimPosition, supportDirection, airPlaceEnabled, hand
      );

      if (RotationManager.isSilentRotationMode()) {
         float[] postRotations = this.computePostPlacementRotations(geometry, aimPosition);

         this.applyRotation(postRotations);
      }

      this.placementDelayTimer.reset();

      this.rotationTimeoutTimer.reset();

      this.targetTimeoutTimer.reset();
   }

   private boolean attackBlockingEntity(ScaffoldPlacementTarget target) {
      if (target == null) {
         return false;
      }
      Boolean attackEnabled = this.attackSetting.getValue();
      if (!attackEnabled) {
         return false;
      }
      ElapsedTimer timer = this.attackTimer;
      CrystalInteractionManager crystalManager = ClientContext.crystalInteractionManager;
      long attackDelayMillis = crystalManager.getAttackDelayMillis();
      if (!timer.hasElapsed(attackDelayMillis)) {
         return false;
      }

      BlockPos targetPosition = target.position();
      crystalManager = ClientContext.crystalInteractionManager;
      int entityCheckFlags = crystalManager.getMinimumCrystalAge();
      Entity blockingEntity = BlockInteractionUtils.attackCrystalAt(targetPosition, entityCheckFlags);
      if (blockingEntity != null) {
         float[] attackRotations = RotationManager.getRotationsToEntity(blockingEntity);

         this.applyRotation(attackRotations);
         timer = this.attackTimer;
         timer.reset();
         return true;
      }
      return false;
   }

   @EventHandler
   private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
      Setting<Boolean> safeWalkSetting = this.safeWalkSetting;
      if (safeWalkSetting.getValue()) {
         applySafeWalk(event);
      }
   }

   private static void applySafeWalk(LocalPlayerMoveEvent event) {
      if (!RotationManager.isMovementSyncEnabled()) return;

      LocalPlayer player = ClientAccess.zioMinecraft.player;
      if (!player.onGround()) return;

      if (ClientAccess.zioMinecraft.player.isHandsBusy()) return;

      AABB playerBounds = ClientAccess.zioMinecraft.player.getBoundingBox();
      double eventMovementX = event.getX();
      double movementZ = event.getZ();
      AABB movedBounds = playerBounds.move(eventMovementX, 0.0, movementZ);
      boolean wasSneaking = ClientAccess.zioMinecraft.player.isShiftKeyDown();
      ClientLevel level = ClientAccess.zioMinecraft.level;

      AABB expandedBounds = movedBounds.expandTowards(0.0, -1.0D, 0.0);
      if (level.noCollision(expandedBounds)) {
         if (!wasSneaking) {
            ClientAccess.zioMinecraft.player.setShiftKeyDown(true);
         }

         event.setHorizontalVelocity(0.0, 0.0);
      } else if (!wasSneaking) {
         ClientAccess.zioMinecraft.player.setShiftKeyDown(false);
      }
   }

   @EventHandler
   private void onConnectionSendPacketEvent(
      PacketEvent.Send event
   ) {
      if (event.isCancelled()) return;

      if (!(event.getPacket() instanceof ServerboundContainerClickPacket)) return;

      ElapsedTimer inventoryClickTimer = this.inventoryClickTimer;
      inventoryClickTimer.reset();
   }

   private float[] computePrePlacementRotations() {
      ScaffoldPlacementTarget target = this.placementTargets.getFirst();
      ScaffoldPlacementGeometry geometry = ScaffoldPlacementGeometry.fromTarget(target, true);
      Vec3 eyePosition = ClientAccess.zioMinecraft.player.getEyePosition();

      if (RotationManager.isMovementSyncEnabled()) {
         eyePosition = ClientContext.movementPredictionManager.getPredictedEyePosition();
      }

      Vec3 aimBasePosition = geometry.aimPosition();

      VoxelShape shape = geometry.supportShape();
      double aimHeight;
      if (shape.isEmpty()) {
         aimHeight = MathConstants.HALF;
      } else {
         aimHeight = (geometry.supportShape().bounds().maxY) - 0.01D;
      }

      Vec3 aimPosition = aimBasePosition.add(0.0, aimHeight, 0.0);

      float[] rotations = RotationManager.calculateRotations(eyePosition, aimPosition);

      if (MovementPhysics.isLocalPlayerMoving()) {
         if (!RotationManager.isMovementSyncEnabled()) {
            float playerYawSnapshot = ClientAccess.zioMinecraft.player.getYRot();
            ClientInput movementInput = ClientAccess.zioMinecraft.player.input;

            double[] movementDirection = MovementPhysics.getHorizontalMotion(
               playerYawSnapshot, movementInput, 1.0D
            );
            double directionZ = movementDirection[1];
            double movementDirectionX = movementDirection[0];
            double movementRadians = Math.atan2(directionZ, movementDirectionX);

            float movementYaw = (float)(Math.toDegrees(movementRadians) - 270.0D);
            float placementYaw = rotations[0];

            if (Mth.degreesDifferenceAbs(movementYaw, placementYaw) < 6e+01F) {
               rotations[0] = movementYaw;
            }
         }
      }

      if (RotationManager.isMovementSyncEnabled()) {
         if (!MovementPhysics.isLocalPlayerMoving()) {
            float stationaryYawBase = rotations[0];
            ThreadLocalRandom random = ThreadLocalRandom.current();

            rotations[0] = stationaryYawBase
               + random.nextInt(8) * AntiCheatModule.ROTATION_JITTER_STEP;
         }
      }

      this.rotationTimeoutTimer.reset();
      return rotations;
   }

   private float[] computePostPlacementRotations(ScaffoldPlacementGeometry geometry, Vec3 postAimPosition) {
      VoxelShape postShape = geometry.supportShape();
      double postAimHeight;
      if (postShape.isEmpty()) {
         postAimHeight = MathConstants.HALF;
      } else {
         postAimHeight = (geometry.supportShape().bounds().maxY) - 0.01D;
      }

      postAimPosition = postAimPosition.add(0.0, postAimHeight, 0.0);

      return RotationManager.getRotationsToPosition(postAimPosition);
   }

   double findSupportHeight(float sampleY) {
      LocalPlayer boundsPlayer = ClientAccess.zioMinecraft.player;
      AABB bounds = boundsPlayer.getBoundingBox();
      LocalPlayer groundPlayer = ClientAccess.zioMinecraft.player;
      boolean onGround = groundPlayer.onGround();
      LocalPlayer passengerCheckPlayer = ClientAccess.zioMinecraft.player;
      if (passengerCheckPlayer.isPassenger()) {
         LocalPlayer boundsVehiclePlayer = ClientAccess.zioMinecraft.player;
         Entity boundsVehicle = boundsVehiclePlayer.getVehicle();
         bounds = boundsVehicle.getBoundingBox();
         LocalPlayer groundVehiclePlayer = ClientAccess.zioMinecraft.player;
         Entity groundVehicle = groundVehiclePlayer.getVehicle();
         onGround = groundVehicle.onGround();
      }

      double minimumX = bounds.minX;
      double minimumZ = bounds.minZ;
      double maximumX = bounds.maxX;
      double maximumZ = bounds.maxZ;
      List<Vec3> samplePoints = new ArrayList<>();
      Vec3 bottomCenter = BoundingBoxUtils.getBottomCenter(bounds);
      double sampleHeight = sampleY;
      Axis sampleAxis = Axis.Y;
      samplePoints.add(bottomCenter.with(sampleAxis, sampleHeight));
      if (onGround) {
         bottomCenter = new Vec3(minimumX, sampleY, minimumZ);
         Vec3 maximumXCorner = new Vec3(maximumX, sampleY, minimumZ);
         Vec3 maximumZCorner = new Vec3(minimumX, sampleY, maximumZ);
         Vec3 maximumCorner = new Vec3(maximumX, sampleY, maximumZ);
         samplePoints.addAll(List.of(bottomCenter, maximumXCorner, maximumZCorner, maximumCorner));
      }

      double supportHeight = -999.0D;
      for (Vec3 sample : samplePoints) {
         BlockRaycastContext.Builder builder = new BlockRaycastContext.Builder(sample, sample.add(0.0, -1.0D, 0.0));
         LocalPlayer raycastPassengerPlayer = ClientAccess.zioMinecraft.player;
         Entity raycastEntity;
         if (raycastPassengerPlayer.isPassenger()) {
            LocalPlayer raycastVehiclePlayer = ClientAccess.zioMinecraft.player;
            raycastEntity = raycastVehiclePlayer.getVehicle();
         } else {
            raycastEntity = ClientAccess.zioMinecraft.player;
         }

         BlockRaycastContext context = builder.withEntity(raycastEntity).build();
         BlockHitResult hit = RaycastUtils.raycast(context);
         double sampleHeightResult;
         if (hit.getType() == Type.MISS) {
            double sampleOriginY = sample.y();

            sampleHeightResult = sampleOriginY - 1.0D;
         } else {
            Vec3 hitPosition = hit.getLocation();
            double hitY = hitPosition.y();

            sampleHeightResult = hitY - 0.009999999776482582D;
         }

         double previousSupportHeight = supportHeight;
         supportHeight = Math.max(previousSupportHeight, sampleHeightResult);
      }

      return supportHeight;
   }

   private void setJumpStrength(float strength) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      Holder jumpAttribute = Attributes.JUMP_STRENGTH;
      AttributeInstance attribute = player.getAttribute(jumpAttribute);
      double baseValue = strength;
      attribute.setBaseValue(baseValue);
   }

   @EventHandler
   private void onPlayerIsStayingOnGroundSurfaceEvent(PlayerIsStayingOnGroundSurfaceEvent event) {
      if (this.safeWalkSetting.getValue()) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         if (!player.isHandsBusy()) {
            event.cancel();
         }
      }
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      Boolean rotateEnabled = this.rotateSetting.getValue();
      if (!rotateEnabled) {
         if (event.getStage().isPre()) {
            this.tickPlacement();
         }
      }
   }

   private int scoreBlockStack(ItemStack stack) {
      if (!(stack.getItem() instanceof BlockItem blockItem)) return 0;

      Block block = blockItem.getBlock();
      if (!((BlockBehaviourAccessor)block).isCollidable()) {
         return 0;
      }

      LocalPlayer player = ClientAccess.zioMinecraft.player;
      if (!player.isShiftKeyDown() && block == Blocks.POWDER_SNOW) {
         return 0;
      }

      if (!this.canUseFallingBlock(blockItem)) {
         return 0;
      }

      ListFilterMode filterMode = this.selectionSetting.getValue();
      Collection selectedBlocks = this.blocksSetting.getValue();
      if (!ListFilterMode.matches(filterMode, block, selectedBlocks)) return 0;

      BlockState blockState = block.defaultBlockState();
      ClientLevel blockLevel = ClientAccess.zioMinecraft.level;
      BlockPos shapePosition = BlockPos.ZERO;
      VoxelShape collisionShape = blockState.getCollisionShape(blockLevel, shapePosition);
      int score;
      if (BoundingBoxUtils.hasUnitDimensions(collisionShape)) {
         score = 2;
      } else {
         score = 1;
      }

      return score;

   }

   private void selectBlockSlot() {
      ItemStack stack = ClientAccess.zioMinecraft.player.getMainHandItem();

      if (this.scoreBlockStack(stack) > 0) {
         this.blockSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
         return;
      }

      int bestSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
      stack = ClientAccess.zioMinecraft.player.getInventory().getItem(bestSlot);

      int bestScore = this.scoreBlockStack(stack);
      for (int hotbarIndex = 0;; hotbarIndex++) {
         if (hotbarIndex >= (9)) {
            if (bestScore == 0) {
               this.blockSlot = -1;
            } else {
               this.blockSlot = bestSlot;
            }

            return;
         }

         stack = ClientAccess.zioMinecraft.player.getInventory().getItem(hotbarIndex);

         int candidateScore = this.scoreBlockStack(stack);
         if (candidateScore > bestScore) {
            bestSlot = hotbarIndex;
            bestScore = candidateScore;
         }

      }
   }

   private void updateTower() {
      boolean fastTower = this.towerSetting.getValue() == ScaffoldModule.TowerChoice.FAST;
      double playerY = ClientAccess.zioMinecraft.player.getY();
      int blockY = (int)Math.floor(playerY);
      if (this.lastTowerBlockY == blockY && !RotationManager.isMovementSyncEnabled()) {
         return;
      }

      ElapsedTimer setbackTimer = ClientContext.serverStateManager.getSetbackTimer();

      if (!setbackTimer.hasElapsed(200L) && fastTower) return;

      this.lastTowerBlockY = blockY;
      if (this.towerSetting.getValue() == ScaffoldModule.TowerChoice.NONE) return;

      Input input = ClientAccess.zioMinecraft.player.input.keyPresses;
      if (!input.jump()
         || ClientAccess.zioMinecraft.player.zza != 0.0F
         || ClientAccess.zioMinecraft.player.xxa != 0.0F) return;

      if (this.towerSetting.getValue() == ScaffoldModule.TowerChoice.GRIM) {
         if (ClientAccess.zioMinecraft.player.onGround()) {
            this.savedJumpStrength = this.getJumpStrength();

            float grimJumpStrength = 0.36813F;

            this.setJumpStrength(grimJumpStrength);
         }
      } else {
         if (fastTower) {
            TickRateController.PlayerTicks playerTicks = ClientContext.playerTickController;

            float towerMultiplier = 2.0F;
            playerTicks.requestMultiplier(this, towerMultiplier);
         }

         ClientAccess.zioMinecraft.player.setDeltaMovement(0.0, 0.41999998688698D, 0.0);
      }
   }

   private void updatePlacementRotation() {
      int rotationTimeout = 500;
      if (RotationManager.isMovementSyncEnabled()) {
         if (antiCheatModule.is2b2tEnabled()) {
            rotationTimeout = 200;
         } else {
            rotationTimeout = -1;
         }
      }

      if (!RotationManager.isSilentRotationMode()
         && !this.placementTargets.isEmpty()) {
         float[] rotations = this.computePrePlacementRotations();

         this.applyRotation(rotations);
      }

      if (this.cachedRotations != null) {
         RotationManager rotationManager = ClientContext.rotationManager;
         float[] cachedRotation = this.cachedRotations;

         rotationManager.requestRotation(cachedRotation, 150);
      }

      long rotationTimeoutMillis = rotationTimeout;
      if (this.rotationTimeoutTimer.hasElapsed(rotationTimeoutMillis)) {
         this.cachedRotations = null;
      }
   }

   @EventHandler
   private void onRotationEvent(RotationRequestEvent event) {
      if (this.rotateSetting.getValue()) {
         this.tickPlacement();
      }
   }

   boolean canPlaceAt(BlockPos position, int slot) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      Inventory inventory = player.getInventory();
      int placementSlot;
      if (slot == -1) {
         LocalPlayer selectionPlayer = ClientAccess.zioMinecraft.player;
         Inventory selectedInventory = selectionPlayer.getInventory();
         placementSlot = selectedInventory.getSelectedSlot();
      } else {
         placementSlot = slot;
      }

      ItemStack stack = inventory.getItem(placementSlot);
      if (stack.getItem() instanceof BlockItem blockItem) {
         Block collisionBlock = blockItem.getBlock();
         if (!((BlockBehaviourAccessor)collisionBlock).isCollidable()) {
            Block survivalBlock = blockItem.getBlock();
            BlockState placementState = survivalBlock.defaultBlockState();
            ClientLevel placementLevel = ClientAccess.zioMinecraft.level;
            if (!placementState.canSurvive(placementLevel, position)) {
               return false;
            }
         }

         Block block = blockItem.getBlock();
         Boolean attackEnabled = this.attackSetting.getValue();
         return BlockInteractionUtils.canPlaceBlock(position, block, true, attackEnabled);
      } else {
         Boolean fallbackAttackEnabled = this.attackSetting.getValue();
         return BlockInteractionUtils.canPlaceAt(position, true, fallbackAttackEnabled);
      }
   }

   boolean isSelectedBlockNonCollidable() {
      if (this.blockSlot == -1) {
         return false;
      }

      LocalPlayer player = ClientAccess.zioMinecraft.player;
      Inventory inventory = player.getInventory();
      int slot = this.blockSlot;
      ItemStack stack = inventory.getItem(slot);
      if (stack.getItem() instanceof BlockItem blockItem) {
         Block block = blockItem.getBlock();
         return !((BlockBehaviourAccessor)block).isCollidable();
      }
      return false;
   }

   private boolean canUseFallingBlock(BlockItem blockItem) {
      int groundDistance = RaycastUtils.getGroundDistance();
      return !(blockItem.getBlock() instanceof FallingBlock) || groundDistance <= 1;
   }

   private void applyRotation(float[] rotations) {
      if (this.rotateSetting.getValue()) {
         if (RotationManager.isMovementSyncEnabled()) {
            rotations = RotationManager.addYawJitter(rotations);
         }

         if (RotationManager.isSilentRotationMode()) {
            RotationManager rotationManager = ClientContext.rotationManager;

            rotationManager.setRenderRotations(rotations, 10);
            rotationManager = ClientContext.rotationManager;

            rotationManager.requestRotation(rotations, 150);
         } else {
            this.cachedRotations = rotations;
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry moduleRegistry) {
      antiCheatModule = (AntiCheatModule)moduleRegistry.getModule(
         AntiCheatModule.class
      );
   }

   public ScaffoldModule() {
      super("Scaffold", "Speedbridges for you automatically.", ModuleCategory.PLAYER, new String[0]);
      this.blocksSetting = this.add(new RegistrySetSetting<>("Blocks", BuiltInRegistries.BLOCK, new Block[0]));
      this.selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.ANY));
      this.swapSetting = this.add(new EnumSetting<>("Swap", ScaffoldSwapChoice.NONE));
      this.modeSetting = this.add(new EnumSetting<>("Mode", ScaffoldMode.SINGLE));
      this.sizeSetting = this.add(new NumberSetting<>("Size", 1, 1, 4, ignored -> this.modeSetting.getValue() == ScaffoldMode.MULTI));
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 9, ignored -> this.modeSetting.getValue() == ScaffoldMode.MULTI));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 180, 0, 300).withUnit("ms"));
      this.offsetSetting = this.add(new NumberSetting<>("Offset", 0.0F, 0.0F, 3.0F, ignored -> this.modeSetting.getValue() == ScaffoldMode.SINGLE));
      this.towerSetting = this.add(new EnumSetting<>("Tower", ScaffoldModule.TowerChoice.NONE));
      this.safeWalkSetting = this.add(new BooleanSetting("SafeWalk", true));
      this.airPlaceSetting = this.add(new BooleanSetting("AirPlace", false));
      this.tellySetting = this.add(new BooleanSetting("Telly", false));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
      this.attackSetting = this.add(new BooleanSetting("Attack", true));
      this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.5F, 5.0F, ignored -> this.isRenderVisible()));
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(146643455, true), ignored -> this.isRenderVisible()));
      this.outlineSetting = this.add(new ColorSetting("Outline", new Color(-4351489, true), ignored -> this.isRenderVisible()));
      this.fadeSetting = this.add(new BooleanSetting("Fade", true, ignored -> this.isRenderVisible()));
      this.fadeTimeSetting = this.add(new NumberSetting<>("FadeTime", 1.0F, 0.1F, 1.0F, ignored -> this.isRenderVisible() && this.fadeSetting.getValue()).withUnit("s"));
      this.targetTimeoutTimer = new ElapsedTimer();
      this.rotationTimeoutTimer = new ElapsedTimer();
      this.placementDelayTimer = new ElapsedTimer();
      this.inventoryClickTimer = new ElapsedTimer();
      this.attackTimer = new ElapsedTimer();
      this.placementTargets = new ArrayList<>();
      this.blockSlot = -1;

      this.savedJumpStrength = -1.0F;
      FeatureBoxRenderer renderer = ClientContext.featureBoxRenderer;
      FeatureBoxRenderer.RenderConfiguration renderConfiguration = new FeatureBoxRenderer.RenderConfiguration(
         this, this.fillSetting, this.outlineSetting, this.lineWidthSetting, this.fadeTimeSetting,
         () -> false, this.fadeSetting, getRenderFadeTimeScale()
      );
      renderer.register(renderConfiguration);
   }

   private boolean isRenderVisible() {
      Setting<Boolean> renderSetting = this.renderSetting;
      return renderSetting.isExpanded();
   }

   private static int getRenderFadeTimeScale() {
      return 450;
   }

   public enum TowerChoice implements NamedEntry {
      NONE("None"),
      PLAIN("Plain"),
      FAST("Fast"),
      GRIM("GrimV3");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      TowerChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
