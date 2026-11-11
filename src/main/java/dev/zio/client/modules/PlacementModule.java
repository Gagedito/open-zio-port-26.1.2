/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules;

import dev.zio.client.events.EventStage;
import dev.zio.client.managers.CrystalInteractionManager;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.TaskScheduler;
import dev.zio.client.render.FeatureBoxRenderer;
import dev.zio.client.utils.LockUtils;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.utils.combat.BlockRaycastClassifier;
import dev.zio.client.utils.combat.BlockRaycastContext;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.combat.RaycastUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.world.WorldUtils;


import dev.zio.client.events.StepHeightEvent;
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.AbstractClientPlayerTickEvent;
import dev.zio.client.events.ItemUseEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.combat.OffhandModule;
import dev.zio.client.modules.exploit.ChorusControlModule;
import dev.zio.client.modules.exploit.MultiTaskModule;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.AntiCheatRotationsChoice;
import dev.zio.client.settings.options.BlockSampleMode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public abstract class PlacementModule extends Module {
   private static AntiCheatModule antiCheatModule;
   private static ChorusControlModule chorusControlModule;
   protected static MultiTaskModule multiTaskModule;
   protected final Setting<Integer> placementDelaySetting;
   protected final Setting<Integer> placementBptSetting;
   protected final Setting<Boolean> placementAirPlaceSetting;
   protected final Setting<Boolean> placementRaytraceSetting;
   protected final Setting<Boolean> placementRotateSetting;
   protected final Setting<Boolean> placementAttackSetting;
   protected final Setting<Boolean> placementAutoDisableSetting;
   protected final Setting<Boolean> placementJumpSetting;
   public final Setting<Boolean> placementChorusSetting;
   public final Setting<Boolean> placementPearlSetting;
   public final Setting<Boolean> placementRubberBandSetting;
   public final Setting<Boolean> placementHeightSetting;
   protected final Setting<Boolean> placementRenderSetting;
   protected final Setting<Color> placementFillSetting;
   protected final Setting<Color> placementOutlineSetting;
   protected final Setting<Float> placementLineWidthSetting;
   protected final Setting<Boolean> placementFadeSetting;
   protected final Setting<Float> placementFadeTimeSetting;
   private final ElapsedTimer placementTimer;
   private final ElapsedTimer attackTimer;
   private Vec3 lastPlacementHitPosition;
   private Direction placementDirection;
   private Entity pendingAttackEntity;
   private int placementsThisCycle;
   private int missingBlockAttempts;
   public double placementBaselineY;
   private boolean disableOnRespawn;
   private boolean ignoreMissingBlocks;
   private boolean inventoryPrepared;
   private boolean batchCooldownActive;
   private boolean placedThisCycle;
   private boolean skipNextCycle;
   private volatile boolean tickPlacementActive;

   protected boolean disableForHeightChange() {
      if (!this.placementHeightSetting.getValue()) {
         return false;
      }

      if (ClientAccess.zioMinecraft.player.onGround()) {
         return false;
      }

      double trackedHeight = ClientContext.playerStateTracker.getLastGroundedY();
      LocalPlayer playerForHeight = ClientAccess.zioMinecraft.player;
      double heightDifference = trackedHeight - playerForHeight.getY();
      double absoluteHeightDifference = Math.abs(heightDifference);
      if (absoluteHeightDifference >= 2.0D) {
         this.setToggled(false);
         return true;
      }
      return false;
   }

   protected BlockPos getPlacementOrigin() {
      return PlayerStateUtils.getLocalRoundedBlockPosition();
   }

   protected boolean disableForJumpOrSleep() {
      double heightSinceBaseline = ClientAccess.zioMinecraft.player.getY() - this.placementBaselineY;

      if (ClientAccess.zioMinecraft.player.onGround()) {
         LocalPlayer playerForBaseline = ClientAccess.zioMinecraft.player;
         this.placementBaselineY = playerForBaseline.getY();
      }

      double currentY = ClientAccess.zioMinecraft.player.getY();
      double baselineY = this.placementBaselineY;
      boolean jumpOrSleepingResult = currentY > baselineY + 0.1D && heightSinceBaseline >= 0.4D;
      if (!jumpOrSleepingResult) {
         jumpOrSleepingResult = ClientAccess.zioMinecraft.player.isSleeping();
      }

      if (this.placementJumpSetting.getValue() && jumpOrSleepingResult) {
         this.setToggled(false);
         return true;
      }
      return false;
   }

   protected boolean shouldDiscardAttackedCrystal() {
      return false;
   }

   protected boolean usesBatchRotations() {
      Setting<Boolean> movementSyncSettingRef = antiCheatModule.movementSyncSetting;

      if (movementSyncSettingRef.getValue()) {
         Setting<AntiCheatRotationsChoice> rotationModeSettingRef = antiCheatModule.rotationsSetting;
         if (rotationModeSettingRef.getValue() == AntiCheatRotationsChoice.SILENT) {
            return false;
         }
      }

      if (!this.placementRotateSetting.getValue()) {
         return false;
      }

      return this.placementBptSetting.getValue() > 1;
   }

   protected int getPlacementRotationPriority() {
      return 20;
   }

   protected InventoryUtils.SwapSlot findPlacementSwapSlot() {
      Item obsidianSnapshot = Items.OBSIDIAN;

      InventoryUtils.SwapSlot obsidianSlot = InventoryUtils.findSwapSlot(obsidianSnapshot, false);
      if (obsidianSlot.isFound()) {
         return obsidianSlot;
      }

      Predicate<ItemStack> blockPredicate = stack -> {
         if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
         }

         if (blockItem.getBlock() == Blocks.ANVIL) {
            return false;
         }
         Block block = blockItem.getBlock();
         float explosionResistance = block.getExplosionResistance();

         return explosionResistance >= 600.0F;
      };

      return InventoryUtils.findSwapSlot(blockPredicate, false);
   }

   public PlacementModule(String name, String description, ModuleCategory category, String... aliases) {
      super(name, description, category, aliases);
      NumberSetting<Integer> delay = new NumberSetting<>("Delay", 50, 0, 250);
      NumberSetting<Integer> delayWithUnit = delay.withUnit("ms");
      this.placementDelaySetting = this.add(delayWithUnit);
      this.placementBptSetting = this.add(new NumberSetting<>(
         "BPT", 8, 1, 9
      ));
      this.placementAirPlaceSetting = this.add(new BooleanSetting("AirPlace", false));
      this.placementRaytraceSetting = this.add(new BooleanSetting("Raytrace", false));
      this.placementRotateSetting = this.add(new BooleanSetting("Rotate", false));
      this.placementAttackSetting = this.add(new BooleanSetting("Attack", true));
      this.placementAutoDisableSetting = this.add(new BooleanSetting("AutoDisable", false).markAsGroup());
      BooleanSetting jump = new BooleanSetting("Jump", true);
      Setting<?>[] autoDisableDependency = new Setting<?>[]{this.placementAutoDisableSetting};
      this.placementJumpSetting = this.add(jump.dependsOn(autoDisableDependency));
      this.placementChorusSetting = this.add(new BooleanSetting("Chorus", false).dependsOn(this.placementAutoDisableSetting));
      this.placementPearlSetting = this.add(new BooleanSetting("Pearl", false).dependsOn(this.placementAutoDisableSetting));
      this.placementRubberBandSetting = this.add(new BooleanSetting("RubberBand", false).dependsOn(this.placementAutoDisableSetting));
      this.placementHeightSetting = this.add(new BooleanSetting("Height", false).dependsOn(this.placementAutoDisableSetting));
      this.placementRenderSetting = this.add(new BooleanSetting("Render", false).markVisibilityDependency());
      this.placementFillSetting = this.add(new ColorSetting("Fill", new Color(
         189, 153, 255, 8
      ), unused -> {
         Setting<Boolean> render = this.placementRenderSetting;

         return render.isExpanded();
      }));
      this.placementOutlineSetting = this.add(new ColorSetting("Outline", new Color(
         189, 153, 255, 91
      ), unused -> {
         Setting<Boolean> render = this.placementRenderSetting;

         return render.isExpanded();
      }));
      this.placementLineWidthSetting = this.add(new NumberSetting<>(
         "LineWidth", 1.0F, 0.1F, 3.0F
      ).dependsOn(this.placementRenderSetting));
      this.placementFadeSetting = this.add(new BooleanSetting("Fade", true).dependsOn(this.placementRenderSetting));
      NumberSetting<Float> fadeTime = new NumberSetting<>("FadeTime", 1.0F, 0.1F, 1.0F);
      fadeTime = fadeTime.withUnit("s");
      Setting<?>[] fadeDependencies = new Setting<?>[2];
      fadeDependencies[0] = this.placementRenderSetting;
      fadeDependencies[1] = this.placementFadeSetting;
      this.placementFadeTimeSetting = this.add(fadeTime.dependsOn(fadeDependencies));
      this.placementTimer = new ElapsedTimer();
      this.attackTimer = new ElapsedTimer();
      FeatureBoxRenderer renderer = ClientContext.featureBoxRenderer;
      FeatureBoxRenderer.RenderConfiguration renderConfiguration = new FeatureBoxRenderer.RenderConfiguration(
         this, this.placementFillSetting, this.placementOutlineSetting, this.placementLineWidthSetting, this.placementFadeTimeSetting,
         () -> true, this.placementFadeSetting, 450
      );

      renderer.register(renderConfiguration);
   }

   public PlacementModule(String name, ModuleCategory category, String... aliases) {
      this(name, "", category, aliases);
   }

   static {
      antiCheatModule = (AntiCheatModule)ClientContext.getModule(AntiCheatModule.class);
      chorusControlModule = (ChorusControlModule)ClientContext.getModule(ChorusControlModule.class);
      multiTaskModule = (MultiTaskModule)ClientContext.getModule(MultiTaskModule.class);
   }

   @Override
   public <S extends Setting<?>> S add(S setting) {
      if (!this.getSettings().contains(this.placementFadeTimeSetting)) {
         return super.add(setting);
      }
      List<Setting<?>> settingsRegistry = this.getSettings();
      settingsRegistry.add(this.getSettings().indexOf(this.placementAutoDisableSetting), setting);
      return setting;
   }

   @Override
   public void onEnable() {
      if (!this.isWorldUnavailable()) {
         LocalPlayer playerForHeight = ClientAccess.zioMinecraft.player;

         this.placementBaselineY = playerForHeight.getY();
      }

      this.placementsThisCycle = 0;
      this.missingBlockAttempts = 0;
      this.skipNextCycle = false;
   }

   private int getPlacementBatchLimit() {
      if (!this.tickPlacementActive) {
         return 1;
      }

      if (this.usesBatchRotations()
         && this.batchCooldownActive) {
         return 1;
      }

      return this.placementBptSetting.getValue();
   }

   protected void setDisableOnRespawn(boolean enabled) {
      this.disableOnRespawn = enabled;
   }

   protected void deduplicatePlacementPositions(List positions) {
      if (positions != null && positions.size() > 1) {
         HashSet uniquePositions = new HashSet(positions);
         positions.clear();
         positions.addAll(uniquePositions);
      }
   }

   protected void runPlacementCycle() {
      this.placedThisCycle = false;
      if (this.skipNextCycle) {
         this.skipNextCycle = false;
         return;
      }
      if (OffhandModule.isRecentSwapWindowActive() || SpeedMineModule.isSwapPending()) {
         return;
      }

      if (ClientAccess.zioMinecraft.player.isSpectator()) {
         return;
      }

      if (!ClientAccess.zioMinecraft.player.isAlive()) {
         return;
      }

      if (ClientAccess.zioMinecraft.player.isHandsBusy()) {
         return;
      }

      if (ClientAccess.zioMinecraft.player.isSleeping()) {
         return;
      }

      if (multiTaskModule.shouldPauseModule(this)) {
         return;
      }
      ElapsedTimer placementTimerSnapshot = this.placementTimer;

      long delayMillis = (this.placementDelaySetting.getValue()).intValue();
      if (!placementTimerSnapshot.hasElapsed(delayMillis)) {
         return;
      }
      List candidates = this.getPlacementPositions();
      InventoryUtils.SwapSlot blockSlot = this.findPlacementSwapSlot();
      this.filterPlacementPositions(blockSlot, candidates);
      this.sortPlacementPositions(candidates);
      Runnable placementTask = () -> this.placePositions(candidates);
      LockUtils.runWithSharedLock(placementTask);
      this.placementsThisCycle = 0;
   }

   protected boolean shouldSendBatchRotation() {
      if (!this.tickPlacementActive || !this.usesBatchRotations()) {
         return false;
      }

      if (!ClientAccess.zioMinecraft.player.onGround()) {
         return false;
      }

      return ClientContext.playerStateTracker.getGroundedTicks() > 1 && !this.batchCooldownActive;
   }

   @EventHandler
   private void onItemStackFinishUsingItemEvent(
      ItemUseEvent.BeforeFinish event
   ) {
      ItemStack finishedStack = event.getStack();

      if (finishedStack.getItem() != Items.CHORUS_FRUIT) {
         return;
      }

      if (ClientAccess.zioMinecraft.player.isShiftKeyDown()) {
         return;
      }

      if (chorusControlModule.isToggled()) {
         return;
      }

      if (this.placementChorusSetting.getValue()) {
         this.disable();
      } else {
         this.skipNextCycle = true;
      }
   }

   @EventHandler
   private void onInboundPacket(
      PacketEvent.Receive event
   ) {
      if (this.placementAttackSetting.getValue() && event.getPacket() instanceof ClientboundAddEntityPacket spawnPacket) {
         if (spawnPacket.getType() == EntityType.END_CRYSTAL && this.shouldHandleCrystalSpawn()) {
            if (OffhandModule.isRecentSwapWindowActive()) {
               return;
            }

            if (multiTaskModule.shouldPauseModule(this)) {
               return;
            }

            if (RotationManager.isMovementSyncEnabled()) {
               return;
            }

            Vec3 spawnPositionSnapshot = new Vec3(spawnPacket.getX(), spawnPacket.getY(), spawnPacket.getZ());

            AABB spawnBounds = AABB.ofSize(spawnPositionSnapshot, 2.0D, 2.0D, 2.0D);
            AABB crystalBounds = spawnBounds.move(0.0D, 1.0D, 0.0D);
            List candidates = this.getPlacementPositions();
            this.filterPlacementPositions(this.findPlacementSwapSlot(), candidates);
            Iterator candidateIterator = candidates.iterator();

            while (candidateIterator.hasNext()) {
               BlockPos candidatePos = (BlockPos)candidateIterator.next();

               if (BoundingBoxUtils.intersectsBlock(crystalBounds, candidatePos)) {
                  CrystalInteractionManager attackManagerSnapshot = ClientContext.crystalInteractionManager;
                  int packetEntityId = spawnPacket.getId();

                  if (!attackManagerSnapshot.attackEntity(packetEntityId, true)) {
                     return;
                  }
                  break;
               }
            }

            Runnable placementTask = () -> this.placePositions(candidates);

            LockUtils.runWithSharedLock(placementTask);
         }
      }

      if (event.getPacket() instanceof ClientboundPlayerPositionPacket correctionPacket) {
         PositionMoveRotation positionChange = correctionPacket.change();
         Vec3 serverPosition = positionChange.position();
         this.placementBaselineY = serverPosition.y();

         Vec3 playerPositionSnapshot = ClientAccess.zioMinecraft.player.position();
         PositionMoveRotation distanceChange = correctionPacket.change();
         Vec3 correctedPosition = distanceChange.position();
         double correctionDistanceSquared = playerPositionSnapshot.distanceToSqr(correctedPosition);

         if (correctionDistanceSquared >= 1.0D) {
            if (this.placementRubberBandSetting.getValue()) {
               this.disable();
               return;
            }
         }
      }

      if (event.getPacket() instanceof ClientboundRespawnPacket
         && this.disableOnRespawn) {
         this.disable();
      }

      if (event.getPacket() instanceof ClientboundOpenScreenPacket openScreenPacket) {
         if (!this.placementTimer.hasElapsed(100L)) {
            PacketSender.send(new ServerboundContainerClosePacket(openScreenPacket.getContainerId()));
            event.cancel();
         }
      }
   }

   protected boolean sendBatchRotation(
      BlockPos currentPos, BlockPos nextPos, PlacementModule.PlacementAngles currentAngles, PlacementModule.PlacementAngles previousAngles
   ) {
      if (previousAngles != null) {
         if (previousAngles.intersectsBlock(currentPos)) {
            return false;
         }
      }

      if (currentPos.equals(nextPos)) {
         PlacementModule.PlacementAngles playerAngles = new PlacementModule.PlacementAngles(
            ClientAccess.zioMinecraft.player.getYRot(), ClientAccess.zioMinecraft.player.getXRot()
         );
         if (playerAngles.intersectsBlock(currentPos)) {
            return false;
         }
      }

      double playerX = ClientAccess.zioMinecraft.player.getX();

      double playerY = ClientAccess.zioMinecraft.player.getY();

      double playerZ = ClientAccess.zioMinecraft.player.getZ();
      float angleYaw = currentAngles.yaw();
      float anglePitch = currentAngles.pitch();

      boolean onGround = ClientAccess.zioMinecraft.player.onGround();
      PacketSender.sendPositionRotation(
         playerX, playerY, playerZ, angleYaw, anglePitch, onGround
      );
      return true;
   }


   private boolean tryPlaceAtHit(BlockPos blockPos, Vec3 hitPosition, ItemStack stack) {
      Direction directionSnapshot = this.placementDirection;

      boolean airPlaceFlag = this.placementAirPlaceSetting.getValue();
      InteractionHand hand = InteractionHand.MAIN_HAND;

      return BlockInteractionUtils.placeBlockIgnoringCrystals(blockPos, hitPosition, directionSnapshot, airPlaceFlag, hand, stack);
   }

   protected void onPlacementAttempt(BlockPos blockPos, boolean placementFailed) {
   }

   protected void sortPlacementPositions(List positions) {
      if (this.getPlacementBatchLimit() > 1) {
         positions.sort(Comparator.comparing((Function<BlockPos, Integer>)sortPos -> {
                  boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();

                  int sortKey = BlockInteractionUtils.findSupportDirection(sortPos, strictDirection)
                        != null
                     ? -1
                     : 1;
                  return sortKey;
               }));
      }

      if (!this.tickPlacementActive) {
         positions.sort(Comparator.comparing((Function<BlockPos, Float>)sortPos -> {
                  float targetYaw = RotationManager.getRotationsToBlock(
                     sortPos
                  )[0];

                  float packetYaw = ClientContext.playerStateTracker.getPacketRotations()[0];
                  float yawDifference = Mth.degreesDifferenceAbs(targetYaw, packetYaw);
                  return yawDifference;
               }));
      }
   }

   @EventHandler(priority = 100)
   private void onPlacementTick(RotationRequestEvent event) {
      this.lastPlacementHitPosition = null;
      this.pendingAttackEntity = null;
      this.tickPlacementActive = true;
      this.runPlacementCycle();
      this.tickPlacementActive = false;
   }

   @EventHandler
   private void onAbstractClientPlayerTickEvent(AbstractClientPlayerTickEvent event) {
      this.disableForJumpOrSleep();

      this.disableForHeightChange();
   }

   @EventHandler
   private void onStepHeight(StepHeightEvent event) {
      if (event.getStage() == EventStage.Post && event.getHeight() > MathConstants.ONE_HALF) {
         if (this.placementJumpSetting.getValue()) {
            this.disable();
         }
      }
   }

   protected void filterPlacementPositions(InventoryUtils.SwapSlot blockSlot, List candidates) {
      this.deduplicatePlacementPositions(candidates);
      ArrayList rejectedPositions = new ArrayList();
      Block placementBlock = null;
      if (blockSlot.isFound()) {
         ItemStack placementStack = blockSlot.getStack();

         if (placementStack.getItem() instanceof BlockItem blockItem) {
            placementBlock = blockItem.getBlock();
         }
      }

      Iterator candidateIterator = candidates.iterator();

      while (candidateIterator.hasNext()) {
         BlockPos candidatePos = (BlockPos)candidateIterator.next();

         Vec3 eyeSnapshot = ClientAccess.zioMinecraft.player.getEyePosition();

         Vec3 blockCenter = candidatePos.getCenter();
         double squaredDistance = eyeSnapshot.distanceToSqr(blockCenter);

         boolean allowAttack = this.placementAttackSetting.getValue();

         if (BlockInteractionUtils.canPlaceBlock(candidatePos, placementBlock, true, allowAttack) && squaredDistance <= 36.0D) {
            continue;
         }

         rejectedPositions.add(candidatePos);
      }

      candidateIterator = rejectedPositions.iterator();

      while (candidateIterator.hasNext()) {
         candidates.remove(((BlockPos)candidateIterator.next()));
      }
   }

   protected void placePositions(List positions) {
      if (positions.isEmpty()) {
         return;
      }

      this.attackCrystalsAtPositions(positions);
      int previousSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
      InventoryUtils.SwapSlot blockSlot = this.findPlacementSwapSlot();
      PlacementModule.PlacementAngles previousAngles = null;
      boolean retryPlacement = true;
      HashSet placedPositions = new HashSet();

      while (retryPlacement) {
         retryPlacement = false;
         Iterator positionIterator = positions.iterator();

         while (positionIterator.hasNext()) {
            BlockPos blockPos = (BlockPos)positionIterator.next();

            if (ClientContext.swingManager.isCrystalPlacementLimited()) {
               break;
            }

            if (!this.checkPlacementSwapSlot(blockSlot)) {
               break;
            }

            if (this.placementsThisCycle >= this.getPlacementBatchLimit()) {
               break;
            }

            if (placedPositions.contains(blockPos)) {
               continue;
            }

            PlacementModule.PlacementResult placementResult = this.placeBlock(
               blockSlot, blockPos
            );
            if (!placementResult.success()) {
               continue;
            }
            ClientContext.swingManager.tryTrackCrystalPlacement(blockPos);
            placedPositions.add(blockPos);
            this.missingBlockAttempts = 0;

            retryPlacement = antiCheatModule.isPaperPlacement();
            if (blockPos != positions.getLast() && this.shouldSendBatchRotation()) {
               BlockPos nextBlockPos = (BlockPos)positions.getFirst();
               PlacementModule.PlacementAngles currentAnglesSnapshot = placementResult.angles();

               if (this.sendBatchRotation(
                  blockPos, nextBlockPos, currentAnglesSnapshot, previousAngles
               )) {
                  previousAngles = placementResult.angles();
               }
            }
         }
      }

      if (!this.batchCooldownActive) {
         this.batchCooldownActive = true;
         TaskScheduler cooldownScheduler = ClientContext.taskScheduler;
         Runnable clearBatchCooldown = () -> this.batchCooldownActive = false;

         cooldownScheduler.scheduleRotationTask(clearBatchCooldown, 1);
      }

      if (this.inventoryPrepared) {
         InventoryUtils.swapBack(previousSlot, blockSlot);
         this.inventoryPrepared = false;
      }
   }

   private PlacementModule.PlacementResult placeBlock(InventoryUtils.SwapSlot blockSlot, BlockPos blockPos) {
      if (OffhandModule.isRecentSwapWindowActive()) {
         return PlacementModule.PlacementResult.FAILED;
      }

      boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();

      Boolean raytraceEnabled = this.placementRaytraceSetting.getValue();

      this.placementDirection = BlockInteractionUtils.findSupportDirection(
         blockPos, strictDirection, raytraceEnabled
      );
      if (this.placementDirection == null) {
         if (!this.placementAirPlaceSetting.getValue()) {
            return PlacementModule.PlacementResult.FAILED;
         }
      }

      Vec3 blockCenterSnapshot = blockPos.getCenter();
      Direction rotationDirection = this.placementDirection;
      float[] rotations = RotationManager.getRotationsToFace(blockCenterSnapshot, rotationDirection);

      boolean raytraceRequired = this.placementRaytraceSetting.getValue();
      Vec3 hitPosition = null;
      if (this.placementDirection != null) {
         BlockSampleMode hitPointSamplerSnapshot = BlockSampleMode.X8;

         BlockPos neighborPositionSnapshot = blockPos.relative(this.placementDirection);

         Direction oppositeFace = this.placementDirection.getOpposite();
         Stream<Vec3> visibleHitPoints = hitPointSamplerSnapshot.sampleFace(neighborPositionSnapshot, oppositeFace).stream()
            .map((Function<Vec3, Vec3>)samplePoint -> new Vec3(
               Math.round(samplePoint.x), Math.round(samplePoint.y), Math.round(samplePoint.z)
            ))
            .filter((Predicate<Vec3>)samplePoint -> {
               Vec3 eyePosition = ClientAccess.zioMinecraft.player.getEyePosition();

               BlockPos neighborForVisibility = blockPos.relative(this.placementDirection);

               Direction oppositeForVisibility = this.placementDirection.getOpposite();
               return WorldUtils.canSee(eyePosition, samplePoint, neighborForVisibility, oppositeForVisibility);
            });
         Comparator<Vec3> hitDistanceOrder = Comparator.comparing((Function<Vec3, Double>)samplePoint -> {
            Vec3 eyePosition = ClientAccess.zioMinecraft.player.getEyePosition();
            double squaredDistance = eyePosition.distanceToSqr(samplePoint);
            return squaredDistance;
         });
         Optional<Vec3> hitPointOptional = visibleHitPoints.min(hitDistanceOrder);
         hitPosition = hitPointOptional.orElse(null);
         if (hitPosition != null) {
            raytraceRequired = false;
         }
      } else {
         raytraceRequired &= !WorldUtils.canSeeBlock(blockPos);
      }

      boolean placementFailed = true;
      if (!this.inventoryPrepared) {
         InventoryUtils.swapTo(blockSlot);

         this.inventoryPrepared = true;
      }

      if (!raytraceRequired && this.tryPlaceAtHit(blockPos, hitPosition, blockSlot.getStack())) {
         if (RotationManager.isMovementSyncEnabled()) {
            if (antiCheatModule.is2b2tEnabled()) {
               double yawBeforeJitter = rotations[0];
               double yawRandom = Math.random();

               rotations[0] = (float)(yawBeforeJitter + yawRandom * 0.009999999776482582D);
               double pitchBeforeJitter = rotations[1];
               double pitchRandom = Math.random();

               rotations[1] = (float)(pitchBeforeJitter + pitchRandom * 0.009999999776482582D);
            }
         }

         this.placementTimer.reset();

         if (this.placementRenderSetting.getValue()) {
            ClientContext.featureBoxRenderer.addBox(this, blockPos);
         }

         this.lastPlacementHitPosition = blockPos.getCenter();
         if (this.placementDirection != null) {
            double aimPointOffset = MathConstants.ONE_HALF;
            Direction aimPointDirection = this.placementDirection;
            Vec3 aimPointSnapshot = this.lastPlacementHitPosition;
            this.lastPlacementHitPosition = aimPointSnapshot.relative(aimPointDirection, aimPointOffset);
         }

         if (this.placementRotateSetting.getValue()
            && this.tickPlacementActive) {
            RotationManager rotationManagerSnapshot = ClientContext.rotationManager;
            int rotationPriority = this.getPlacementRotationPriority();
            rotationManagerSnapshot.requestRotation(rotations, rotationPriority);
         }

         this.placementsThisCycle = this.placementsThisCycle
            + 1;
         this.placedThisCycle = true;
         placementFailed = false;
      }

      this.onPlacementAttempt(blockPos, placementFailed);
      return new PlacementModule.PlacementResult(
         !placementFailed, new PlacementModule.PlacementAngles(rotations[0], rotations[1])
      );
   }

   public abstract List getPlacementPositions();

   protected void breakInstantlyMineableBlock(BlockPos blockPos) {
      BlockState blockState = ClientAccess.zioMinecraft.level.getBlockState(blockPos);
      if (blockState.canBeReplaced()) {
         return;
      }

      if (ClientAccess.zioMinecraft.level.getBlockState(blockPos).getDestroySpeed(ClientAccess.zioMinecraft.level, blockPos)
         == 0.0F) {
         PacketSender.send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, blockPos, Direction.DOWN));
         PacketSender.send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, Direction.DOWN));
      }
   }

   protected boolean checkPlacementSwapSlot(InventoryUtils.SwapSlot blockSlot) {
      boolean slotUsable = false;
      if (blockSlot.isFound()) {
         ItemStack stack = blockSlot.getStack();
         slotUsable = !stack.isEmpty();
      }
      if (!slotUsable) {
         this.missingBlockAttempts++;
      }

      if (this.missingBlockAttempts >= 30
         && !this.ignoreMissingBlocks) {
         this.missingBlockAttempts = 0;
         String moduleName = this.getName();
         MutableComponent nameComponentSnapshot = Component.literal(moduleName);

         MutableComponent warningText = nameComponentSnapshot.append(" is out of blocks!");

         ChatUtils.printChat(warningText, ChatUtils.createSignature(-2), ChatUtils.MessagePriority.HIGH);

         this.placementsThisCycle = 1337;
         this.disable();
         slotUsable = false;
      }

      return slotUsable;
   }

   protected void attackCrystalsAtPositions(List positions) {
      Iterator positionIterator = positions.iterator();

      while (positionIterator.hasNext()) {
         BlockPos blockPos = (BlockPos)positionIterator.next();

         if (!this.placementAttackSetting.getValue()) {
            continue;
         }
         ElapsedTimer attackTimerSnapshot = this.attackTimer;

         long attackDelayMillis = ClientContext.crystalInteractionManager.getAttackDelayMillis();
         if (!attackTimerSnapshot.hasElapsed(attackDelayMillis)) {
            continue;
         }

         int attackRange = ClientContext.crystalInteractionManager.getMinimumCrystalAge();

         this.pendingAttackEntity = BlockInteractionUtils.attackCrystalAt(
            blockPos, attackRange
         );
         if (this.pendingAttackEntity == null) {
            continue;
         }

         if (RotationManager.isMovementSyncEnabled()) {
            float[] targetAngles = RotationManager.getRotationsToEntity(
               this.pendingAttackEntity
            );
            RotationManager rotationManagerSnapshot = ClientContext.rotationManager;
            int rotationPriority = this.getPlacementRotationPriority() + 1;
            rotationManagerSnapshot.requestRotation(targetAngles, rotationPriority);
         }

         if (this.shouldDiscardAttackedCrystal()) {
            ClientAccess.zioMinecraft.execute((Runnable)() -> {
                  this.pendingAttackEntity.discard();
               });
         }

         attackTimerSnapshot = this.attackTimer;

         attackTimerSnapshot.reset();
         break;
      }
   }

   protected void ignoreMissingPlacementBlocks() {
      this.ignoreMissingBlocks = true;
   }

   protected boolean hasPlacedThisCycle() {
      return this.placedThisCycle;
   }

   protected boolean shouldHandleCrystalSpawn() {
      return true;
   }

   public static void bindModules(ModuleRegistry modules) {
      antiCheatModule = (AntiCheatModule)modules.getModule(
         AntiCheatModule.class
      );
      chorusControlModule = (ChorusControlModule)modules.getModule(
         ChorusControlModule.class
      );
      multiTaskModule = (MultiTaskModule)modules.getModule(
         MultiTaskModule.class
      );
   }

   public record PlacementAngles(
      float yaw,
      float pitch
   ) {
      private HitResult raycast() {
         float pitchRadians = this.pitch
            * MathConstants.RADIANS_PER_DEGREE;
         float negativeYawRadians = -this.yaw
            * MathConstants.RADIANS_PER_DEGREE;
         float yawCos = Mth.cos((double)negativeYawRadians);
         float yawSin = Mth.sin((double)negativeYawRadians);
         float pitchCos = Mth.cos((double)pitchRadians);
         float pitchSin = Mth.sin((double)pitchRadians);
         Vec3 lookDirection = new Vec3(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);

         Vec3 rayStart = ClientAccess.zioMinecraft.player.getEyePosition();
         double scaledX = lookDirection.x * 5.0D;
         double scaledY = lookDirection.y * 5.0D;
         double scaledZ = lookDirection.z * 5.0D;
         Vec3 rayEnd = rayStart.add(scaledX, scaledY, scaledZ);
         BlockRaycastContext.Builder builder = new BlockRaycastContext.Builder(ClientAccess.zioMinecraft.player.getEyePosition(), rayEnd);

         return RaycastUtils.raycast(builder.withClassifier(BlockRaycastClassifier.solidPositions(ClientContext.worldStateManager.getPendingPlacements().keySet())).build());
      }

      public boolean intersectsBlock(BlockPos blockPos) {
         HitResult hit = this.raycast();

         if (hit.getType() != Type.BLOCK) {
            return false;
         }
         AABB hitBounds = new AABB(hit.getLocation().add(-0.01D, -0.01D, -0.01D), hit.getLocation().add(0.01D, 0.01D, 0.01D));
         AABB blockBounds = new AABB(blockPos);
         return hitBounds.intersects(blockBounds);
      }
   }

   public record PlacementResult(
      boolean success,
      PlacementModule.PlacementAngles angles
   ) {
      public static final PlacementModule.PlacementResult FAILED = new PlacementModule.PlacementResult(false, null);
   }
}
