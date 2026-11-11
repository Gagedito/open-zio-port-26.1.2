/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;


import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.StepHeightEvent;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.managers.TickRateController;
import dev.zio.client.managers.HoleManager;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

public class StepModule extends Module {
   private static HoleSnapModule holeSnap;
   final Setting<ModeChoice> modeSetting;
   private final Setting<Float> heightSetting;
   private final Setting<Boolean> safeDisableSetting;
   final Setting<Boolean> useTimerSetting;
   private final Setting<Float> timerSpeedSetting;
   private boolean usingStepTimer;
   private int stepCount;

   @Override
   public String getInfo() {
      return FontRenderer.capitalize(this.modeSetting.getValue());
   }

   @Override
   public void onDisable() {
      this.usingStepTimer = false;
      this.stepCount = 0;
   }

   @EventHandler(priority = -100)
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
      if (this.stepCount <= 0) {
         return;
      }
      HoleManager holes = ClientContext.holeManager;
      BlockPos position = PlayerStateUtils.getLocalRoundedBlockPosition();
      if (!holes.intersectsHole(position)) {
         return;
      }
      if (this.safeDisableSetting.getValue()) {
         this.disable();
      }
   }

   @EventHandler
   private void onStepHeight(StepHeightEvent event) {
      if (holeSnap.shouldPauseStep()) {
         return;
      }
      if (!ClientAccess.zioMinecraft.player.onGround() || PlayerStateUtils.getTrackedFallDistance() > 0.0F
         || !ClientAccess.zioMinecraft.player.verticalCollision) {
         TickRateController timer = ClientContext.tickRateController;
         timer.releaseMultiplier(this);
         return;
      }
      if (EventStage.Pre == event.getStage()) {
         Setting<Float> height = this.heightSetting;
         float requestedHeight = height.getValue();
         event.setHeight(requestedHeight);
      }

      if (event.getStage() != EventStage.Post) {
         return;
      }

      double actualHeight = event.getHeight();
      if (actualHeight <= 0.6D) {
         TickRateController timer = ClientContext.tickRateController;
         timer.releaseMultiplier(this);
         return;
      }

      if (this.modeSetting.getValue() == StepModule.ModeChoice.VANILLA) {
         this.stepCount++;
         return;
      }

      ArrayList<Double> offsets = new ArrayList<>();
      if (this.useTimerSetting.getValue()) {
         if (!PlayerStateUtils.isInCobweb(ClientAccess.zioMinecraft.player)) {
            TickRateController timer = ClientContext.tickRateController;
            float requestedSpeed = this.timerSpeedSetting.getValue();
            timer.requestMultiplier(this, requestedSpeed);
            this.usingStepTimer = true;
         }
      }

      this.stepCount++;
      float height = event.getHeight();
      if (height <= 1.0F) {
         offsets.addAll(List.of(0.42D * height, 0.75D * height));
      } else if (height <= 1.5D) {
         offsets.addAll(List.of(0.42D, 0.75D, 1.0D, 1.16D, 1.23D, 1.2D));
      } else if (height <= this.heightSetting.getValue()) {
         offsets.addAll(List.of(0.42D, 0.78D, 0.63D, 0.51D, 0.9D, 1.21D, 1.45D, 1.43D));
      }

      if (offsets.isEmpty()) {
         return;
      }
      for (Double offset : offsets) {
         double x = ClientAccess.zioMinecraft.player.getX();
         double baseY = ClientAccess.zioMinecraft.player.getY();
         double y = baseY + offset;
         double z = ClientAccess.zioMinecraft.player.getZ();
         boolean onGround = ClientAccess.zioMinecraft.player.onGround();
         PacketSender.sendPosition(x, y, z, onGround);
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.usingStepTimer = false;
   }

   public boolean isUsingTimer() {
      return this.usingStepTimer;
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      holeSnap = (HoleSnapModule)registry.getModule(HoleSnapModule.class);
   }

   public StepModule() {
      super("Step", "Allows you to step up blocks without jumping.", ModuleCategory.MOVEMENT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", StepModule.ModeChoice.VANILLA));
      this.heightSetting = this.add(new NumberSetting<>("Height", 2.0F, 1.0F, 4.0F).withUnit("m"));
      this.safeDisableSetting = this.add(new BooleanSetting("SafeDisable", false));
      Predicate<Object> timerVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.NORMAL;
      this.useTimerSetting = this.add(new BooleanSetting("UseTimer", true, timerVisibility).markVisibilityDependency());
      Predicate<Object> timerSpeedVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.NORMAL && this.useTimerSetting.isExpanded();
      this.timerSpeedSetting = this.add(new NumberSetting<>("TimerSpeed", 0.3F, 0.01F, 1.0F, timerSpeedVisibility));
      this.stepCount = 0;
   }

   public enum ModeChoice implements NamedEntry {
      VANILLA("Vanilla"),
      NORMAL("Normal");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
