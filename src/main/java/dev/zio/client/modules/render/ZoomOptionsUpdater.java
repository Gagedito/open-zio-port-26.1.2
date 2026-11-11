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
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.render.GameOptionsOverrides;
import dev.zio.client.settings.Setting;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

public class ZoomOptionsUpdater implements ClientAccess {
   private final ZoomModule zoomModule;

   public ZoomOptionsUpdater(ZoomModule zoomModule) {
      this.zoomModule = zoomModule;
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      ZoomModule fovModule = this.zoomModule;
      int fov = fovModule.calculateFov();
      OptionInstance fovOption = ClientAccess.zioMinecraft.options.fov();
      Integer fovValue = fov;
      GameOptionsOverrides.forceSetOptionValue(fovOption, fovValue);
      Options cameraOptions = ClientAccess.zioMinecraft.options;
      ZoomModule toggleModule = this.zoomModule;
      boolean smoothCamera = false;
      if (toggleModule.isToggled()) {
         Setting<Boolean> smoothCameraSetting = this.zoomModule.smoothCameraSetting;
         smoothCamera = (smoothCameraSetting.getValue());
      }
      cameraOptions.smoothCamera = smoothCamera;
      if (fov >= this.zoomModule.baselineFov) {
         ZoomModule currentToggleModule = this.zoomModule;
         if (!currentToggleModule.isToggled()) {
            EventBus bus = ClientAccess.eventBus;
            bus.unsubscribe(this);
            this.zoomModule.fovUpdateActive = false;
            return;
         }
      }
      this.zoomModule.fovUpdateActive = true;
   }

}
