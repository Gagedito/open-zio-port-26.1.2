/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;

import static dev.zio.client.utils.math.MathConstants.FULL_TURN_DEGREES;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.movement.ElytraFlyModeHandler;

import net.minecraft.core.Direction.Axis;


public class PacketElytraFlyHandler extends ElytraFlyModeHandler {
   private long accelerationStartTimeMillis;
   private float flightSpeed;
   private final ElapsedTimer idleMovementTimer = new ElapsedTimer();

   public PacketElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void onEnable() {
      this.flightSpeed = 0.0F;
      this.accelerationStartTimeMillis = System.currentTimeMillis();
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      if (!this.module.isPacketFlightActive()) {
         return;
      }
      if (!MovementPhysics.isLocalPlayerMoving()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
      }
      if (this.module.accelerateSetting.getValue()) {
         this.flightSpeed = (float)MovementPhysics.accelerateTo(
            this.module.flightSpeedSetting.getValue(), this.module.accelMinSetting.getValue(),
            this.module.accelTimeSetting.getValue(), this.accelerationStartTimeMillis
         );
      } else {
         this.flightSpeed = this.module.flightSpeedSetting.getValue();
      }
      double[] horizontalMotion = MovementPhysics.applyHorizontalMotion(event, this.flightSpeed);
      float glideMotion = -this.module.glideSetting.getValue();
      ClientAccess.zioMinecraft.player.setDeltaMovement(horizontalMotion[0], glideMotion, horizontalMotion[1]);
      event.setY(glideMotion);
      if (ClientAccess.zioMinecraft.player.input.keyPresses.shift()) {
         event.setY(-0.5D);
         ClientAccess.zioMinecraft.player.setDeltaMovement(
            ClientAccess.zioMinecraft.player.getDeltaMovement().with(Axis.Y, -0.5D)
         );
      }
      PacketSender.sendStartFallFlying();
      if (this.module.antiAfkSetting.getValue() && event.getX() == 0.0 && event.getZ() == 0.0
         && this.idleMovementTimer.hasElapsed(2500L)) {
         double radians = Math.toRadians(ClientAccess.zioMinecraft.player.tickCount % FULL_TURN_DEGREES);
         event.setHorizontalVelocity(Math.sin(radians) * 0.03D, Math.cos(radians) * 0.03D);
         return;
      }
      if (MovementPhysics.isLocalPlayerMoving()) {
         this.idleMovementTimer.reset();
      }
   }

}
