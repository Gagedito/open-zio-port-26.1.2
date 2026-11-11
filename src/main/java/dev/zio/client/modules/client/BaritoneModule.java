/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.client;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.MinecraftTickEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.PermanentModule;
import dev.zio.client.modules.movement.SafeWalkModule;
import dev.zio.client.modules.movement.StepModule;
import dev.zio.client.modules.player.AutoToolModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.Setting;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import java.awt.Color;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class BaritoneModule extends PermanentModule {
   private static StepModule step;
   private static AutoToolModule autoTool;
   private static SafeWalkModule safeWalk;
   private final Setting<Boolean> freeLookSetting;
   private final Setting<Boolean> backFillSetting;
   private final Setting<Boolean> logOnArrivalSetting;
   private final Setting<Boolean> assumeEnabledSetting;
   private final Setting<Boolean> censorCoordsSetting;
   private final Setting<Boolean> replaceColorsSetting;
   private final Setting<Color> colorSetting;
   private final Setting<Color> goalSetting;
   private List<AssumedSetting> assumedSettings;

   static {

      step = ClientContext.getModule(StepModule.class);
      autoTool = ClientContext.getModule(AutoToolModule.class);
      safeWalk = ClientContext.getModule(SafeWalkModule.class);
   }

   private List<AssumedSetting> getAssumedSettings() {
      if (this.assumedSettings == null) {
         this.assumedSettings = AssumedSetting.create(step, autoTool, safeWalk);
      }

      return this.assumedSettings;
   }

   @EventHandler
   private void onMinecraftTick(MinecraftTickEvent event) {
      if (!ClientContext.baritoneBridge.isAvailable()) {
         return;
      }
      Settings settings = BaritoneAPI.getSettings();

      Settings.Setting targetSetting = settings.disconnectOnArrival;
      targetSetting.value = this.logOnArrivalSetting.getValue();
      targetSetting = settings.backfill;
      Setting<Boolean> backFillSetting = this.backFillSetting;
      targetSetting.value = backFillSetting.getValue();
      targetSetting = settings.freeLook;
      Setting<Boolean> freeLookSetting = this.freeLookSetting;
      targetSetting.value = freeLookSetting.getValue();
      targetSetting = settings.censorCoordinates;
      targetSetting.value = this.censorCoordsSetting.getValue();
      targetSetting = settings.censorRanCommands;
      targetSetting.value = this.censorCoordsSetting.getValue();
      Setting<Boolean> replaceColorsSetting = this.replaceColorsSetting;
      Boolean replaceColors = replaceColorsSetting.getValue();
      if (replaceColors) {
         Setting<Color> pathColorSetting = this.colorSetting;
         Color configuredPathColor = pathColorSetting.getValue();

         Color pathColor = ColorUtils.withAlpha(
            configuredPathColor, 255
         );
         Setting<Color> goalColorSetting = this.goalSetting;
         Color configuredGoalColor = goalColorSetting.getValue();

         Color goalColor = ColorUtils.withAlpha(
            configuredGoalColor, 255
         );
         settings.colorBestPathSoFar.value = pathColor;
         settings.colorGoalBox.value = goalColor;
         settings.colorInvertedGoalBox.value = goalColor;
         settings.colorCurrentPath.value = pathColor;
         settings.colorMostRecentConsidered.value = pathColor;
         settings.colorBlocksToBreak.value = pathColor;
         settings.colorBlocksToPlace.value = pathColor;
         settings.colorBlocksToWalkInto.value = pathColor;
         settings.colorNextPath.value = pathColor;
         targetSetting = settings.pathRenderLineWidthPixels;

         targetSetting.value = (1.5F);
         targetSetting = settings.goalRenderLineWidthPixels;

         targetSetting.value = (1.5F);
      }

      Setting<Boolean> assumeEnabledSetting = this.assumeEnabledSetting;
      Boolean assumeEnabled = assumeEnabledSetting.getValue();
      if (assumeEnabled) {
         this.getAssumedSettings().forEach(AssumedSetting::sync);
      }
   }

   public static void bindModuleDependencies(ModuleRegistry registry) {
      step = (StepModule)registry.getModule(
         StepModule.class
      );
      autoTool = (AutoToolModule)registry.getModule(
         AutoToolModule.class
      );
      safeWalk = (SafeWalkModule)registry.getModule(
         SafeWalkModule.class
      );
   }

   public BaritoneModule() {
      super("Baritone", "Manages baritone settings.", ModuleCategory.CLIENT, new String[0]);
      this.freeLookSetting = this.add(new BooleanSetting(("FreeLook"), true));
      this.backFillSetting = this.add(new BooleanSetting("BackFill", false));
      this.logOnArrivalSetting = this.add(new BooleanSetting("LogOnArrival", false));
      this.assumeEnabledSetting = this.add(new BooleanSetting("AssumeEnabled", false));
      this.censorCoordsSetting = this.add(new BooleanSetting("CensorCoords", false));
      BooleanSetting replaceColorsSetting = new BooleanSetting("ReplaceColors", true);
      replaceColorsSetting.markVisibilityDependency();
      this.replaceColorsSetting = this.add(replaceColorsSetting);
      this.colorSetting = this.add(new ColorSetting(("Color"), new Color(-4351489, true)).dependsOn(this.replaceColorsSetting));
      this.goalSetting = this.add(new ColorSetting("Goal", new Color(-4351489, true)).dependsOn(this.replaceColorsSetting));
      if (ClientContext.baritoneBridge.isAvailable()) {
         Settings.Setting chatControlSetting = BaritoneAPI.getSettings().chatControl;
         chatControlSetting.value = false;
      }
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   private static class AssumedSetting {
      private final Function<Settings, Settings.Setting<Boolean>> settingSelector;
      private final Supplier<Boolean> moduleEnabled;
      private boolean lastEnabledValue;

      private static List<AssumedSetting> create(StepModule step, AutoToolModule autoTool, SafeWalkModule safeWalk) {
         return List.of(
            new AssumedSetting(settings -> settings.assumeStep, step::isToggled),
            new AssumedSetting(settings -> settings.assumeExternalAutoTool, autoTool::isToggled),
            new AssumedSetting(settings -> settings.assumeSafeWalk, safeWalk::isToggled)
         );
      }

      private AssumedSetting(Function<Settings, Settings.Setting<Boolean>> settingSelector, Supplier<Boolean> moduleEnabled) {
         this.settingSelector = settingSelector;
         this.moduleEnabled = moduleEnabled;
         this.lastEnabledValue = this.getSetting().value;
      }

      public void sync() {
         boolean enabled = this.moduleEnabled.get();
         if (enabled) {
            this.getSetting().value = true;
            this.lastEnabledValue = true;
         } else if (this.lastEnabledValue) {
            this.getSetting().value = false;
            this.lastEnabledValue = false;
         }
      }

      private Settings.Setting<Boolean> getSetting() {
         return this.settingSelector.apply(BaritoneAPI.getSettings());
      }

   }
}
