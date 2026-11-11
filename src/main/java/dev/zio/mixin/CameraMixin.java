/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import dev.zio.client.events.CameraSetupEvent;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.utils.movement.EntityMotionAccess;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.modules.render.FreeLookModule;
import dev.zio.client.modules.player.FreecamModule;
import dev.zio.client.modules.render.NoRenderModule;
import dev.zio.client.modules.render.ViewClipModule;
import dev.zio.client.modules.render.ViewModelModule;
import dev.zio.client.modules.render.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {
   private static ViewClipModule viewClip = ClientContext.getModule(ViewClipModule.class);
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static ElytraFlyModule elytrafly = ClientContext.getModule(ElytraFlyModule.class);
   private static FreecamModule freecam = ClientContext.getModule(FreecamModule.class);
   private static FreeLookModule freelook = ClientContext.getModule(FreeLookModule.class);
   private static ZoomModule zoom = ClientContext.getModule(ZoomModule.class);
   private static ViewModelModule viewmodel = ClientContext.getModule(ViewModelModule.class);
   private float zio$alignPartialTick;

   @Inject(method = "alignWithEntity(F)V", at = @At("HEAD"))
   private void captureAlignPartialTick(float partialTick, CallbackInfo callback) {
      this.zio$alignPartialTick = partialTick;
   }
   @Final
   @Shadow
   private Vector3f forwards;
   @Final
   @Shadow
   private Vector3f up;
   @Final
   @Shadow
   private Vector3f left;
   @Shadow
   private Vec3 position;
   @Shadow
   private float eyeHeight;
   @Shadow
   private float eyeHeightOld;
   @Shadow
   private Entity entity;
   @Shadow
   @Final
   private EnvironmentAttributeProbe attributeProbe;
   @Shadow
   private Level level;

   @Shadow
   protected void setPosition(Vec3 position) {
   }

   @Shadow
   protected abstract void setRotation(float yaw, float pitch);

   @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
   private void applyViewClipAndFreeLookDistance(
      float originalDistance, CallbackInfoReturnable<Float> callback, @Local(argsOnly = true) LocalFloatRef distanceRef
   ) {
      if (viewClip.isToggled()) {
         float cameraDistance = viewClip.getCameraDistance();
         if (freelook.isScrollEnabled()) {
            cameraDistance += freelook.getCameraDistanceOffset();
            cameraDistance = Mth.clamp(cameraDistance, 1.0F, 20.0F);
         }

         callback.setReturnValue(cameraDistance);
         return;
      }
      if (freelook.isScrollEnabled()) {
         distanceRef.set(
            Mth.clamp(
               distanceRef.get()
                  + freelook.getCameraDistanceOffset(),
               1.0F,
               20.0F
            )
         );
      }
   }

   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void applyLegacySneakingCamera(CallbackInfo callback) {
      if (!noRenderModule.isToggled() || !noRenderModule.newSneakingSetting.getValue() || this.entity == null) {
         return;
      }
      callback.cancel();
      this.eyeHeightOld = this.eyeHeight;
      if (this.entity instanceof Player cameraPlayer) {
         if (cameraPlayer.hasPose(Pose.CROUCHING)) {
            this.eyeHeight = 1.54F;
         } else if (this.eyeHeight < 1.62F && cameraPlayer.hasPose(Pose.STANDING)) {
            float heightDifference = 1.62F - this.eyeHeight;
            heightDifference *= 0.4F;
            this.eyeHeight = 1.62F - heightDifference;
         } else {
            this.eyeHeight = this.eyeHeight + (this.entity.getEyeHeight() - this.eyeHeight) * 0.5F;
         }
      }

      this.attributeProbe.tick(this.level, this.position);
   }

   @Inject(method = "move", at = @At("HEAD"), cancellable = true)
   private void applySmoothViewClipMove(float forward, float vertical, float sideways, CallbackInfo callback) {
      if (viewClip.isToggled() && viewClip.smoothSetting.getValue()) {
         double offsetX = (double)this.forwards.x() * forward + (double)this.up.x() * vertical + (double)this.left.x() * sideways;
         double offsetY = (double)this.forwards.y() * forward + (double)this.up.y() * vertical + (double)this.left.y() * sideways;
         double offsetZ = (double)this.forwards.z() * forward + (double)this.up.z() * vertical + (double)this.left.z() * sideways;
         float blend = viewClip.clipAnimation.updateAndGet();
         this.setPosition(new Vec3(this.position.x + offsetX * blend, this.position.y + offsetY * blend, this.position.z + offsetZ * blend));
         callback.cancel();
      }
   }

   @ModifyArgs(method = "alignWithEntity(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
   private void modifySetupPosition(
      Args args
   ) {
      // MC 26.1.2: Camera.setup(...) se fusiono en alignWithEntity(F); la
      // entidad es el campo this.entity (ya no es argumento) y el partial tick
      // es el ARGUMENTO del metodo (capturado en captureAlignPartialTick):
      // usar RenderUtils.getPartialTick() aca quedaba un frame atras (ver
      CameraSetupEvent event = new CameraSetupEvent(
         this.entity,
         this.zio$alignPartialTick,
         args.<Double>get(0),
         args.<Double>get(1),
         args.<Double>get(2)
      );
      ClientAccess.eventBus
         .post(event);
      if (event.isCancelled()) {
         args.set(0, event.getPosition().x);
         args.set(1, event.getPosition().y);
         args.set(
            2,
            event.getPosition().z
         );
      }
   }

   @ModifyArgs(method = "alignWithEntity(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
   private void applyCameraSetupRotations(Args arguments) {
      if (elytrafly.isToggled()
         && elytrafly.modeSetting.getValue() == ElytraFlyModule.ModeChoice.BOUNCE
         && elytrafly.pitchLockSetting.getValue()
         && !elytrafly.silentSetting.getValue()) {
         arguments.set(1, elytrafly.pitchSetting.getValue());
      }

      float partialTick = RenderUtils.getPartialTick();
      if (freecam.isToggled()) {
         arguments.set(
            0,
            (float)freecam.getInterpolatedYaw(partialTick)
         );
         arguments.set(
            1,
            (float)freecam.getInterpolatedPitch(partialTick)
         );
      }
   }

   @Inject(method = "alignWithEntity(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1, shift = Shift.AFTER))
   public void applyFreeLookRotation(
      float partialTick, CallbackInfo callback
   ) {
      if (!freecam.isToggled() && freelook.isToggled() && this.entity instanceof EntityMotionAccess motionAccess) {
         this.setRotation(motionAccess.getCameraYaw(), motionAccess.getCameraPitch());
      }
   }

   /**
    * MC 26.1.2: migrado desde GameRendererMixin (applyZoomAndViewModelFov).
    * El FOV de manos/HUD-3D sigue un call-site useFovSetting=false en 1.21.11
    * (proyeccion de manos de GameRenderer.render); en 26.1.2 ese valor es
    * Camera.hudFov, calculado por calculateHudFov y consumido por la
    * proyeccion de manos de renderLevel y por extractRenderState.
    */
   @ModifyReturnValue(method = "calculateHudFov(F)F", at = @At("RETURN"))
   private float applyHandZoomAndViewModelFov(float originalFov) {
      float zoomScale = 1.0F - zoom.getZoomBlend();
      return viewmodel.isToggled() && viewmodel.viewModelFovSetting.getValue()
         ? (viewmodel.fovAmountSetting.getValue()).intValue() * zoomScale
         : originalFov * zoomScale;
   }
}
