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
import dev.zio.client.events.ClientLevelAddEntityEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.SelectedSlotChangeEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.MinecraftDisconnectEvent;
import dev.zio.client.events.MultiPlayerGameModeStartDestroyBlockEvent;
import dev.zio.client.managers.ServerStateManager;
import dev.zio.client.managers.TaskScheduler;
import dev.zio.client.managers.SwingInteractionManager;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.combat.ExtraBreakTarget;
import dev.zio.client.utils.world.BlockBreakingState;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.entity.SwingAction;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.utils.inventory.ExtraBreakTracker;
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.managers.TickRateController;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.WorldStateManager;
import dev.zio.client.events.EventStage;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.combat.AnchorAuraModule;
import dev.zio.client.modules.combat.AutoCrystalModule;
import dev.zio.client.modules.combat.AutoExpModule;
import dev.zio.client.modules.combat.AutoMineModule;
import dev.zio.client.modules.combat.AutoMineMiningTracker;
import dev.zio.client.modules.exploit.MultiTaskModule;
import dev.zio.client.modules.render.LogoutSpotsModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.BlockSampleMode;
import dev.zio.client.settings.options.ListFilterMode;
import dev.zio.client.settings.options.SpeedMineAutoSwapChoice;
import dev.zio.client.settings.options.SpeedMineColorModeChoice;
import dev.zio.client.settings.options.SpeedMineRebreakChoice;
import dev.zio.client.settings.options.SpeedMineRenderModeChoice;
import dev.zio.client.settings.options.SpeedMineValue121Choice;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SpeedMineModule extends Module {
   private static AutoExpModule autoExpModule = (AutoExpModule)ClientContext.getModule(AutoExpModule.class);
   private static AutoCrystalModule autoCrystalModule = (AutoCrystalModule)ClientContext.getModule(AutoCrystalModule.class);
   private static AutoMineModule autoMineModule = (AutoMineModule)ClientContext.getModule(AutoMineModule.class);
   private static NoGlitchBlocksModule noGlitchBlocksModule = (NoGlitchBlocksModule)ClientContext.getModule(NoGlitchBlocksModule.class);
   private static ItemSaverModule itemSaverModule = (ItemSaverModule)ClientContext.getModule(ItemSaverModule.class);
   private static LogoutSpotsModule logoutSpotsModule = (LogoutSpotsModule)ClientContext.getModule(LogoutSpotsModule.class);
   private static MultiTaskModule multiTaskModule = (MultiTaskModule)ClientContext.getModule(MultiTaskModule.class);
   private static AnchorAuraModule anchorAuraModule = (AnchorAuraModule)ClientContext.getModule(AnchorAuraModule.class);
   public static boolean automatedMiningStart;
   protected final Setting<Set<Block>> blocksSetting;
   public final Setting<ListFilterMode> modeSetting;
   public final Setting<Float> damageSetting;
   public final Setting<Float> rangeSetting;
   public final Setting<Boolean> grimSetting;
   private final Setting<Boolean> tpsSyncSetting;
   public final Setting<Boolean> noBreakDelaySetting;
   public final Setting<Boolean> extraBreakSetting;
   public final Setting<Boolean> sneakExtendSetting;
   public final Setting<Boolean> rebreakEnabledSetting;
   public final Setting<SpeedMineRebreakChoice> rebreakSetting;
   public final Setting<Boolean> asyncSetting;
   protected final Setting<Float> instantDelaySetting;
   private final Setting<Boolean> holdingBestSetting;
   public final Setting<Boolean> autoSwapSetting;
   final Setting<SpeedMineAutoSwapChoice> autoSwapModeSetting;
   private final Setting<Boolean> swapBackSetting;
   private final Setting<SpeedMineValue121Choice> value121Setting;
   public final Setting<Boolean> alternativeSetting;
   public final Setting<Boolean> swapResetSetting;
   final Setting<Boolean> rotateSetting;
   private final Setting<Float> limitSetting;
   final Setting<Boolean> renderSetting;
   public final Setting<Float> lineWidthSetting;
   public final Setting<SpeedMineRenderModeChoice> renderModeSetting;
   public final Setting<Boolean> airSetting;
   public final Setting<Boolean> fadeSetting;
   public final Setting<SpeedMineColorModeChoice> colorModeSetting;
   public final Setting<Color> fillSetting;
   public final Setting<Color> outlineSetting;
   private final Setting<Boolean> abortSetting;
   private final SpeedMineRebreakTracker rebreakTracker;
   private final SpeedMineRenderQueue renderQueue;
   private final ExtraBreakTracker extraBreakTracker;
   private final ArrayDeque<Boolean> pendingBreakPositions;
   private final ElapsedTimer startDestroyRetryTimer;
   private final ElapsedTimer retargetTimer;
   private final ElapsedTimer breakDelayTimer;
   private final ElapsedTimer itemSpawnTimer;
   private final ElapsedTimer crystalDelayTimer;
   private final ElapsedTimer extraBreakTimer;
   private final ElapsedTimer stalledBreakTimer;
   private static final AtomicBoolean swapPending = new AtomicBoolean();
   private static final AtomicBoolean restartPending = new AtomicBoolean();
   protected float miningProgress;
   private boolean waitingForRebreak;
   private float silentSwapProgress;
   private int bestToolSlot;
   private int previousSelectedSlot;
   private ExtraBreakTarget extraBreakTarget;
   protected BlockPos miningPos;
   protected BlockPos lastMiningPos;
   public volatile boolean crystalAttackPending;

   public boolean isInMiningRange(BlockPos blockPos) {
      return SpeedMineRange.isInRange(blockPos, this.rangeSetting.getValue());
   }

   private void rotateToBlock(BlockPos blockPos) {
      Boolean rotationEnabled = this.rotateSetting.getValue();
      if (!rotationEnabled) {
         return;
      }
      if (RotationManager.isSilentRotationMode()) {
         return;
      }
      if (!WorldUtils.canMineBlock(blockPos)) {
         return;
      }
      BlockSampleMode sampleMode = BlockSampleMode.X8;
      Vec3 targetPoint = WorldUtils.findVisiblePoint(
         blockPos, sampleMode
      );
      if (targetPoint == null) {
         targetPoint = blockPos.getCenter();
      }

      float[] rotations = RotationManager.getRotationsToPosition(targetPoint);
      RotationManager rotationManager = ClientContext.rotationManager;
      rotationManager.requestRotation(rotations, 150, true);

   }

   public int getMiningToolSlot() {
      if (this.bestToolSlot < 0) {
         return ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
      } else {
         return this.bestToolSlot;
      }
   }

   public static void clearRebreakAndReset(SpeedMineModule owner) {
      owner.lastMiningPos = null;
      reset(owner);
   }

   private boolean canBreakNearCrystal() {
      BlockPos miningPosition = this.miningPos;

      if (WorldUtils.getBlock(miningPosition)
         != Blocks.OBSIDIAN) {
         BlockPos currentPosition = this.miningPos;
         if (!WorldUtils.isAir(currentPosition)) {
            return true;
         }
      }

      AABB miningBounds = new AABB(this.getMiningPos());
      Iterator entities = ClientAccess.zioMinecraft.level.entitiesForRendering().iterator();

      while (entities.hasNext()) {
         Entity entity = (Entity)entities.next();
         if (entity instanceof EndCrystal) {
            AABB crystalBounds = entity.getBoundingBox();
            double verticalInset = MathConstants.ONE_QUARTER;
            if (crystalBounds.inflate(0.0, verticalInset, 0.0).intersects(miningBounds)) {
               return true;
            }
         }
      }

      BlockPos positionAboveCheck = this.getMiningPos();

      if (this.isPlayerAboveBlock(positionAboveCheck)) {
         ElapsedTimer crystalDelay = this.crystalDelayTimer;

         float baseDelayMillis = 1e+03F;
         ServerStateManager serverState = ClientContext.serverStateManager;

         long scaledDelayMillis = (long)(baseDelayMillis / serverState.getTickRateScale());
         if (!crystalDelay.hasElapsed(scaledDelayMillis)) {
            return false;
         }
      }

      return true;
   }

   public boolean isBlockAllowed(BlockPos blockPos) {
      if (WorldUtils.canMineBlock(blockPos)) {
         ListFilterMode filterMode = this.modeSetting.getValue();
         Block block = ClientAccess.zioMinecraft.level.getBlockState(blockPos).getBlock();
         Setting<Set<Block>> selectedBlocks = this.blocksSetting;
         if (ListFilterMode.matches(filterMode, block, selectedBlocks)) {
            return true;
         }
      }

      return false;
   }

   public ExtraBreakTracker getExtraBreakTracker() {
      return this.extraBreakTracker;
   }

   private boolean isPlayerAboveBlock(BlockPos blockPos) {
      if (blockPos == null) {
         return true;
      }

      AABB blockBounds = new AABB(blockPos);
      ClientLevel level = ClientAccess.zioMinecraft.level;
      ArrayList players = new ArrayList<>(level.players());
      if (logoutSpotsModule.isToggled()) {
         Collection logoutPlayers = logoutSpotsModule.getSpotPlayers();
         players.addAll(logoutPlayers);
      }

      Iterator playerIterator = players.iterator();

      while (playerIterator.hasNext()) {
         LivingEntity player = (LivingEntity)playerIterator.next();
         if (player != ClientAccess.zioMinecraft.player) {
            AABB playerBounds = player.getBoundingBox();

            playerBounds = playerBounds.expandTowards(0.0, 1.0D, 0.0);

            if (playerBounds.inflate(1.0D, 0.0, 1.0D).intersects(blockBounds)) {
               if (this.getMiningPos().getY() - player.getBlockY() > 0) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public BlockPos getMiningPos() {
      return this.miningPos;
   }

   public BlockPos getMiningOrNearlyBrokenPos() {
      if (this.isToggled()) {
         return this.miningPos;
      }

      if (ClientAccess.zioMinecraft.gameMode.isDestroying()) {
         if ((((BlockBreakingState)ClientAccess.zioMinecraft.gameMode).getBreakingProgress()) >= 0.9D) {
            return ((BlockBreakingState)ClientAccess.zioMinecraft.gameMode).getCurrentBreakingBlock();
         }
      }

      return null;
   }

   public static boolean isSwapPending() {
      return swapPending.get();
   }

   public float getMiningProgress() {
      return Mth.clamp(this.miningProgress, 0.0F, 1.0F);
   }

   private void playBreakEffects(BlockPos blockPos) {
      if (ClientAccess.zioMinecraft.level.getFluidState(blockPos).isEmpty()) {
         BlockState state = ClientAccess.zioMinecraft.level.getBlockState(blockPos);
         if (!state.isAir()) {
            SoundType soundType = state.getSoundType();
            SoundEvent sound = soundType.getBreakSound();
            float soundValue = soundType.getVolume();

            soundValue += 1.0F;

            float volume = soundValue / 2.0F;
            soundValue = soundType.getPitch();
            float pitch = soundValue * 0.8F;
            SoundSource soundSource = SoundSource.BLOCKS;
            ClientAccess.zioMinecraft.level.playLocalSound(blockPos, sound, soundSource, volume, pitch, false);
         }

         ClientLevel level = ClientAccess.zioMinecraft.level;
         BlockState destroyedState = ClientAccess.zioMinecraft.level.getBlockState(blockPos);
         level.addDestroyBlockEffect(blockPos, destroyedState);
      }
   }

   public float getLineWidth() {
      return this.lineWidthSetting.getValue();
   }

   public BlockPos getRebreakPos() {
      SpeedMineRebreakChoice rebreakMode = this.rebreakSetting.getValue();
      switch (rebreakMode) {
         case NONE:
            return null;
         case FAST:
            return this.lastMiningPos;
         case INSTANT:
            return this.miningPos;
         default:
            throw new MatchException(null, null);
      }
   }

   public ExtraBreakTarget getExtraBreakTarget() {
      return this.extraBreakTarget;
   }

   private boolean isMainHandMultitask() {
      InteractionHand hand = ClientAccess.zioMinecraft.player.getUsedItemHand();
      return multiTaskModule.shouldPauseModule(this) && hand == InteractionHand.MAIN_HAND;
   }

   public ElapsedTimer getBreakDelayTimer() {
      return this.breakDelayTimer;
   }

   public double getTickBreakProgress() {
      if (this.miningPos == null) {
         return 0.0;
      }

      BlockPos miningPosition = this.miningPos;
      BlockState miningBlockState = ClientAccess.zioMinecraft.level.getBlockState(miningPosition);

      float tickRateScale = ClientContext.serverStateManager.getTickRateScale();
      Inventory inventory = ClientAccess.zioMinecraft.player.getInventory();

      int toolSlot = this.getMiningToolSlot();
      ItemStack tool = inventory.getItem(toolSlot);
      boolean onGround = ClientAccess.zioMinecraft.player.onGround();
      return WorldUtils.getBreakProgress(tool, miningBlockState, onGround) * tickRateScale;
   }

   private boolean restartForRebreak() {
      SpeedMineRebreakChoice rebreakMode = this.rebreakSetting.getValue();
      if (!rebreakMode.isInstant()) {
         return false;
      }

      if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NONE) {
         return false;
      }

      SpeedMineRebreakTracker tracker = this.rebreakTracker;

      tracker.validateTarget();
      tracker = this.rebreakTracker;

      boolean delayElapsed = tracker.hasRebreakDelayElapsed();
      BlockPos targetPosition = this.miningPos;
      if (delayElapsed) {
         this.renderQueue.remove(targetPosition);

         reset(this);
         restartPending.set(true);
         TaskScheduler scheduleRebreakRotationReceiver = ClientContext.taskScheduler;
         Runnable scheduleRebreakRotationTask = () -> {
               restartPending.set(false);
               Direction direction = Direction.UP;
               ClientAccess.zioMinecraft.gameMode.startDestroyBlock(targetPosition, direction);
            };
         scheduleRebreakRotationReceiver.scheduleRotationTask(scheduleRebreakRotationTask, 1);
      }

      return delayElapsed;
   }

   public boolean isBreakDelayActive() {
      if (this.noBreakDelaySetting.getValue()) {
         return false;
      }

      ElapsedTimer timer = this.breakDelayTimer;

      return !timer.hasElapsed(250L);
   }

   private boolean isAlternativeAllowed() {
      if (RotationManager.is2b2tMode()) {
         MultiTaskModule multiTask = multiTaskModule;

         if (!multiTask.isItemUseRestricted()) {
            MultiTaskModule recentMultiTask = multiTaskModule;

            if (!recentMultiTask.isItemUseActiveOrRecent(150L)) {
               return false;
            }
         }
      }

      return this.alternativeSetting.getValue();
   }

   public float getRenderProgress() {
      float damage = (this.damageSetting.getValue()).floatValue();
      return Mth.clamp(this.getMiningProgress() / damage, 0.0F, 1.0F);
   }

   public static void reset(SpeedMineModule owner) {
      // Abort the active server-side attempt before clearing the local progress.
      if (owner.miningPos != null) {
         if (!owner.isWorldUnavailable()) {
            Action abortAction = Action.ABORT_DESTROY_BLOCK;
            BlockPos abortPosition = owner.miningPos;
            BlockPos facePosition = owner.miningPos;

            Direction direction = selectDestroyFace(facePosition);
            PacketSender.sendPlayerAction(abortAction, abortPosition, direction);
            SwingAction swingAction = SwingAction.MINING;
            InteractionHand hand = InteractionHand.MAIN_HAND;
            ClientContext.swingManager.swingPacket(hand, swingAction);
            ((BlockBreakingState)ClientAccess.zioMinecraft.gameMode).setBreakingBlock(false);
            ((BlockBreakingState)ClientAccess.zioMinecraft.gameMode).setBreakingProgress(0.0F);
         }

      }
      owner.miningPos = null;
      owner.silentSwapProgress = owner.miningProgress = 0.0F;
      owner.bestToolSlot = -1;
      owner.pendingBreakPositions.clear();

      owner.retargetTimer.reset();
      owner.rebreakTracker.reset();
   }

   @Override
   public String getInfo() {
      BlockPos miningPosition = this.miningPos;

      if (!WorldUtils.canMineBlock(miningPosition)) {
         return null;
      }

      Object[] arguments = new Object[1];

      float progress = this.getRenderProgress();
      arguments[0] = progress;
      return "%.2f".formatted(arguments);
   }

   @Override
   public void onEnable() {
      reset(this);
      this.lastMiningPos = null;
      swapPending.set(false);

      this.renderQueue.clear();
   }

   private static Direction selectDestroyFace(BlockPos blockPos) {
      List directions = BlockInteractionUtils.getStrictDirections(blockPos);
      return !directions.isEmpty() ? (Direction)directions.get(0) : Direction.UP;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      Boolean tpsSyncEnabled = this.tpsSyncSetting.getValue();
      float tickScale;
      if ((tpsSyncEnabled)) {
         tickScale = ClientContext.serverStateManager.getTickRateScale();
      } else {
         tickScale = 1.0F;
      }

      float progressScale = tickScale;

      float multiplier = 1.0F;
      TickRateController tickRateController = ClientContext.tickRateController;
      progressScale *= multiplier / tickRateController.getMultiplier();

      this.getExtraBreakTracker().pruneExpired();

      this.renderQueue.snapshotProgress();
      this.updateExtraBreakProgress(progressScale);
      this.updateMiningProgress(progressScale);
   }

   private void updateExtraBreakProgress(float progressScale) {
      if (this.extraBreakTarget != null) {
         BlockPos extraPosition = this.extraBreakTarget.getPosition();
         BlockState extraState = ClientAccess.zioMinecraft.level.getBlockState(extraPosition);
         int extraToolSlot = InventoryUtils.findBestToolSlot(
            extraPosition, this.alternativeSetting.getValue());
         ItemStack extraTool = ClientAccess.zioMinecraft.player.getInventory().getItem(extraToolSlot);
         boolean extraOnGround = ClientAccess.zioMinecraft.player.onGround();
         double extraProgress = WorldUtils.getBreakProgress(
               extraTool, extraState, extraOnGround
            )
            * progressScale;
         ExtraBreakTarget extraTarget = this.extraBreakTarget;
         float extraProgressFloat = (float)extraProgress;

         extraTarget.addProgress(extraProgressFloat);
         if (!(extraPosition.equals(this.miningPos))) {
            SpeedMineRenderQueue extraRenderQueue = this.renderQueue;

            float extraRenderProgress = this.extraBreakTarget.getClampedProgress();
            extraRenderQueue.setProgress(extraPosition, extraRenderProgress);
         }

         if (ClientAccess.zioMinecraft.level.isEmptyBlock(extraPosition)) {
            this.extraBreakTarget = null;

            this.extraBreakTimer.reset();
         }
      }
   }

   private void updateMiningProgress(float progressScale) {
      if (this.lastMiningPos != null) {
         BlockPos lastPosition = this.lastMiningPos;
         if ((ClientAccess.zioMinecraft.level.getBlockState(lastPosition).getBlock() instanceof ShulkerBoxBlock)) {
            WorldStateManager worldState = ClientContext.worldStateManager;
            BlockPos placedPosition = this.lastMiningPos;

            if ((worldState.hasKnownPlacement(placedPosition))) {
               this.lastMiningPos = null;
            }
         }
      }

      AtomicBoolean pendingRestart = restartPending;
      if (!(pendingRestart.get())) {
         if (this.miningPos == null) {
            if (this.lastMiningPos != null) {
               LocalPlayer player = ClientAccess.zioMinecraft.player;
               if (!(player.isCreative())) {
                  BlockPos rebreakPosition = this.lastMiningPos;

                  if ((WorldUtils.canMineBlock(rebreakPosition))) {
                     if ((this.rebreakSetting.getValue()).isFast()) {
                        this.miningPos = this.lastMiningPos;
                     }
                  }
               }
            }
         } else {
            BlockPos currentPosition = this.miningPos;

            if (!(WorldUtils.canMineBlock(currentPosition))) {
               if ((this.rebreakSetting.getValue()).isInstant()) {
                  BlockPos airPosition = this.miningPos;
                  if ((WorldUtils.isAir(airPosition))) {
                     if (this.airSetting.getValue()) {
                        SpeedMineRenderQueue airRenderQueue = this.renderQueue;
                        BlockPos airRenderPosition = this.miningPos;
                        float airProgress = this.miningProgress;
                        Float airDamageThreshold = this.damageSetting.getValue();
                        float airRenderProgress = airProgress / airDamageThreshold;
                        airRenderQueue.setProgress(airRenderPosition, airRenderProgress);
                     }
                  }
               }

               if ((this.rebreakSetting.getValue()).isFast()) {
                  ArrayDeque<Boolean> pendingPositions = this.pendingBreakPositions;
                  boolean queuedOnGround = ClientAccess.zioMinecraft.player.onGround();
                  Boolean queuedGroundState = queuedOnGround;
                  pendingPositions.add(queuedGroundState);

                  if (this.pendingBreakPositions.size() > (200)) {
                     this.pendingBreakPositions.removeLast();
                  }
               }
            } else {
               BlockPos miningPosition = this.miningPos;
               BlockState miningState = ClientAccess.zioMinecraft.level.getBlockState(miningPosition);
               Inventory inventory = ClientAccess.zioMinecraft.player.getInventory();

               int toolSlot = this.getMiningToolSlot();
               ItemStack toolStack = inventory.getItem(toolSlot);
               ItemStack unenchantedStack = new ItemStack(toolStack.getItem());

               int selectedToolSlot = this.getMiningToolSlot();
               Inventory selectionInventory = ClientAccess.zioMinecraft.player.getInventory();
               if (selectedToolSlot != selectionInventory.getSelectedSlot()) {
                  if (this.value121Setting.getValue() == SpeedMineValue121Choice.SLOW) {
                     toolStack = unenchantedStack;
                  }
               }

               // Fast rebreak accumulates ticks while the old block is absent and replays them on return.
               boolean processedQueuedProgress = false;
               if ((this.rebreakSetting.getValue()).isFast()) {
                  while (true) {
                     if ((this.pendingBreakPositions.isEmpty())) {
                        break;
                     }

                     boolean queuedOnGround = this.pendingBreakPositions.poll();
                     this.miningProgress = this.miningProgress
                        + (float)(
                           WorldUtils.getBreakProgress(
                                 toolStack, miningState, queuedOnGround
                              )
                              * progressScale
                        );
                     processedQueuedProgress = true;
                     SpeedMineRenderQueue queuedRenderQueue = this.renderQueue;
                     BlockPos queuedPosition = this.miningPos;
                     float queuedProgress = this.miningProgress;
                     Float queuedDamageThreshold = this.damageSetting.getValue();
                     float queuedRenderProgress = queuedProgress / queuedDamageThreshold;
                     queuedRenderQueue.setProgressTwice(queuedPosition, queuedRenderProgress);
                  }
               }

               if (processedQueuedProgress) {
                  Block obsidian = Blocks.OBSIDIAN;
                  if ((miningState.is(obsidian))) {
                     float obsidianProgress = this.miningProgress;

                     float maximumObsidianProgress = 0.99F;
                     this.miningProgress = Mth.clamp(
                        obsidianProgress, 0.0F, maximumObsidianProgress
                     );
                     SpeedMineRenderQueue obsidianRenderQueue = this.renderQueue;
                     BlockPos obsidianPosition = this.miningPos;
                     float clampedObsidianProgress = this.miningProgress;
                     Float obsidianDamageThreshold = this.damageSetting.getValue();
                     float obsidianRenderProgress = clampedObsidianProgress / obsidianDamageThreshold;
                     obsidianRenderQueue.setProgressTwice(obsidianPosition, obsidianRenderProgress);
                     return;
                  }
               }

               // Track the unenchanted tool separately for the delayed silent-swap decision.
               float previousSilentProgress = this.silentSwapProgress;
               boolean silentOnGround = ClientAccess.zioMinecraft.player.onGround();
               this.silentSwapProgress = previousSilentProgress
                  + (float)(
                     WorldUtils.getBreakProgress(
                           unenchantedStack, miningState, silentOnGround
                        )
                        * progressScale
                  );
               float previousMiningProgress = this.miningProgress;
               boolean miningOnGround = ClientAccess.zioMinecraft.player.onGround();
               this.miningProgress = previousMiningProgress
                  + (float)(
                     WorldUtils.getBreakProgress(
                           toolStack, miningState, miningOnGround
                        )
                        * progressScale
                  );
               SpeedMineRenderQueue miningRenderQueue = this.renderQueue;
               BlockPos renderPosition = this.miningPos;
               float renderProgress = this.miningProgress;
               Float renderDamageThreshold = this.damageSetting.getValue();
               float renderFraction = renderProgress / renderDamageThreshold;
               miningRenderQueue.setProgress(renderPosition, renderFraction);
            }
         }
      }
   }

   private boolean canReplaceExtraBreakTarget(ExtraBreakTarget candidate, BlockPos blockPos) {
      ElapsedTimer timer = this.extraBreakTimer;

      if (!timer.hasElapsed(200L)) {
         return false;
      }

      if (candidate == null) {
         return true;
      }

      boolean samePosition = candidate.getPosition().equals(blockPos);

      BlockPos candidatePosition = candidate.getPosition();
      boolean invalidCandidate = !this.isBlockAllowed(candidatePosition);
      if (!samePosition && !invalidCandidate) {
         AtomicBoolean pendingSwap = swapPending;
         if (!pendingSwap.get()) {
            return true;
         }
      }

      return false;
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      reset(this);
      this.lastMiningPos = null;
      this.extraBreakTarget = null;
   }

   @EventHandler
   private void onPacketReceive(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundRespawnPacket) {
         reset(this);
         this.lastMiningPos = null;
         this.extraBreakTarget = null;
      }

      this.rebreakTracker.handleBlockUpdates(event);
   }

   public boolean hasCrystalDelayElapsed(long delayMillis) {
      return this.crystalDelayTimer.hasElapsed(delayMillis);
   }

   @EventHandler(priority = 100)
   private void onSelectedSlotChange(SelectedSlotChangeEvent event) {
      Boolean alternativeEnabled = this.alternativeSetting.getValue();
      if (!alternativeEnabled) {
         return;
      }
      if (this.autoSwapModeSetting.getValue() != SpeedMineAutoSwapChoice.SILENT) {
         return;
      }
      Boolean resetEnabled = this.swapResetSetting.getValue();
      if (!resetEnabled) {
         return;
      }
      // A selected-slot change restarts the attempt after rotation scheduling has caught up.
      restartPending.set(true);
      BlockPos currentPosition = this.miningPos;

      if (WorldUtils.canMineBlock(currentPosition)) {
         BlockPos renderPosition = this.miningPos;
         this.renderQueue.setProgressTwice(renderPosition, 0.0F);
      }

      this.silentSwapProgress = 0.0F;
      this.miningProgress = 0.0F;
      TaskScheduler scheduleStartDestroyRotationReceiver = ClientContext.taskScheduler;
      Runnable scheduleStartDestroyRotationTask = () -> {
            restartPending.set(false);
            BlockPos targetPosition = this.miningPos;
            if (targetPosition != null) {
               SwingAction swingAction = SwingAction.MINING;
               InteractionHand hand = InteractionHand.MAIN_HAND;
               ClientContext.swingManager.swing(hand, swingAction);

               if (WorldUtils.canMineBlock(targetPosition)) {
                  automatedMiningStart = true;

                  reset(this);

                  MultiPlayerGameModeStartDestroyBlockEvent startEvent = new MultiPlayerGameModeStartDestroyBlockEvent(
                     targetPosition,
                     selectDestroyFace(targetPosition)
                  );
                  this.onMultiPlayerGameModeStartDestroyBlockEvent(startEvent);
                  automatedMiningStart = false;
               } else {
                  if ((this.rebreakSetting.getValue()).isFast()) {
                     Action startAction = Action.START_DESTROY_BLOCK;

                     Direction direction = selectDestroyFace(targetPosition);
                     PacketSender.sendPlayerAction(
                        startAction, targetPosition, direction
                     );
                     this.pendingBreakPositions.clear();
                  }
               }
            }
         };
      scheduleStartDestroyRotationReceiver.scheduleRotationTask(scheduleStartDestroyRotationTask, 1);
      return;

   }

   @EventHandler
   private void onClientLevelAddEntityEvent(ClientLevelAddEntityEvent event) {
      if (this.miningPos != null) {
         if ((this.rebreakSetting.getValue()).isInstant()) {
            if (!(event.getEntity() instanceof ItemEntity)) {
               return;
            }

            AABB miningBounds = new AABB(this.miningPos);
            Vec3 entityPosition = event.getEntity().position();
            if (miningBounds.contains(entityPosition)) {
               if (this.asyncSetting.getValue()) {
                  this.crystalDelayTimer.reset();
               }

               if (this.alternativeSetting.getValue()) {
                  this.itemSpawnTimer.clear();
               }
            }

            return;
         }
      }
   }

   @EventHandler
   private void onRenderWorld(WorldRenderEvent.Main event) {
      if (this.renderSetting.getValue()) {
         if (ClientAccess.zioMinecraft.player.gameMode() == GameType.SURVIVAL) {
            this.renderQueue.render(event);
         }
      }
   }

   @EventHandler(priority = -100)
   private void onMultiPlayerGameModeStartDestroyBlockEvent(
      MultiPlayerGameModeStartDestroyBlockEvent event
   ) {
      BlockPos requestedPosition = event.getPos();
      if (event.isCancelled()) {
         return;
      }
      if (!this.isBlockAllowed(requestedPosition)) {
         this.lastMiningPos = null;

         reset(this);

         if (!WorldUtils.canMineBlock(requestedPosition)) {
            event.cancel();
         }
      } else {
         LocalPlayer spectatorCheckPlayer = ClientAccess.zioMinecraft.player;
         if (!spectatorCheckPlayer.isSpectator()) {
            LocalPlayer creativeCheckPlayer = ClientAccess.zioMinecraft.player;
            if (!creativeCheckPlayer.isCreative()) {
               event.cancel();
               ElapsedTimer timer = this.startDestroyRetryTimer;

               if (!timer.hasElapsed(100L)
                  && !automatedMiningStart) {
                  if (requestedPosition.equals(this.miningPos)) {
                     timer = this.startDestroyRetryTimer;

                     timer.reset();
                  }

                  return;
               }

               if (this.extraBreakSetting.getValue()) {
                  if (this.sneakExtendSetting.getValue()) {
                     KeyMapping sneakKey = ClientAccess.zioMinecraft.options.keyShift;
                     if (KeyUtils.isPhysicallyDown(sneakKey)) {
                        ExtraBreakTarget currentExtraTarget = this.extraBreakTarget;
                        if (!this.canReplaceExtraBreakTarget(currentExtraTarget, requestedPosition)) {
                           return;
                        }

                        if (this.extraBreakTarget == null) {
                           float[] rotations = RotationManager.getRotationsToBlock(
                              requestedPosition);
                           float yaw = rotations[0];
                           float pitch = rotations[1];
                           Vec3 blockCenter = requestedPosition.getCenter();
                           Vec3 lookDirection = Vec3.directionFromRotation(pitch, yaw);
                           Position containingPosition = blockCenter.add(lookDirection);

                           BlockPos extendedPosition = BlockPos.containing(containingPosition);
                           if (this.isBlockAllowed(extendedPosition)) {
                              requestedPosition = extendedPosition;
                           }
                        }
                     }
                  }
               }

               if (!WorldUtils.canMineBlock(requestedPosition)
                  && !automatedMiningStart) {
                  return;
               }

               if ((this.extraBreakSetting.getValue()) && this.extraBreakTarget != null) {
                  ExtraBreakTarget existingExtraTarget = this.extraBreakTarget;

                  if (requestedPosition.equals(existingExtraTarget.getPosition())) {
                     return;
                  }
               }

               if (this.isBreakDelayActive()) {
                  BlockPos currentTarget = this.miningPos;

                  if (!WorldUtils.canMineBlock(currentTarget)) {
                     reset(this);
                  }

                  return;
               }
               if (!this.isInMiningRange(requestedPosition)) {
                  return;
               }

               timer = this.startDestroyRetryTimer;

               timer.reset();
               boolean changedTarget = false;
               if (this.miningPos != null
                  && !this.miningPos.equals(requestedPosition)) {
                  SpeedMineRenderQueue oldRenderQueue = this.renderQueue;
                  BlockPos oldTarget = this.miningPos;
                  oldRenderQueue.remove(oldTarget);

                  reset(this);
                  changedTarget = true;
               }

               if (!changedTarget
                  && this.miningPos != null
                  && this.miningPos.equals(requestedPosition)) {
                  timer = this.retargetTimer;

                  if (timer.hasElapsed(100L)) {
                     SpeedMineRenderQueue retargetRenderQueue = this.renderQueue;
                     BlockPos retargetPosition = this.miningPos;
                     retargetRenderQueue.remove(retargetPosition);
                     this.lastMiningPos = null;

                     reset(this);
                  }

                  return;
               }

               if (this.alternativeSetting.getValue()) {
                  if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT) {
                     if (this.swapResetSetting.getValue()) {
                        if (this.crystalAttackPending) {
                           this.crystalAttackPending = false;

                           reset(this);
                           return;
                        }

                        if (autoExpModule.isToggled()) {
                           if (autoExpModule.modeSetting.getValue() == AutoExpModule.ModeChoice.SILENT
                              && !autoExpModule.stopped
                              )
                            {
                              reset(this);
                              return;
                           }
                        }
                     }
                  }
               }

               if (!automatedMiningStart) {
                  autoMineModule.setMiningActive(true);
               }

               this.miningPos = this.lastMiningPos = requestedPosition;
               BlockPos facePosition = this.miningPos;

               Direction destroyFace = selectDestroyFace(facePosition);
               SwingAction miningSwing = SwingAction.MINING;
               InteractionHand miningHand = InteractionHand.MAIN_HAND;
               ClientContext.swingManager.swing(miningHand, miningSwing);
               BlockPos instantBreakPosition = this.miningPos;

               SpeedMineModule.InstantBreakResult instantBreak = this.evaluateInstantBreak(instantBreakPosition);
               int toolSlot = instantBreak.toolSlot();
               int selectedSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
               BlockPos hardnessPosition = this.miningPos;

               float hardness = WorldUtils.getHardness(hardnessPosition);
               if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NORMAL) {
                  BlockPos toolSearchPosition = this.miningPos;

                  toolSlot = InventoryUtils.findBestToolSlot(
                     toolSearchPosition, false);
                  if (toolSlot != selectedSlot) {
                     if (this.swapBackSetting.getValue()) {
                        this.previousSelectedSlot = selectedSlot;
                     }
                  }
               }

               if (instantBreak.canInstantBreak()) {
                  if (this.alternativeSetting.getValue()
                     && this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT) {
                     InventoryUtils.swapIntoSelectedSlot(toolSlot);
                  } else if (selectedSlot != instantBreak.toolSlot()) {
                     InventoryUtils.selectHotbarSlotForPlacement(toolSlot);
                  }
               }

               // Extra break sends a start and stop now so its progress can be tracked separately.
               if (this.extraBreakSetting.getValue() && !instantBreak.breaksInOneTick()) {
                  BlockPos extraStartPosition = this.miningPos;
                  Action extraStartAction = Action.START_DESTROY_BLOCK;
                  PacketSender.sendPlayerAction(extraStartAction, extraStartPosition, destroyFace);
                  BlockPos extraGrimPosition = this.miningPos;
                  this.sendGrimStartPacket(extraGrimPosition, destroyFace);
                  BlockPos extraStopPosition = this.miningPos;
                  Action extraStopAction = Action.STOP_DESTROY_BLOCK;
                  PacketSender.sendPlayerAction(extraStopAction, extraStopPosition, destroyFace);
                  timer = this.breakDelayTimer;
                  timer.reset();
                  if (this.extraBreakTarget == null) {
                     this.extraBreakTarget = new ExtraBreakTarget(this.miningPos);
                  }
               } else {
                  BlockPos startPosition = this.miningPos;
                  Action startAction = Action.START_DESTROY_BLOCK;
                  PacketSender.sendPlayerAction(startAction, startPosition, destroyFace);
                  BlockPos grimPosition = this.miningPos;
                  this.sendGrimStartPacket(grimPosition, destroyFace);
                  if (instantBreak.canInstantBreak()) {
                     if (!instantBreak.breaksInOneTick()) {
                        BlockPos stopPosition = this.miningPos;
                        Action stopAction = Action.STOP_DESTROY_BLOCK;
                        PacketSender.sendPlayerAction(
                           stopAction, stopPosition, destroyFace
                        );
                        timer = this.breakDelayTimer;

                        timer.reset();
                     }
                  }
               }

               if (instantBreak.canInstantBreak() && toolSlot != -1) {
                  if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT) {
                     if (this.alternativeSetting.getValue()) {
                        InventoryUtils.swapIntoSelectedSlot(toolSlot);
                     } else if (selectedSlot != toolSlot) {
                        InventoryUtils.selectHotbarSlotForPlacement(selectedSlot);
                     }
                  }

                  WorldStateManager worldState = ClientContext.worldStateManager;
                  BlockPos predictedPosition = this.miningPos;

                  worldState.destroyBlockPredicted(predictedPosition);
                  BlockPos attemptPosition = this.miningPos;
                  this.recordBreakAttempt(attemptPosition);
                  this.startDestroyRetryTimer.clear();
                  this.extraBreakTarget = null;
                  if ((this.rebreakSetting.getValue()).isInstant() && hardness == 0.0F) {
                     this.waitingForRebreak = true;
                  }

                  if ((this.rebreakSetting.getValue()).isNone()) {
                     this.miningPos = null;
                  }
               }

               timer = this.retargetTimer;

               timer.reset();
               return;
            }
         }
      }

   }

   @EventHandler(priority = 100)
   private void onMiningTick(RotationRequestEvent event) {
      boolean extraInventorySwap;
      boolean canSwapTools;
      if ((this.waitingForRebreak)) {
         BlockPos rebreakPosition = this.miningPos;

         if ((WorldUtils.canMineBlock(rebreakPosition))) {
            rebreakPosition = this.miningPos;

            if (WorldUtils.getHardness(rebreakPosition) > 0.0F) {
               this.waitingForRebreak = false;
               this.miningProgress = 0.0F;
            }
         }
      }

      BlockPos currentMiningPosition = this.miningPos;

      if ((WorldUtils.canMineBlock(currentMiningPosition))) {
         BlockPos toolSearchPosition = this.miningPos;
         this.bestToolSlot = InventoryUtils.findBestToolSlot(
            toolSearchPosition, this.alternativeSetting.getValue());
      } else {
         if ((this.rebreakSetting.getValue()).isNone()) {
            reset(this);
         }
      }

      int toolSlot = this.bestToolSlot;
      if (swapPending.get()) {
         return;
      }
      if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NONE) {
         ItemStack heldStack = ClientAccess.zioMinecraft.player.getMainHandItem();

         if (!(ItemSaverModule.isSafeToUse(heldStack))) {
            if ((itemSaverModule.isToggled())) {
               return;
            }
         }
      }

      float health = EntityUtils.getPlayerHealthWithAbsorption();
      canSwapTools = true;
      if (!(health > 4.0F)) {
         ItemStack heldStack = ClientAccess.zioMinecraft.player.getMainHandItem();
         Item totem = Items.TOTEM_OF_UNDYING;
         if (heldStack.is(totem)) {
            canSwapTools = false;
         }
      }

      if (this.extraBreakTarget != null) {
         ExtraBreakTarget extraTarget = this.extraBreakTarget;

         if (!(extraTarget.getPosition()
                  .equals(this.miningPos))) {
            if (this.extraBreakTarget.getClampedProgress() >= 1.0F) {
               if (!(this.isMainHandMultitask())
                  && canSwapTools) {
                  swapPending.set(true);
                  extraInventorySwap = false;
                  if (this.alternativeSetting.getValue()) {
                     if (!RotationManager.is2b2tMode()) {
                        extraInventorySwap = true;
                     }
                  }

                  boolean useExtraInventorySwap = extraInventorySwap;
                  int extraPreviousSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();

                  currentMiningPosition = this.extraBreakTarget.getPosition();
                  int extraToolSlot = InventoryUtils.findBestToolSlot(
                     currentMiningPosition, useExtraInventorySwap);
                  if (useExtraInventorySwap) {
                     InventoryUtils.swapIntoSelectedSlot(extraToolSlot);
                  } else {
                     PacketSender.sendSelectedSlot(extraToolSlot);
                  }

                  SwingInteractionManager extraSwingManager = ClientContext.swingManager;
                  InteractionHand extraSwingHand = InteractionHand.MAIN_HAND;
                  SwingAction extraSwingAction = SwingAction.MINING;
                  extraSwingManager.swing(
                     extraSwingHand, extraSwingAction, true
                  );

                  BlockPos extraRotationPosition = this.extraBreakTarget.getPosition();

                  this.rotateToBlock(extraRotationPosition);

                  BlockPos extraAttemptPosition = this.extraBreakTarget.getPosition();
                  this.recordBreakAttempt(extraAttemptPosition);

                  BlockPos extraEffectPosition = this.extraBreakTarget.getPosition();

                  this.playBreakEffects(extraEffectPosition);

                  this.extraBreakTimer.reset();

                  ClientContext.worldStateManager.markExtraBreak(this.extraBreakTarget.getPosition());
                  AutoMineMiningTracker miningTracker = autoMineModule.miningTracker;

                  BlockPos trackedExtraPosition = this.extraBreakTarget.getPosition();
                  miningTracker.rememberPosition(trackedExtraPosition);
                  this.extraBreakTarget = null;
                  TaskScheduler extraSwapScheduler = ClientContext.taskScheduler;

                  // Restore the same swap mechanism after the extra break has been sent.
                  Runnable restoreExtraSlot = () -> {
                     if (useExtraInventorySwap) {
                        InventoryUtils.swapIntoSelectedSlot(extraToolSlot);
                     } else {
                        PacketSender.sendSelectedSlot(extraPreviousSlot);
                        LocalPlayer player = ClientAccess.zioMinecraft.player;
                        Inventory inventory = player.getInventory();
                        inventory.setSelectedSlot(extraPreviousSlot);
                     }
                     swapPending.set(false);
                  };

                  extraSwapScheduler.scheduleRotationTask(restoreExtraSlot, 2);
                  return;
               }
            }
         }
      }

      boolean lastBreakWasShulker = this.extraBreakTracker.getLastBlock() instanceof ShulkerBoxBlock;

      boolean blockedByContainer = RotationManager.is2b2tMode()
         && ClientAccess.zioMinecraft.screen instanceof AbstractContainerScreen;
      currentMiningPosition = this.miningPos;

      boolean canContinueMining = WorldUtils.canMineBlock(currentMiningPosition);
      if (((this.asyncSetting.getValue())) && !blockedByContainer && !lastBreakWasShulker) {
         canContinueMining = true;
      }

      if (this.miningPos == null) {
         canContinueMining = false;
      }

      currentMiningPosition = this.miningPos;

      if (!(WorldUtils.canMineBlock(currentMiningPosition))) {
         BlockPos aboveCheckPosition = this.getMiningPos();

         if ((this.isPlayerAboveBlock(aboveCheckPosition))) {
            canContinueMining = false;
         }
      }

      // Normal auto-swap keeps the chosen tool until mining can no longer continue.
      if (this.previousSelectedSlot != -1 && !canContinueMining) {
         if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NORMAL) {
            if (this.swapBackSetting.getValue()) {
               int previousSlot = this.previousSelectedSlot;

               InventoryUtils.selectHotbarSlotForPlacement(previousSlot);
               this.previousSelectedSlot = -1;
               return;
            }
         }
      }

      if (!(this.restartForRebreak())) {
         if (this.miningPos != null) {
            AnchorAuraModule anchorAura = anchorAuraModule;
            BlockPos anchorPosition = this.miningPos;

            if ((anchorAura.isManagedAnchor(anchorPosition))) {
               return;
            }
         }

         LocalPlayer spectatorCheckPlayer = ClientAccess.zioMinecraft.player;
         if (!(spectatorCheckPlayer.isSpectator())) {
            LocalPlayer creativeCheckPlayer = ClientAccess.zioMinecraft.player;
            if (!(creativeCheckPlayer.isCreative()) && canContinueMining) {
               boolean rotate = true;
               if ((this.limitSetting.getValue()) != 1.0F) {
                  float progress = this.getMiningProgress();
                  Float damage = this.damageSetting.getValue();
                  float fraction = progress / damage;
                  Float limit = this.limitSetting.getValue();
                  if (!(fraction >= limit)) {
                     rotate = false;
                  } else if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NONE) {
                     if (this.holdingBestSetting.getValue()) {
                        Inventory inventory = ClientAccess.zioMinecraft.player.getInventory();
                        if (toolSlot != inventory.getSelectedSlot()) {
                           rotate = false;
                        }
                     }
                  }
               }
               if (rotate) {
                  BlockPos rotationTarget = this.miningPos;
                  this.rotateToBlock(rotationTarget);
               }

               float progress = this.getMiningProgress();
               Float damageThreshold = this.damageSetting.getValue();
               if (progress < damageThreshold) {
                  return;
               }

               boolean alternativeAllowed = this.isAlternativeAllowed();
               float instantDelay = this.instantDelaySetting.getValue();
               if (alternativeAllowed) {
                  if ((RotationManager.is2b2tMode())) {
                     float minimumInstantDelay = 0.1F;
                     float configuredInstantDelay = instantDelay;
                     instantDelay = Math.max(configuredInstantDelay, minimumInstantDelay);
                  }
               }

               if ((this.rebreakSetting.getValue()).isInstant()) {
                  double instantDelaySeconds = instantDelay;
                  TimeUnit instantDelayUnit = TimeUnit.SECONDS;
                  ElapsedTimer instantDelayTimer = this.breakDelayTimer;
                  if (!(instantDelayTimer.hasElapsed(instantDelaySeconds, instantDelayUnit))) {
                     return;
                  }
               }

               if (!(this.isMainHandMultitask())
                  || alternativeAllowed) {
                  boolean holdingTool = toolSlot == InventoryUtils.getSelectedSlot();
                  if (((this.rebreakSetting.getValue()).isInstant())
                     && !holdingTool
                     && alternativeAllowed) {
                     ElapsedTimer itemSpawnDelay = this.itemSpawnTimer;

                     if (!(itemSpawnDelay.hasElapsed(150L))) {
                        return;
                     }
                  }

                  if (!(this.canBreakNearCrystal())) {
                     return;
                  }

                  int selectedSlot;
                  BlockState miningState;
                  Direction destroyFace;
                  boolean swappedTool = false;
                  {
                     selectedSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
                     BlockPos statePosition = this.miningPos;
                     miningState = ClientAccess.zioMinecraft.level.getBlockState(statePosition);
                     BlockPos facePosition = this.miningPos;

                     destroyFace = selectDestroyFace(facePosition);
                     if (this.autoSwapModeSetting.getValue() != SpeedMineAutoSwapChoice.NONE && toolSlot != -1) {
                        if (ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot() != toolSlot) {
                           if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT && alternativeAllowed) {
                              InventoryUtils.swapIntoSelectedSlot(
                                 toolSlot);
                           } else {
                              this.previousSelectedSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();

                              InventoryUtils.selectHotbarSlotForPlacement(
                                 toolSlot);
                           }
                           swappedTool = true;
                        }
                     }

                     if (!swappedTool && selectedSlot != toolSlot) {
                        if ((this.rebreakSetting.getValue()).isInstant()) {
                           if (this.holdingBestSetting.getValue()) {
                              return;
                           }
                        }
                     }
                  }

                  {
                     this.breakDelayTimer.reset();

                     this.itemSpawnTimer.reset();
                     SwingAction finishSwingAction = SwingAction.MINING;
                     InteractionHand finishSwingHand = InteractionHand.MAIN_HAND;
                     ClientContext.swingManager.swingPacket(finishSwingHand, finishSwingAction);
                     currentMiningPosition = this.miningPos;

                     boolean recordAttempt = WorldUtils.canMineBlock(currentMiningPosition);
                     if (!recordAttempt) {
                        Boolean asyncEnabled = this.asyncSetting.getValue();
                        recordAttempt = asyncEnabled;
                     }

                     if (recordAttempt) {
                        BlockPos attemptedPosition = this.miningPos;
                        this.recordBreakAttempt(attemptedPosition);
                     }
                  }

                  currentMiningPosition = this.miningPos;

                  if ((WorldUtils.canMineBlock(currentMiningPosition))) {
                     if (this.sneakExtendSetting.getValue()) {
                        if ((ClientAccess.zioMinecraft.player.isShiftKeyDown())) {
                           WorldStateManager predictedWorldState = ClientContext.worldStateManager;
                           BlockPos predictedPosition = this.miningPos;

                           predictedWorldState.destroyBlockPredicted(predictedPosition);
                        }
                     }
                  }

                  if (!(RotationManager.isMovementSyncEnabled())) {
                     SpeedMineRebreakChoice rebreakMode = this.rebreakSetting.getValue();
                     if (!(rebreakMode.isInstant())) {
                        double playerX = ClientAccess.zioMinecraft.player.getX();
                        double playerY = ClientAccess.zioMinecraft.player.getY();
                        double playerZ = ClientAccess.zioMinecraft.player.getZ();
                        boolean packetOnGround = ClientAccess.zioMinecraft.player.onGround();
                        PacketSender.sendPosition(
                           playerX, playerY, playerZ, packetOnGround
                        );
                     }
                  }

                  BlockPos brokenPosition = this.miningPos;
                  SpeedMineRebreakTracker tracker = this.rebreakTracker;
                  BlockPos trackedPosition = this.miningPos;

                  tracker.startTracking(trackedPosition);
                  if ((this.waitingForRebreak)) {
                     BlockPos restartPosition = this.miningPos;
                     Action restartAction = Action.START_DESTROY_BLOCK;
                     PacketSender.sendPlayerAction(
                        restartAction, restartPosition, destroyFace
                     );
                  }

                  if (!(miningState.isAir())) {
                     if ((this.stalledBreakTimer.hasElapsedAndReset(200L))) {
                        BlockPos effectPosition = this.miningPos;

                        this.playBreakEffects(effectPosition);
                     }
                  }

                  // Record the stop as unconfirmed until a block update arrives or the tracking window expires.
                  BlockPos stopPosition = this.miningPos;
                  Action stopAction = Action.STOP_DESTROY_BLOCK;
                  PacketSender.sendPlayerAction(stopAction, stopPosition, destroyFace);
                  WorldStateManager unconfirmedWorldState = ClientContext.worldStateManager;
                  BlockPos unconfirmedPosition = this.miningPos;

                  unconfirmedWorldState.markUnconfirmedBreak(unconfirmedPosition);
                  if ((this.rebreakSetting.getValue()).isFast()) {
                     BlockPos abortPosition = this.miningPos;
                     Action abortAction = Action.ABORT_DESTROY_BLOCK;
                     PacketSender.sendPlayerAction(
                        abortAction, abortPosition, destroyFace
                     );
                     BlockPos rebreakStopPosition = this.miningPos;
                     Action rebreakStopAction = Action.STOP_DESTROY_BLOCK;
                     PacketSender.sendPlayerAction(
                        rebreakStopAction, rebreakStopPosition, destroyFace
                     );
                     BlockPos rebreakStartPosition = this.miningPos;
                     Action rebreakStartAction = Action.START_DESTROY_BLOCK;
                     PacketSender.sendPlayerAction(
                        rebreakStartAction, rebreakStartPosition, destroyFace
                     );
                     this.miningPos = null;
                  }

                  // Silent swaps must be undone after the final stop, including the delayed 1.21 stop.
                  Setting<SpeedMineAutoSwapChoice> silentSwapSetting = this.autoSwapModeSetting;
                  if (silentSwapSetting.getValue() == SpeedMineAutoSwapChoice.SILENT && toolSlot != -1) {
                     boolean needsExtraTick = this.silentSwapProgress < 0.425F;

                     int slotToRestore = selectedSlot;

                     Runnable restoreSlotTask = () -> {
                        if (alternativeAllowed && this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT) {
                           InventoryUtils.swapIntoSelectedSlot(toolSlot);
                        } else if (slotToRestore != toolSlot || this.value121Setting.getValue() == SpeedMineValue121Choice.TICK) {
                           InventoryUtils.selectHotbarSlotForPlacement(slotToRestore);
                        }
                        autoCrystalModule.placementGate.set(false);
                     };
                     if (needsExtraTick && this.value121Setting.getValue() == SpeedMineValue121Choice.TICK
                        && WorldUtils.canMineBlock(brokenPosition)) {
                        swapPending.set(true);
                        TaskScheduler scheduler = ClientContext.taskScheduler;
                        Runnable delayedBreak = () -> {
                           PacketSender.sendPlayerAction(Action.STOP_DESTROY_BLOCK, brokenPosition, destroyFace);
                           WorldStateManager worldState = ClientContext.worldStateManager;
                           worldState.destroyBlockPredicted(brokenPosition);
                           swapPending.set(false);
                           restoreSlotTask.run();
                        };
                        scheduler.scheduleRotationTask(delayedBreak, 1);
                     } else {
                        restoreSlotTask.run();
                     }
                  }

                  boolean resetAfterBreak = true;
                  if ((this.rebreakSetting.getValue()).isInstant()) {
                     if (!(miningState.getBlock() instanceof ShulkerBoxBlock)) {
                        resetAfterBreak = false;
                     } else {
                        WorldStateManager worldState = ClientContext.worldStateManager;
                        BlockPos miningPosition = this.miningPos;
                        if (!worldState.hasKnownPlacement(miningPosition)) {
                           resetAfterBreak = false;
                        }
                     }
                  }
                  if (resetAfterBreak) {
                     reset(this);
                  }

                  if (this.rotateSetting.getValue()) {
                     if ((RotationManager.isSilentRotationMode())) {
                        if ((WorldUtils.canMineBlock(brokenPosition))) {
                           RotationManager rotationManager = ClientContext.rotationManager;
                           Vec3 blockCenter = brokenPosition.getCenter();

                           float[] rotations = RotationManager.getRotationsToPosition(
                              blockCenter);

                           rotationManager.requestRotation(rotations, 150);
                        }
                     }
                  }

                  return;
               }

               return;
            }
         }
      }

   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (event.getStage() == EventStage.Pre
         && this.miningPos != null) {
         BlockPos miningPosition = this.miningPos;
         if (!this.isInMiningRange(miningPosition)) {
            clearRebreakAndReset(this);
         }
      }
   }

   private void recordBreakAttempt(BlockPos blockPos) {
      if (WorldUtils.canMineBlock(blockPos)) {
         this.crystalDelayTimer.reset();
      }

      this.extraBreakTracker.trackBreak(blockPos);
   }

   private void sendGrimStartPacket(BlockPos blockPos, Direction direction) {
      if (this.grimSetting.getValue()) {
         Action startAction = Action.START_DESTROY_BLOCK;

         BlockPos offsetPosition = blockPos.above(1420);
         PacketSender.sendPlayerAction(startAction, offsetPosition, direction);
      }
   }

   public static void bindModules(ModuleRegistry moduleRegistry) {
      autoExpModule = (AutoExpModule)moduleRegistry.getModule(
         AutoExpModule.class
      );
      autoCrystalModule = (AutoCrystalModule)moduleRegistry.getModule(
         AutoCrystalModule.class
      );
      autoMineModule = (AutoMineModule)moduleRegistry.getModule(
         AutoMineModule.class
      );
      noGlitchBlocksModule = (NoGlitchBlocksModule)moduleRegistry.getModule(
         NoGlitchBlocksModule.class
      );
      itemSaverModule = (ItemSaverModule)moduleRegistry.getModule(
         ItemSaverModule.class
      );
      logoutSpotsModule = (LogoutSpotsModule)moduleRegistry.getModule(
         LogoutSpotsModule.class
      );
      multiTaskModule = (MultiTaskModule)moduleRegistry.getModule(
         MultiTaskModule.class
      );
      anchorAuraModule = (AnchorAuraModule)moduleRegistry.getModule(
         AnchorAuraModule.class
      );
   }

   private SpeedMineModule.InstantBreakResult evaluateInstantBreak(BlockPos blockPos) {
      if (SpeedMineModule.noGlitchBlocksModule.isToggled()) {
         if (SpeedMineModule.noGlitchBlocksModule.breakSetting.getValue()) {
            return new SpeedMineModule.InstantBreakResult(false, -1, false);
         }
      }

      BlockPos miningPosition = this.miningPos;

      int bestToolSlot = InventoryUtils.findBestToolSlot(
         miningPosition, this.alternativeSetting.getValue());
      int selectedSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
      ItemStack tool = ClientAccess.zioMinecraft.player.getInventory().getItem(bestToolSlot);
      BlockState state = ClientAccess.zioMinecraft.level.getBlockState(blockPos);
      boolean onGround = ClientAccess.zioMinecraft.player.onGround();
      double breakProgress = WorldUtils.getBreakProgress(tool, state, onGround);
      boolean holdingBestTool = bestToolSlot == selectedSlot;
      if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NONE && !holdingBestTool) {
         return new SpeedMineModule.InstantBreakResult(false, -1, false);
      }

      if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT) {
         if (this.value121Setting.getValue() != SpeedMineValue121Choice.OFF && !holdingBestTool) {
            return new SpeedMineModule.InstantBreakResult(false, -1, false);
         }
      }

      Float damageThreshold = this.damageSetting.getValue();
      if (breakProgress < damageThreshold.floatValue()) {
         return new SpeedMineModule.InstantBreakResult(false, -1, false);
      } else {
         return new SpeedMineModule.InstantBreakResult(true, bestToolSlot, breakProgress >= 1.0D);
      }
   }

   public SpeedMineModule() {
      super("SpeedMine", "Mines blocks silently.", ModuleCategory.PLAYER, new String[0]);
      this.blocksSetting = this.add(new RegistrySetSetting<>("Blocks", BuiltInRegistries.BLOCK, Blocks.OBSIDIAN, Blocks.ENDER_CHEST));
      this.modeSetting = this.add(new EnumSetting<>("Mode", ListFilterMode.ANY));
      this.damageSetting = this.add(new NumberSetting<>("Damage", 1.0F, 0.6F, 1.0F));
      this.rangeSetting = this.add(new NumberSetting<>("Range", 5.2F, 1.0F, 6.0F).withUnit("m"));
      this.grimSetting = this.add(new BooleanSetting("Grim", false));
      this.tpsSyncSetting = this.add(new BooleanSetting("TPSSync", true));
      this.noBreakDelaySetting = this.add(new BooleanSetting("NoBreakDelay", true));
      this.extraBreakSetting = this.add(new BooleanSetting("ExtraBreak", false).markVisibilityDependency());
      this.sneakExtendSetting = this.add(new BooleanSetting("SneakExtend", false).dependsOn(this.extraBreakSetting));
      this.rebreakEnabledSetting = this.add(new BooleanSetting("Rebreak", true).markAsGroup().markVisibilityDependency());
      this.rebreakSetting = this.add(new EnumSetting<>("Rebreak", SpeedMineRebreakChoice.NONE, this::isRebreakPageExpanded));
      this.asyncSetting = this.add(new BooleanSetting("Async", false, this::isInstantRebreak).dependsOn(this.rebreakEnabledSetting));
      this.instantDelaySetting = this.add(new NumberSetting<>("InstantDelay", 0.0F, 0.0F, 2.0F, this::isInstantRebreakPageExpanded).withUnit("s"));
      this.holdingBestSetting = this.add(new BooleanSetting("HoldingBest", false, this::isInstantRebreakPageExpanded));
      this.autoSwapSetting = this.add(new BooleanSetting("AutoSwap", true).markAsGroup().markVisibilityDependency());
      this.autoSwapModeSetting = this.add(new EnumSetting<>("AutoSwap", SpeedMineAutoSwapChoice.NONE, this::isAutoSwapPageExpanded));
      this.swapBackSetting = this.add(new BooleanSetting("SwapBack", false, this::isNormalSwap).dependsOn(this.autoSwapSetting));
      this.value121Setting = this.add(new EnumSetting<>("1.21", SpeedMineValue121Choice.OFF, this::isSilentSwapPageExpanded));
      this.alternativeSetting = this.add(new BooleanSetting("Alternative", false, this::isSilentSwapPageExpanded));
      this.swapResetSetting = this.add(new BooleanSetting("SwapReset", true, this::isSwapResetVisible));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", false).markVisibilityDependency());
      this.limitSetting = this.add(new NumberSetting<>("Limit", 0.9F, 0.0F, 1.0F, this::isRotatePageExpanded)
         .setBoundaryLabel("Off", SettingLabelCondition.MAX));
      this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.0F, 5.0F, this::isRenderPageExpanded));
      this.renderModeSetting = this.add(new EnumSetting<>("RenderMode", SpeedMineRenderModeChoice.OUT, this::isRenderPageExpanded));
      this.airSetting = this.add(new BooleanSetting("Air", false, this::isAirVisible));
      this.fadeSetting = this.add(new BooleanSetting("Fade", false, this::isRenderPageExpanded));
      this.colorModeSetting = this.add(new EnumSetting<>("ColorMode", SpeedMineColorModeChoice.PROGRESS, this::isRenderPageExpanded));
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(280861183, true), this::isCustomColorVisible));
      this.outlineSetting = this.add(new ColorSetting("Outline", new Color(-1044538881, true), this::isCustomColorVisible));
      this.abortSetting = this.add(new BooleanSetting("Abort", false, ignored -> false));
      this.rebreakTracker = new SpeedMineRebreakTracker(this);
      this.renderQueue = new SpeedMineRenderQueue();
      this.extraBreakTracker = new ExtraBreakTracker();
      this.pendingBreakPositions = new ArrayDeque<>();
      this.startDestroyRetryTimer = new ElapsedTimer();
      this.retargetTimer = new ElapsedTimer();
      this.breakDelayTimer = new ElapsedTimer();
      this.itemSpawnTimer = new ElapsedTimer();
      this.crystalDelayTimer = new ElapsedTimer();
      this.extraBreakTimer = new ElapsedTimer();
      this.stalledBreakTimer = new ElapsedTimer();
      this.bestToolSlot = -1;
      this.extraBreakTarget = null;
      this.crystalAttackPending = false;
      this.blocksSetting.addVisibilityPredicate(ignored -> (this.modeSetting.getValue()).isFiltering());
      this.rebreakEnabledSetting.setConfigName("RebreakPage");
      this.autoSwapSetting.setConfigName("SwapPage");
      this.abortSetting.setChangeCallback(() -> {
         if (!this.isWorldUnavailable() && this.abortSetting.getValue()) {
            reset(this);
            this.abortSetting.setValue(false);
         }
      });
   }

   private boolean isRebreakPageExpanded(Object ignored) {
      return this.rebreakEnabledSetting.isExpanded();
   }

   private boolean isInstantRebreak(Object ignored) {
      return (this.rebreakSetting.getValue()).isInstant();
   }

   private boolean isInstantRebreakPageExpanded(Object ignored) {
      return (this.rebreakSetting.getValue()).isInstant() && this.rebreakEnabledSetting.isExpanded();
   }

   private boolean isAutoSwapPageExpanded(Object ignored) {
      return this.autoSwapSetting.isExpanded();
   }

   private boolean isNormalSwap(Object ignored) {
      return this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NORMAL;
   }

   private boolean isSilentSwapPageExpanded(Object ignored) {
      return this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT && this.autoSwapSetting.isExpanded();
   }

   private boolean isSwapResetVisible(Object ignored) {
      return this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT && this.alternativeSetting.getValue() && this.autoSwapSetting.isExpanded();
   }

   private boolean isRotatePageExpanded(Object ignored) {
      return this.rotateSetting.isExpanded();
   }

   private boolean isRenderPageExpanded(Object ignored) {
      return this.renderSetting.isExpanded();
   }

   private boolean isAirVisible(Object ignored) {
      return this.renderSetting.isExpanded() && (this.rebreakSetting.getValue()).isInstant();
   }

   private boolean isCustomColorVisible(Object ignored) {
      if (this.renderSetting.isExpanded()) {
         return this.colorModeSetting.getValue() == SpeedMineColorModeChoice.CUSTOM;
      }
      return false;
   }

   record InstantBreakResult(
      boolean canInstantBreak,
      int toolSlot,
      boolean breaksInOneTick
   ) {
   }
}
