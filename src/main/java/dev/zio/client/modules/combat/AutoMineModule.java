/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.utils.inventory.AutoMineProviderBase;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.utils.inventory.AutoMineTerrainProvider;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.inventory.AutoMineFeetProvider;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.inventory.AutoMineHeadProvider;
import dev.zio.client.utils.inventory.AutoMineCandidates;
import dev.zio.client.utils.inventory.AutoMineProvider;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.exploit.MultiTaskModule;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.BlockSampleMode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class AutoMineModule extends Module {
   private static SpeedMineModule speedMine;
   private static AntiCheatModule antiCheat;
   private static MultiTaskModule multiTask;
   public final Setting<Boolean> strictDirectionSetting;
   public final Setting<Boolean> raytraceSetting;
   public final Setting<Boolean> rotateSetting;
   public final Setting<Boolean> ignoreNakedSetting;
   public final Setting<Boolean> assistantSetting;
   private final Setting<Boolean> targetsSetting;
   public final Setting<Boolean> burrowSetting;
   public final Setting<Boolean> fishSetting;
   public final Setting<Boolean> terrainSetting;
   public final Setting<Boolean> enderChestsSetting;
   public final Setting<Float> rangeSetting;
   public final Setting<Boolean> headSetting;
   public final Setting<Boolean> inCrawlSetting;
   public final Setting<Boolean> trapOverrideSetting;
   public final Setting<Boolean> feetSetting;
   public final Setting<Boolean> cornersSetting;
   public final Setting<Boolean> safeSetting;
   public final Setting<Boolean> selfSetting;
   public final Setting<Boolean> faceSetting;
   public final Setting<Boolean> onlyCrawlSetting;
   public final Setting<Boolean> tryCrawlSetting;
   public final Setting<Boolean> instantSetting;
   public final Setting<Boolean> downSetting;
   public final Setting<Boolean> downPrioritySetting;
   private final List<ScheduledMiningBreak> pendingBreaks;
   private final List<AutoMineProvider> providerStages;
   public final AutoMineTargetSelector targetSelector;
   public final AutoMineMiningTracker miningTracker;
   private boolean miningActive;

   private float getBreakDelayValue() {
      Setting<Boolean> noDelaySetting = speedMine.noBreakDelaySetting;

      if (!noDelaySetting.getValue()) {
         if (antiCheat.isMovementSyncEnabled()) {
            ElapsedTimer timerSnapshot = speedMine.getBreakDelayTimer();

            float normalizedElapsed = ((float)timerSnapshot.elapsedMillis()) / 300.0F;

            float remaining = 1.0F - Mth.clamp(normalizedElapsed, 0.0F, 1.0F);

            float scaled = remaining * 6.0F;

            return scaled + 1.5F;
         }
      }

      return 0.0F;
   }

   private boolean updateMiningState() {
      if (this.isMiningActive()) {
         if (speedMine.getMiningPos() == null) {
            this.setMiningActive(false);
         } else {
            BlockPos miningPosition = speedMine.getMiningPos();
            boolean blockPresent = !WorldUtils.isAir(miningPosition);

            this.setMiningActive(blockPresent);
         }
      }

      return this.isMiningActive();
   }


   public float getRange() {
      Setting<Float> rangeSettingSnapshot = speedMine.rangeSetting;

      return rangeSettingSnapshot.getValue();
   }


   public boolean isMiningActive() {
      return this.miningActive;
   }

   static {
      speedMine = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
      antiCheat = (AntiCheatModule)ClientContext.getModule(AntiCheatModule.class);
      multiTask = (MultiTaskModule)ClientContext.getModule(MultiTaskModule.class);
   }

   private Direction selectMiningDirection(BlockPos position) {
      if (this.strictDirectionSetting.getValue()) {
         List<Direction> directions = BlockInteractionUtils.getStrictDirections(position);
         return directions.isEmpty() ? null : directions.getFirst();
      } else {
         return Direction.UP;
      }
   }

   private void processPendingBreaks() {
      this.pendingBreaks.removeIf(
         (Predicate<AutoMineModule.ScheduledMiningBreak>)pending -> {
            if (pending.isReady()) {
               BlockPos positionSnapshot = pending.position();
               Direction directionSnapshot = pending.direction();
               boolean miningFlag = pending.miningFlag();

               this.startMining(positionSnapshot, directionSnapshot, miningFlag);
               return true;
            } else {
               return false;
            }
         }
      );
   }

   private void startMining(
      BlockPos position, Direction direction, boolean miningFlag
   ) {
      if (this.rotateSetting.getValue()) {
         Vec3 centerSnapshot = position.getCenter();

         float[] angles = RotationManager.getRotationsToPosition(centerSnapshot);

         if (this.raytraceSetting.getValue()) {
            BlockSampleMode samplingMode = BlockSampleMode.X8;

            Vec3 hitPoint = WorldUtils.findVisiblePoint(position, samplingMode);
            if (hitPoint != null) {
               angles = RotationManager.getRotationsToPosition(hitPoint);
            }
         }

         ClientContext.rotationManager.requestRotation(angles, 100);
      }

      SpeedMineModule.automatedMiningStart = miningFlag;

      ClientAccess.zioMinecraft.gameMode.startDestroyBlock(position, direction);
      SpeedMineModule.automatedMiningStart = false;
   }

   private void scheduleMining(
      BlockPos position, Direction direction, boolean miningFlag, float delay
   ) {
      if (delay == 0.0F) {
         this.startMining(position, direction, miningFlag);
      } else {
         this.pendingBreaks.add(new ScheduledMiningBreak(position, direction, miningFlag, this.getMiningDeadline(delay)));
      }
   }

   private long getMiningDeadline(float delay) {
      long now = System.currentTimeMillis();
      return now + (long)(50.0F * delay);
   }

   @EventHandler(priority = 300)
   private void onPlayerUpdate(RotationRequestEvent event) {
      if (!speedMine.isToggled()) {
         return;
      }

      if (multiTask.shouldPauseModule(this)) {
         return;
      }
      if (!this.pendingBreaks.isEmpty()) {
         this.processPendingBreaks();
         return;
      }

      AutoMineCandidates candidates = new AutoMineCandidates();
      Iterator<AutoMineProvider> providers = this.providerStages.iterator();

      while (providers.hasNext()) {
         AutoMineProvider provider = providers.next();
         if (provider.isEnabled()) {
            provider.collectCandidates(candidates);
         }
      }

      if (this.updateMiningState()) {
         int priority = candidates.getPriority();
         if (priority < 1500) {
            return;
         }
      }

      if (candidates.isMiningCancellationRequested()) {
         SpeedMineModule.clearRebreakAndReset(speedMine);
         return;
      }

      if (candidates.getPrimaryPosition() == null) {
         if (candidates.getSecondaryPosition() == null) {
            return;
         }

         BlockPos fallbackPosition = candidates.getSecondaryPosition();

         if (fallbackPosition.equals(this.miningTracker.getCurrentPosition())) {
            return;
         }

         candidates.swapPositions();
      }

      if (this.isExtraBreakEnabled()) {
         if (this.miningTracker.hasMiningActivity()) {
            int priority = candidates.getPriority();
            if (priority < 1500) {
               return;
            }
         }
      }

      BlockPos primary = candidates.getPrimaryPosition();

      BlockPos secondary = candidates.getSecondaryPosition();

      if (primary.equals(speedMine.getMiningPos())) {
         return;
      }

      Direction direction = this.selectMiningDirection(primary);
      if (direction == null) {
         return;
      }

      float primaryDelay = 0.0F;

      if (this.isExtraBreakEnabled()
         && secondary != null
         && !secondary.equals(primary)) {
         if (!this.miningTracker.hasMiningActivity()) {
            double rawSecondaryDelay = this.getBreakDelayValue();
            float secondaryDelay = (float)Math.ceil(rawSecondaryDelay);
            if (secondaryDelay > 0.0F) {
               SpeedMineModule.reset(speedMine);
            }

            this.scheduleMining(secondary, direction, false, secondaryDelay);

            primaryDelay += secondaryDelay + this.getBreakMode();
         }
      }

      this.scheduleMining(primary, direction, true, primaryDelay);
      return;
   }


   public void setMiningActive(boolean active) {
      this.miningActive = active;
   }


   public boolean hasAssistantBreakProgress(BlockPos position) {
      if (this.assistantSetting.getValue()) {
         if (ClientContext.blockBreakTracker.getBreakProgress(position) > 0.0F) {
            return true;
         }
      }

      return false;
   }

   private int getBreakMode() {
      if (antiCheat.isMovementSyncEnabled()) {
         Setting<Boolean> noDelaySetting = speedMine.noBreakDelaySetting;
         Boolean noBreakDelay = noDelaySetting.getValue();
         int modeValue;
         if (noBreakDelay) {
            modeValue = 2;
         } else {
            modeValue = 6;
         }

         return modeValue;
      } else {
         return this.isExtraBreakEnabled() ? 1 : 0;
      }
   }


   public boolean isExtraBreakEnabled() {
      Setting<Boolean> extraBreakSetting = speedMine.extraBreakSetting;

      return extraBreakSetting.getValue();
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      speedMine = (SpeedMineModule)modules.getModule(
         SpeedMineModule.class
      );
      antiCheat = (AntiCheatModule)modules.getModule(
         AntiCheatModule.class
      );
      multiTask = (MultiTaskModule)modules.getModule(
         MultiTaskModule.class
      );
      AutoMineProviderBase.bindModules(modules);
      AutoMineFeetProvider.bindModules(modules);
      AutoMineHeadProvider.bindModules(modules);
      AutoMineTerrainProvider.bindModules(modules);
      AutoMineMiningTracker.bindModules(modules);
   }

   private static String[] createAliases() {
      String[] aliases = new String[2];

      aliases[0] = "combatmine";

      aliases[1] = "autocity";
      return aliases;
   }

   public AutoMineModule() {
      super("AutoMine", "Mines out traps for your enemies and farms obsidian. \n"
         + String.valueOf(ChatFormatting.RED) + "Requires SpeedMine module enabled.", ModuleCategory.COMBAT, createAliases());

      this.strictDirectionSetting = this.add(new BooleanSetting("StrictDirection", false));
      this.raytraceSetting = this.add(new BooleanSetting("Raytrace", false));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", false));
      this.ignoreNakedSetting = this.add(new BooleanSetting("IgnoreNaked", false));
      this.assistantSetting = this.add(new BooleanSetting("Assistant", false));
      this.targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency());
      this.burrowSetting = this.add(new BooleanSetting("Burrow", true).dependsOn(this.targetsSetting));
      this.fishSetting = this.add(new BooleanSetting("Fish", false).dependsOn(this.targetsSetting));
      this.terrainSetting = this.add(new BooleanSetting("Terrain", false).dependsOn(this.targetsSetting));
      this.enderChestsSetting = this.add(new BooleanSetting("EnderChests", true).markVisibilityDependency().dependsOn(this.targetsSetting));

      this.rangeSetting = this.add(new NumberSetting<>("Range", 2.5F, 0.1F, 6.0F).dependsOn(this.targetsSetting, this.enderChestsSetting));
      this.headSetting = this.add(new BooleanSetting("Head", false).markVisibilityDependency().dependsOn(this.targetsSetting));
      this.inCrawlSetting = this.add(new BooleanSetting("InCrawl", false).dependsOn(this.targetsSetting, this.headSetting));
      this.trapOverrideSetting = this.add(new BooleanSetting("TrapOverride", false).dependsOn(this.targetsSetting, this.headSetting));
      this.feetSetting = this.add(new BooleanSetting("Feet", true).markVisibilityDependency().dependsOn(this.targetsSetting));
      this.cornersSetting = this.add(new BooleanSetting("Corners", true).dependsOn(this.targetsSetting, this.feetSetting));
      this.safeSetting = this.add(new BooleanSetting("Safe", true).dependsOn(this.targetsSetting, this.feetSetting));
      this.selfSetting = this.add(new BooleanSetting("Self", true).markVisibilityDependency().dependsOn(this.targetsSetting));
      this.faceSetting = this.add(new BooleanSetting("Face", false).dependsOn(this.targetsSetting, this.selfSetting));
      this.onlyCrawlSetting = this.add(new BooleanSetting("OnlyCrawl", false).dependsOn(this.targetsSetting, this.selfSetting));
      this.tryCrawlSetting = this.add(new BooleanSetting("TryCrawl", false).dependsOn(this.targetsSetting, this.selfSetting));
      this.instantSetting = this.add(new BooleanSetting("Instant", false).dependsOn(this.targetsSetting, this.selfSetting));
      this.downSetting = this.add(new BooleanSetting("Down", true).dependsOn(this.targetsSetting, this.selfSetting));
      this.downPrioritySetting = this.add(new BooleanSetting("DownPriority", false).dependsOn(this.targetsSetting, this.selfSetting, this.downSetting));
      this.pendingBreaks = new ArrayList<>();
      AutoMineEnderChestProvider enderChestProvider = new AutoMineEnderChestProvider(this);
      AutoMineBurrowProvider burrowProvider = new AutoMineBurrowProvider(this);
      AutoMineFishProvider fishProvider = new AutoMineFishProvider(this);
      AutoMineFeetProvider feetProvider = new AutoMineFeetProvider(this);
      AutoMineSelfProvider selfProvider = new AutoMineSelfProvider(this);
      AutoMineTerrainProvider terrainProvider = new AutoMineTerrainProvider(this);
      AutoMineHeadProvider headProvider = new AutoMineHeadProvider(this);
      this.providerStages = List.of(
         enderChestProvider, burrowProvider, fishProvider, feetProvider, selfProvider, headProvider, terrainProvider
      );
      this.targetSelector = new AutoMineTargetSelector(this);
      this.miningTracker = new AutoMineMiningTracker();
   }

   record ScheduledMiningBreak(
      BlockPos position,
      Direction direction,
      boolean miningFlag,
      long deadlineMillis
   ) {
      public boolean isReady() {
         return System.currentTimeMillis() >= this.deadlineMillis;
      }
   }
}
