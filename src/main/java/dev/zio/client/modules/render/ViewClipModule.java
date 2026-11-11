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
import dev.zio.client.ClientContext;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.MouseHandlerOnScrollEvent;
import dev.zio.client.utils.ClampedInterpolatedFloat;
import dev.zio.client.utils.FrameTiming;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

public class ViewClipModule extends Module {
   public final Setting<SkipChoice> skipSetting;
   public final Setting<Float> rangeSetting;
   public final Setting<Boolean> smoothSetting;
   public final Setting<Boolean> scrollSetting;
   public final ClampedInterpolatedFloat clipAnimation;
   private float scrollTargetOffset;
   private float scrollOffset;

   public float getCameraDistance() {
      float distance = (this.rangeSetting.getValue()) + this.scrollOffset;
      return Mth.clamp(distance, 0.1F, 1e+01F);
   }

   @EventHandler
   private void onMouseHandlerOnScrollEvent(MouseHandlerOnScrollEvent event) {
      Boolean enabled = this.scrollSetting.getValue();
      if (!enabled) {
         return;
      }
      Camera camera = ClientAccess.zioMinecraft.gameRenderer.getMainCamera();
      if (!camera.isDetached()) {
         return;
      }
      if (!KeyUtils.isPhysicalKeyDown(341)) {
         return;
      }
      float currentTarget = this.scrollTargetOffset;
      float nextTarget = currentTarget - (float)event.getVerticalOffset();
      Float minimumRangeValue = this.rangeSetting.getValue();
      float minimumOffset = -minimumRangeValue;
      float maximum = (this.rangeSetting.getMaximumValue());
      Float currentRangeValue = this.rangeSetting.getValue();
      float maximumOffset = maximum - currentRangeValue;
      this.scrollTargetOffset = Mth.clamp(nextTarget, minimumOffset, maximumOffset);
      event.cancel();
   }

   public boolean shouldSkipCameraType(CameraType cameraType) {
      if (!this.isToggled()) {
         return false;
      }
      Setting<SkipChoice> skip = this.skipSetting;
      if (skip.getValue() == ViewClipModule.SkipChoice.NONE) {
         return false;
      }
      return this.skipSetting.getValue() == ViewClipModule.SkipChoice.FRONT
         ? cameraType == CameraType.THIRD_PERSON_FRONT
         : cameraType == CameraType.THIRD_PERSON_BACK;
   }

   @EventHandler
   private void onRenderFrame(WorldRenderEvent.Main event) {
      FrameTiming timing = ClientContext.frameTiming;
      float frameFactor = timing.getFrameFactor(1.0F);
      float currentOffset = this.scrollOffset;
      float difference = this.scrollTargetOffset - this.scrollOffset;
      double exponent = -1e+01F * frameFactor;
      this.scrollOffset = currentOffset + difference * (1.0F - (float)Math.exp(exponent));
      Camera camera = ClientAccess.zioMinecraft.gameRenderer.getMainCamera();
      if (camera.isDetached()) {
         ClampedInterpolatedFloat animation = this.clipAnimation;
         animation.setTarget(true);
      } else {
         ClampedInterpolatedFloat animation = this.clipAnimation;
         animation.setValue(0.0F);
         this.scrollTargetOffset = this.scrollOffset = 0.0F;
      }
   }

   public ViewClipModule() {
      super(
         "ViewClip",
         "Allows you to clip into blocks using 3rd person camera.",
         ModuleCategory.RENDER,
         new String[0]
      );
      this.skipSetting = this.add(new EnumSetting<>(new String("Skip"), ViewClipModule.SkipChoice.NONE));
      this.rangeSetting = this.add(new NumberSetting<>(new String("Range"), 4.5F, 0.1F, 10.0F));
      this.smoothSetting = this.add(new BooleanSetting(new String("Smooth"), true));
      this.scrollSetting = this.add(new BooleanSetting(new String("Scroll"), false));

      this.clipAnimation = new ClampedInterpolatedFloat(1.3F, true);

      this.setDrawn(false);
   }

   public enum SkipChoice implements NamedEntry {
      NONE("None"),
      FRONT("Front"),
      BACK("Back");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      SkipChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
