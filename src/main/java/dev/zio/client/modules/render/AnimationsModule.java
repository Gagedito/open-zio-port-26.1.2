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
import dev.zio.client.events.CameraSetupEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.utils.math.Vec3Utils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;

public class AnimationsModule extends Module {
   private final Setting<Boolean> smoothCameraSetting;
   public final Setting<Boolean> crystalsSetting;
   public final Setting<Float> floatFactorSetting;
   public final Setting<Float> rotationSpeedSetting;
   public final Setting<Float> crystalScaleSetting;
   private final Setting<Boolean> partsSetting;
   private final Setting<Boolean> innerSetting;
   private final Setting<Boolean> outerSetting;
   private final Setting<Boolean> coreSetting;
   private final Setting<Boolean> bottomSetting;
   public final Setting<Boolean> playersSetting;
   public final Setting<Boolean> staticSetting;
   public final Setting<Boolean> sneakSetting;
   public final Setting<Float> playerScaleSetting;
   private boolean verticalCameraCatchUp;
   private boolean horizontalCameraCatchUp;
   private Vec3 cameraPosition;
   private Vec3 previousCameraPosition;

   public boolean shouldHideCrystalPart(int partIndex) {
      if (!this.isToggled()) {
         return false;
      }
      return switch (partIndex) {
         case 0 -> this.shouldHideCrystalBottom();
         case 1 -> this.shouldHideCrystalOuter();
         case 2 -> this.shouldHideCrystalInner();
         case 3 -> this.shouldHideCrystalCore();
         default -> false;
      };
   }

   public boolean isPlayerScalingEnabled() {
      if (this.isToggled()) {
         Boolean playersEnabled = this.playersSetting.getValue();
         if (playersEnabled) {
            float scale = this.playerScaleSetting.getValue();

            if (scale != 1.0F) {
               return true;
            }
         }
      }

      return false;
   }

   public void resetCrystalModelVisibility() {
      EndCrystal crystal = new EndCrystal(
         EntityType.END_CRYSTAL, ClientAccess.zioMinecraft.level
      );
      EntityRenderDispatcher dispatcherSnapshot = ClientAccess.zioMinecraft.getEntityRenderDispatcher();
      EndCrystalRenderer renderer = (EndCrystalRenderer)dispatcherSnapshot.<EndCrystal>getRenderer(crystal);
      renderer.model.cube.skipDraw = false;
      renderer.model.innerGlass.skipDraw = false;
      renderer.model.outerGlass.skipDraw = false;
   }

   public boolean shouldHideCrystalBottom() {
      return this.isCrystalAnimationEnabled() && !this.bottomSetting.getValue();
   }

   public boolean shouldHideCrystalOuter() {
      return this.isCrystalAnimationEnabled() && !this.outerSetting.getValue();
   }

   public boolean isStaticPlayerAnimationEnabled() {
      return this.isToggled() && this.playersSetting.getValue() && this.staticSetting.getValue();
   }

   public boolean isSneakingAnimationEnabled() {
      return this.isToggled() && this.playersSetting.getValue() && this.sneakSetting.getValue();
   }

   public boolean shouldHideCrystalCore() {
      return this.isCrystalAnimationEnabled() && !this.coreSetting.getValue();
   }

   public boolean isCrystalAnimationEnabled() {
      return this.isToggled() && this.crystalsSetting.getValue();
   }

   public boolean shouldHideCrystalInner() {
      return this.isCrystalAnimationEnabled() && !this.innerSetting.getValue();
   }

   @Override
   public void onDisable() {
      this.resetCrystalModelVisibility();
   }

   private boolean updateCameraCatchUp(boolean previousCatchUp, double distance, double catchUpThreshold) {
      if (distance > catchUpThreshold || PlayerStateUtils.isFallFlying()) {
         return true;
      }
      return distance < 0.1D ? false : previousCatchUp;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      Vec3 playerPosition = player.position();
      Boolean smoothCameraEnabled = this.smoothCameraSetting.getValue();
      if (smoothCameraEnabled
         && this.cameraPosition != null
         && this.previousCameraPosition != null) {
         this.previousCameraPosition = this.cameraPosition;
         Vec3 cameraPositionSnapshot = this.cameraPosition;
         double horizontalDistance = Vec3Utils.horizontalDistance(cameraPositionSnapshot, playerPosition);
         double verticalDelta = playerPosition.y - this.cameraPosition.y;
         double verticalDistance = Math.abs(verticalDelta);
         boolean previousHorizontalCatchUp = this.horizontalCameraCatchUp;
         this.horizontalCameraCatchUp = this.updateCameraCatchUp(previousHorizontalCatchUp, horizontalDistance, 3.0D);
         boolean previousVerticalCatchUp = this.verticalCameraCatchUp;
         this.verticalCameraCatchUp = this.updateCameraCatchUp(previousVerticalCatchUp, verticalDistance, 3.5D);

         double horizontalFraction = Math.min(1.0D, horizontalDistance) / horizontalDistance;
         if (Double.isNaN(horizontalFraction) || horizontalDistance > 64.0D) {
            horizontalFraction = 1.0D;
         } else if (this.horizontalCameraCatchUp) {
            horizontalFraction = Math.min(5.0D, horizontalDistance) / horizontalDistance;
         }

         double verticalFraction = Math.min(0.7D, verticalDistance) / verticalDistance;
         if (Double.isNaN(verticalFraction) || this.verticalCameraCatchUp) {
            verticalFraction = 1.0D;
         }

         this.cameraPosition = new Vec3(
            Mth.lerp(horizontalFraction, this.cameraPosition.x, playerPosition.x),
            Mth.lerp(verticalFraction, this.cameraPosition.y, playerPosition.y),
            Mth.lerp(horizontalFraction, this.cameraPosition.z, playerPosition.z)
         );
      } else {
         this.previousCameraPosition = this.cameraPosition = playerPosition;
      }
   }

   @EventHandler(priority = 100)
   private void onCameraSetupEvent(CameraSetupEvent event) {
      if (!this.smoothCameraSetting.getValue()) {
         return;
      }
      Options options = ClientAccess.zioMinecraft.options;
      CameraType cameraType = options.getCameraType();
      if (!cameraType.isFirstPerson() || this.cameraPosition == null || this.previousCameraPosition == null) {
         return;
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      if (player.getAbilities().flying) {
         return;
      }
      float partialTick = event.getPartialTick();
      Vec3 currentPosition = this.cameraPosition;
      Vec3 interpolatedPosition = this.previousCameraPosition.lerp(currentPosition, partialTick);
      GameRenderer gameRenderer = ClientAccess.zioMinecraft.gameRenderer;
      Camera camera = gameRenderer.getMainCamera();
      float currentEyeHeight = camera.eyeHeight;
      float previousEyeHeight = camera.eyeHeightOld;
      float eyeHeight = Mth.lerp(partialTick, previousEyeHeight, currentEyeHeight);
      Vec3 cameraPosition = interpolatedPosition.add(0.0, eyeHeight, 0.0);
      event.setPosition(cameraPosition);
      event.cancel();
   }

   public AnimationsModule() {
      super("Animations", "Modifies entity animations.", ModuleCategory.RENDER, new String[0]);
      this.smoothCameraSetting = this.add(new BooleanSetting("SmoothCamera", false));
      this.crystalsSetting = this.add(new BooleanSetting("Crystals", false).markVisibilityDependency());
      this.floatFactorSetting = this.add(new NumberSetting<>("FloatFactor", 1.0F, 0.0F, 1.0F).dependsOn(this.crystalsSetting));
      this.rotationSpeedSetting = this.add(new NumberSetting<>("RotationSpeed", 1.0F, 0.0F, 6.0F).dependsOn(this.crystalsSetting));
      this.crystalScaleSetting = this.add(new NumberSetting<>("CrystalScale", 1.0F, 0.0F, 2.0F).dependsOn(this.crystalsSetting));
      this.partsSetting = this.add(new BooleanSetting("Parts", false).markAsGroup().markVisibilityDependency().dependsOn(this.crystalsSetting));
      this.innerSetting = this.add(new BooleanSetting("Inner", true).dependsOn(this.crystalsSetting, this.partsSetting));
      this.outerSetting = this.add(new BooleanSetting("Outer", true).dependsOn(this.crystalsSetting, this.partsSetting));
      this.coreSetting = this.add(new BooleanSetting("Core", true).dependsOn(this.crystalsSetting, this.partsSetting));
      this.bottomSetting = this.add(new BooleanSetting("Bottom", true).dependsOn(this.crystalsSetting, this.partsSetting));
      this.playersSetting = this.add(new BooleanSetting("Players", false).markVisibilityDependency());
      this.staticSetting = this.add(new BooleanSetting("Static", false).dependsOn(this.playersSetting));
      this.sneakSetting = this.add(new BooleanSetting("Sneak", false).dependsOn(this.playersSetting));
      this.playerScaleSetting = this.add(new NumberSetting<>("PlayerScale", 1.0F, 0.0F, 2.0F).dependsOn(this.playersSetting));
      Setting<Boolean> callbackSetting = this.crystalsSetting;
      Runnable callback = this::resetCrystalModelVisibility;
      callbackSetting.setChangeCallback(callback);
   }

}
