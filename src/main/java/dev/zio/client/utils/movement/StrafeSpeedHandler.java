/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

import dev.zio.client.render.RenderUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.math.Vec3Utils;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.movement.SpeedModeHandler;
import dev.zio.client.modules.movement.LongJumpModule;
import dev.zio.client.modules.movement.SpeedModule;

import net.minecraft.client.player.LocalPlayer;

public class StrafeSpeedHandler extends SpeedModeHandler {
   private static LongJumpModule longJumpModule;

   public StrafeSpeedHandler(SpeedModule speedModule) {
      super(speedModule);
      this.movementStage = 4;
   }

   @Override
   public void onMove(LocalPlayerMoveEvent event) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      if (this.speedModule.shouldPauseInBlocks() || this.speedModule.shouldPauseInLiquid()
         || PlayerStateUtils.isHungerLimited() || player.isFallFlying() || player.isSpectator()
         || longJumpModule.isToggled()) {
         return;
      }

      double motionY = event.getY();
      if (this.speedModule.useTimerSetting.getValue()) {
         if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L)) {
            ClientContext.tickRateController.requestMultiplier(this.speedModule, 1.0888F);
         } else {
            ClientContext.tickRateController.releaseMultiplier(this.speedModule);
         }
      }
      float speedMultiplier = !this.speedModule.explosionBoostTimer.hasElapsed(1000L)
         && this.speedModule.boostSetting.getValue() ? 1.2F : 1.0F;
      double configuredSpeed = 0.2873D * this.speedModule.speedSetting.getValue() * speedMultiplier;
      if (this.movementStage == 1 && MovementPhysics.isLocalPlayerMoving()) {
         this.moveSpeed = 1.35D * MovementPhysics.applySpeedEffects(false, configuredSpeed) - 0.01D;
      } else if (this.movementStage == 2 && MovementPhysics.isLocalPlayerMoving() && player.verticalCollisionBelow) {
         motionY = 0.42D + MovementPhysics.getJumpBoost();
         this.moveSpeed *= 1.535D;
      } else if (this.movementStage == 3) {
         this.moveSpeed = this.lastHorizontalDistance - 0.6600000262260437D
            * (this.lastHorizontalDistance - MovementPhysics.applySpeedEffects(true, configuredSpeed));
      } else {
         if ((this.hasVerticalMotionCollision() || player.verticalCollision) && this.movementStage > 0) {
            this.movementStage = MovementPhysics.isLocalPlayerMoving() ? 1 : 0;
         }
         this.moveSpeed = this.lastHorizontalDistance - this.lastHorizontalDistance / 159.0D;
      }
      this.moveSpeed = Math.max(this.moveSpeed, MovementPhysics.applySpeedEffects(false, configuredSpeed));
      double motionX = 0.0;
      double motionZ = 0.0;
      if (MovementPhysics.isLocalPlayerMoving()) {
         double[] motion = MovementPhysics.getHorizontalMotion(player.getViewYRot(RenderUtils.getPartialTick()), player.input, this.moveSpeed);
         motionX = motion[0];
         motionZ = motion[1];
      }
      Vec3Utils.setComponents(event.getVelocity(), motionX, motionY, motionZ);
      if (MovementPhysics.isLocalPlayerMoving()) {
         this.movementStage++;
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      longJumpModule = (LongJumpModule)modules.getModule(
         LongJumpModule.class
      );
   }
}
