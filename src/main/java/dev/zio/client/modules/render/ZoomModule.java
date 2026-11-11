/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.events.MouseHandlerOnScrollEvent;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.utils.FloatAnimation;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;

public class ZoomModule extends Module {
   private final Setting<Boolean> smoothSetting;
   protected final Setting<Boolean> smoothCameraSetting;
   private final Setting<Boolean> scrollSetting;
   private final Setting<Integer> amountSetting;
   private final ZoomOptionsUpdater optionsUpdater;
   protected int baselineFov;
   protected boolean savedSmoothCamera;
   protected boolean fovUpdateActive;
   private final FloatAnimation zoomAnimation;
   private final FloatAnimation scrollAnimation;

   protected int calculateFov() {
      float baseline = this.baselineFov;
      Integer divisor = this.amountSetting.getValue();
      int fovDifference = 75 / divisor - this.baselineFov;
      float distance = Math.abs(fovDifference);
      int fov = (int)(baseline - distance * this.getZoomBlend());
      Boolean scrollEnabled = this.scrollSetting.getValue();
      if (!scrollEnabled) {
         return fov;
      }
      float adjustedFov = fov;
      FloatAnimation animation = this.scrollAnimation;
      adjustedFov -= animation.getValue();
      return (int)Mth.clamp(adjustedFov, 1e+01F, 1.5e+02F);
   }

   public float getZoomBlend() {
      FloatAnimation animation = this.zoomAnimation;
      double progress = 1.0F - animation.getValue();
      return (float)(1.0D - Math.pow(progress, 4.0D));
   }

   @Override
   public void onEnable() {
      if (this.isWorldUnavailable()) {
         this.disable();
         return;
      }
      EventBus bus = ClientAccess.eventBus;
      ZoomOptionsUpdater updater = this.optionsUpdater;
      bus.subscribe(updater);
      if (!this.fovUpdateActive) {
         Options options = ClientAccess.zioMinecraft.options;
         OptionInstance fov = options.fov();
         this.baselineFov = ((Integer)fov.get());
      }
      FloatAnimation animation = this.zoomAnimation;
      long duration = this.getSmoothingDuration(200);
      animation.setTarget(1.0F, duration);
      this.savedSmoothCamera = ClientAccess.zioMinecraft.options.smoothCamera;
   }

   @Override
   public void onDisable() {
      FloatAnimation primaryAnimation = this.zoomAnimation;
      long primaryDuration = this.getSmoothingDuration(100);
      primaryAnimation.setTarget(0.0F, primaryDuration);
      FloatAnimation scroll = this.scrollAnimation;
      long scrollDuration = this.getSmoothingDuration(100);
      scroll.setTarget(0.0F, scrollDuration);
      ClientAccess.zioMinecraft.options.smoothCamera = this.savedSmoothCamera;
   }

   private int getSmoothingDuration(int duration) {
      return !(this.smoothSetting.getValue()) ? 0 : duration;
   }

   @EventHandler
   private void onMouseHandlerOnScrollEvent(MouseHandlerOnScrollEvent event) {
      if (event.getVerticalOffset() == 0.0) {
         return;
      }
      Boolean enabled = this.scrollSetting.getValue();
      if (!enabled) {
         return;
      }
      float baseline = this.baselineFov;
      Integer divisor = this.amountSetting.getValue();
      int fovDifference = 75 / divisor - this.baselineFov;
      float distance = Math.abs(fovDifference);
      int fov = (int)(baseline - distance * this.getZoomBlend());
      double currentValue = this.scrollAnimation.getValue();
      double delta = event.getVerticalOffset();
      float target = (float)(currentValue + delta * 10.0D);
      float minimum = fov - this.baselineFov;
      float maximum = fov - 10;
      int clampedTarget = (int)Mth.clamp(target, minimum, maximum);
      FloatAnimation targetScroll = this.scrollAnimation;
      float scrollTarget = clampedTarget;
      long duration = this.getSmoothingDuration(100);
      targetScroll.setTarget(scrollTarget, duration);
      event.cancel();
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (this.isWorldUnavailable()) {
         this.disable();
      }
   }

   public ZoomModule() {
      super(
         "Zoom",
         "Zooms your camera in.",
         ModuleCategory.RENDER,
         new String[0]
      );
      this.smoothSetting = this.add(new BooleanSetting("Smooth", true));
      this.smoothCameraSetting = this.add(new BooleanSetting("SmoothCamera", true));
      this.scrollSetting = this.add(new BooleanSetting("Scroll", false));
      String amountKey = new String("Amount");
      this.amountSetting = this.add(new NumberSetting<>(amountKey, 2, 2, 5));
      this.optionsUpdater = new ZoomOptionsUpdater(this);
      this.zoomAnimation = new FloatAnimation();
      this.scrollAnimation = new FloatAnimation();
   }
}
