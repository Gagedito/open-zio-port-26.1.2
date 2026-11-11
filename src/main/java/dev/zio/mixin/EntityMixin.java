/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.utils.math.MathConstants;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.StepHeightEvent;
import dev.zio.client.utils.movement.EntityMotionAccess;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.modules.render.FreeLookModule;
import dev.zio.client.modules.player.FreecamModule;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.render.NoRenderModule;
import dev.zio.client.modules.movement.NoSlowModule;
import dev.zio.client.modules.movement.VelocityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityMotionAccess {
   private static NoSlowModule noSlowModule = ClientContext.getModule(NoSlowModule.class);
   private static ElytraFlyModule elytraFlyModule = ClientContext.getModule(ElytraFlyModule.class);
   private static FreecamModule freecamModule = ClientContext.getModule(FreecamModule.class);
   private static VelocityModule velocityModule = ClientContext.getModule(VelocityModule.class);
   private static FreeLookModule freelook = ClientContext.getModule(FreeLookModule.class);
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   @Unique
   private float cameraPitch;
   @Unique
   private float cameraYaw;
   @Unique
   private Vec3 previousVelocity;

   @Shadow
   private Vec3 collide(Vec3 movement) {
      return null;
   }

   @Shadow
   @Override
   public abstract boolean equals(Object other);

   @Shadow
   public abstract Vec3 getDeltaMovement();

   @Shadow
   public abstract boolean hasPose(Pose pose);

   @Shadow
   public abstract Vec3 calculateViewVector(float pitch, float yaw);

   @ModifyReceiver(method = "getBlockSpeedFactor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getSpeedFactor()F"), require = 0)
   private Block overrideBlockSpeedFactor(Block originalBlock) {
      if ((Object)this != Minecraft.getInstance().player) {
         return originalBlock;
      } else {
         return noSlowModule.isToggled()
               && noSlowModule.soulSandSetting.getValue()
            ? Blocks.STONE
            : originalBlock;
      }
   }

   @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
   private void cancelEntityPush(Entity otherEntity, CallbackInfo callback) {
      if (this.equals(Minecraft.getInstance().player)
         && velocityModule.isToggled()
         && velocityModule.pushSetting.getValue()) {
         callback.cancel();
      }
   }

   @ModifyExpressionValue(
      method = "updateFluidHeightAndDoFluidPushing",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/material/FluidState;getFlow(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"
      )
   )
   private Vec3 modifyFluidPush(Vec3 originalPush) {
      return velocityModule.isToggled()
            && velocityModule.liquidsSetting.getValue()
         ? Vec3.ZERO
         : originalPush;
   }

   @ModifyExpressionValue(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"))
   private Vec3 overridePickViewVector(Vec3 originalView) {
      if (!freelook.isToggled()) {
         return originalView;
      }
      Vector2f cameraRotation = freelook.getCameraRotation();
      return this.calculateViewVector(cameraRotation.y(), cameraRotation.x());
   }

   @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
   public void updateFreelookRotation(double horizontalInput, double verticalInput, CallbackInfo callback) {
      if (freecamModule.isToggled() || !freelook.isToggled() || !ClientAccess.isLocalPlayer(this)) {
         return;
      }
      double pitchDelta = verticalInput * 0.15;
      double yawDelta = horizontalInput * 0.15;
      this.cameraPitch = Mth.clamp(
         this.cameraPitch + (float)pitchDelta,
         -MathConstants.NINETY_DEGREES,
         MathConstants.NINETY_DEGREES
      );
      this.cameraYaw += (float)yawDelta;
      callback.cancel();
   }

   @ModifyExpressionValue(
      method = "move",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
   )
   private Vec3 modifyStepCollision(
      Vec3 originalCollision, @Local(argsOnly = true) Vec3 movement
   ) {
      if (!ClientAccess.isLocalPlayer(this)) {
         return this.collide(movement);
      }

      LivingEntity entity = (LivingEntity)(Object)this;
      float previousHeight = EntityUtils.getStepHeight(entity);
      StepHeightEvent beforeStep = new StepHeightEvent(EventStage.Pre, previousHeight);
      ClientAccess.eventBus
         .post(beforeStep);
      boolean modified = beforeStep.isModified();
      float requestedHeight = beforeStep.getHeight();
      if (modified) {
         EntityUtils.setStepHeight(entity, requestedHeight);
      }

      Vec3 collision = this.collide(movement);
      if (modified) {
         EntityUtils.setStepHeight(entity, previousHeight);
      }

      if (collision != null) {
         ClientAccess.eventBus
            .post(
               new StepHeightEvent(EventStage.Post, (float)collision.y)
            );
      }

      return collision;
   }

   @Inject(method = "getPose", at = @At("HEAD"), cancellable = true)
   private void overrideElytraPose(CallbackInfoReturnable<Pose> callback) {
      if ((Object)this == Minecraft.getInstance().player) {
         if (elytraFlyModule.isPacketFlightActive()
            || elytraFlyModule.isDurabilityBypassActive()) {
            callback.setReturnValue(Pose.STANDING);
            callback.cancel();
         }
      }
   }

   @ModifyExpressionValue(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
   private float applyLegacySneakingEyePosition(float originalEyeHeight) {
      return noRenderModule.isToggled() && noRenderModule.newSneakingSetting.getValue() && this.hasPose(Pose.CROUCHING) ? 1.54F : originalEyeHeight;
   }

   @WrapOperation(method = "saveWithoutId", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V"))
   private void saveAdditionalDataForSidePlayer(
      Entity entity, ValueOutput output, Operation original
   ) {
      if (output instanceof TagValueOutput tagOutput && tagOutput.buildResult().contains("zio_side_player")) {
         try {
            original.call(entity, output);
         } catch (Throwable ignoredFailure) {
         }
      } else {
         original.call(entity, output);
      }
   }

   @Inject(
      method = "baseTick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V", ordinal = 0, shift = Shift.AFTER)
   )
   private void capturePreviousVelocity(CallbackInfo callback) {
      this.previousVelocity = this.getDeltaMovement();
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void initializePreviousVelocity(
      EntityType entityType, Level level, CallbackInfo callback
   ) {
      this.previousVelocity = Vec3.ZERO;
   }

   @Unique
   @Override
   public float getCameraPitch() {
      return this.cameraPitch;
   }

   @Unique
   @Override
   public float getCameraYaw() {
      return this.cameraYaw;
   }

   @Unique
   @Override
   public void setCameraPitch(float pitch) {
      this.cameraPitch = pitch;
   }

   @Unique
   @Override
   public void setCameraYaw(float yaw) {
      this.cameraYaw = yaw;
   }

   @Override
   public Vec3 zio$getPrevVelocity() {
      return this.previousVelocity;
   }

   @Unique
   private static void zio$bindEntityModules(ModuleRegistry moduleRegistry) {
      noSlowModule = (NoSlowModule)moduleRegistry.getModule(
         NoSlowModule.class
      );
      elytraFlyModule = (ElytraFlyModule)moduleRegistry.getModule(
         ElytraFlyModule.class
      );
      freecamModule = (FreecamModule)moduleRegistry.getModule(
         FreecamModule.class
      );
      velocityModule = (VelocityModule)moduleRegistry.getModule(
         VelocityModule.class
      );
      freelook = (FreeLookModule)moduleRegistry.getModule(FreeLookModule.class);
      noRenderModule = (NoRenderModule)moduleRegistry.getModule(NoRenderModule.class);
   }
}
