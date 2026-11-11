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
import dev.zio.client.events.ScreenChangeEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.events.MouseHandlerOnScrollEvent;
import dev.zio.client.utils.InterpolatedFloat;
import dev.zio.client.utils.movement.EntityMotionAccess;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.render.GameOptionsOverrides;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.player.FreecamModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Vector2f;

public class FreeLookModule extends Module {

   private static FreecamModule freecamModule = (FreecamModule)ClientContext.getModule(FreecamModule.class);
   private final InterpolatedFloat cameraDistanceAnimation;
   private final Vector2f cameraRotation;
   private CameraType cameraType;
   private float cameraDistance;
   public final Setting<Boolean> scrollSetting;

   @Override
   public void onEnable() {
      if (this.isWorldUnavailable()) {
         this.disable();
         return;
      }

      this.cameraDistance = 1e+02F;
      this.cameraDistanceAnimation.setValue(0.0F);
      Options options = ClientAccess.zioMinecraft.options;
      CameraType selectedCameraType;
      if (options.getCameraType() == CameraType.FIRST_PERSON) {
         selectedCameraType = CameraType.THIRD_PERSON_BACK;
      } else {
         selectedCameraType = ClientAccess.zioMinecraft.options.getCameraType();
      }
      this.cameraType = selectedCameraType;
      GameOptionsOverrides.registerCameraOverride(this);
      Vector2f rotation = this.cameraRotation;
      float yaw = ClientAccess.zioMinecraft.player.getYRot();
      float pitch = ClientAccess.zioMinecraft.player.getXRot();
      rotation.set(yaw, pitch);
      if (ClientAccess.zioMinecraft.player instanceof EntityMotionAccess cameraPlayer) {
         float cameraYaw = this.cameraRotation.x;
         cameraPlayer.setCameraYaw(cameraYaw);
         float cameraPitch = this.cameraRotation.y;
         cameraPlayer.setCameraPitch(cameraPitch);
      }
   }

   @Override
   public void onDisable() {
      GameOptionsOverrides.unregisterCameraOverride(this);
      LocalPlayer yawPlayer = ClientAccess.zioMinecraft.player;
      Vector2f yawRotation = this.cameraRotation;
      float yaw = yawRotation.x();
      yawPlayer.setYRot(yaw);
      LocalPlayer previousYawBobPlayer = ClientAccess.zioMinecraft.player;
      LocalPlayer yawBobPlayer = ClientAccess.zioMinecraft.player;
      LocalPlayer previousYawPlayer = ClientAccess.zioMinecraft.player;
      Vector2f previousYawRotation = this.cameraRotation;
      previousYawBobPlayer.yBobO = yawBobPlayer.yBob = previousYawPlayer.yRotO = previousYawRotation.x();
      LocalPlayer pitchPlayer = ClientAccess.zioMinecraft.player;
      Vector2f pitchRotation = this.cameraRotation;
      float pitch = pitchRotation.y();
      pitchPlayer.setXRot(pitch);
      LocalPlayer previousPitchBobPlayer = ClientAccess.zioMinecraft.player;
      LocalPlayer pitchBobPlayer = ClientAccess.zioMinecraft.player;
      LocalPlayer previousPitchPlayer = ClientAccess.zioMinecraft.player;
      Vector2f previousPitchRotation = this.cameraRotation;
      previousPitchBobPlayer.xBobO = pitchBobPlayer.xBob = previousPitchPlayer.xRotO = previousPitchRotation.y();
      Camera camera = ClientAccess.zioMinecraft.gameRenderer.getMainCamera();
      ClientLevel level = ClientAccess.zioMinecraft.level;
      LocalPlayer cameraPlayer = ClientAccess.zioMinecraft.player;
      // 26.1.2 removed Camera.setup; the equivalent is entity + update(DeltaTracker).
      // The player rotation/bob fields were just overwritten above, so the update pass
      // picks them up exactly like the old setup(..., 1.0F) did.
      camera.setEntity(cameraPlayer);
      camera.update(ClientAccess.zioMinecraft.getDeltaTracker());
   }

   public float getCameraDistanceOffset() {
      float offset = this.cameraDistanceAnimation.getValue();
      return offset / 1e+01F;
   }

   public boolean isScrollEnabled() {
      return this.isToggled() && this.scrollSetting.getValue();
   }

   @EventHandler(priority = -200)
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      FreecamModule freecam = freecamModule;
      if (freecam.isToggled()) {
         return;
      }
      Options options = ClientAccess.zioMinecraft.options;
      CameraType selectedCameraType = this.cameraType;
      options.setCameraType(selectedCameraType);
      Minecraft minecraft = ClientAccess.zioMinecraft;
      LocalPlayer pickPlayer = ClientAccess.zioMinecraft.player;
      double range = ClientAccess.zioMinecraft.player.blockInteractionRange();
      float partialTick = RenderUtils.getPartialTick();
      minecraft.hitResult = pickPlayer.pick(range, partialTick, false);
   }

   @EventHandler
   private void onMouseHandlerOnScrollEvent(MouseHandlerOnScrollEvent event) {
      Boolean enabled = this.scrollSetting.getValue();
      if (enabled) {
         float previousDistance = this.cameraDistance;
         float scrollDelta = (float)event.getVerticalOffset() * this.cameraDistance;
         this.cameraDistance = previousDistance - scrollDelta * 0.08F;
         float distance = this.cameraDistance;
         this.cameraDistance = Mth.clamp(distance, 1.0F, 2e+02F);
         event.cancel();
      }
   }

   @EventHandler
   private void onMinecraftSetScreenEvent(
      ScreenChangeEvent.Before event
   ) {
      if (event.getNewScreen() instanceof LevelLoadingScreen) {
         this.disable();
      }
   }

   @EventHandler(priority = 200)
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (this.isWorldUnavailable()) {
         return;
      }
      InterpolatedFloat targetAnimation = this.cameraDistanceAnimation;
      float distance = this.cameraDistance;
      float target = distance - 1e+02F;
      targetAnimation.setTarget(target);
      this.cameraDistanceAnimation.updateAndGet();
   }

   public Vector2f getCameraRotation() {
      return this.cameraRotation;
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      freecamModule = (FreecamModule)registry.getModule(
         FreecamModule.class
      );
   }

   public FreeLookModule() {
      super("FreeLook", "Allows you to rotate your 3rd person camera without rotating your player.", ModuleCategory.RENDER, new String[0]);
      this.cameraDistanceAnimation = new InterpolatedFloat(2.0F, true);
      this.cameraRotation = new Vector2f();
      Boolean scrollDefault = false;
      String scrollName = new String("Scroll");
      BooleanSetting scroll = new BooleanSetting(scrollName, scrollDefault);
      this.scrollSetting = this.add(scroll);
   }

}
