/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;

import dev.zio.client.modules.movement.ElytraFlyModule.TakeoffChoice;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.utils.movement.ElytraFlyModeHandler;
import dev.zio.client.managers.TickRateController;
import dev.zio.client.events.EventStage;
import dev.zio.client.settings.Setting;

import dev.zio.mixin.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;


public class StrictElytraFlyHandler extends ElytraFlyModeHandler {
   private long accelerationStartTimeMillis;
   private boolean takeoffPending;
   private boolean awaitingFlight;
   private boolean fireworkLaunchPending;

   public StrictElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void onEnable() {
      if (ClientAccess.zioMinecraft.player.isFallFlying()) {
         if (!ClientAccess.zioMinecraft.player.onGround()) {
            this.takeoffPending = true;
            ClientAccess.zioMinecraft.player.stopFallFlying();
         }
      }

      this.awaitingFlight = false;
      this.fireworkLaunchPending = false;
      this.accelerationStartTimeMillis = System.currentTimeMillis();
   }

   @Override
   public void handleOutgoingPacket(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
         if (ClientAccess.zioMinecraft.player.isFallFlying()) {
            if (!ClientAccess.zioMinecraft.player.onGround()) {
               Vec3 velocity = ClientAccess.zioMinecraft.player.getDeltaMovement();
               double verticalMotion = velocity.y();

               if (verticalMotion < 0.1D && MovementPhysics.isLocalPlayerMoving()) {
                  ServerboundMovePlayerPacketAccessor packetAccess = (ServerboundMovePlayerPacketAccessor)packet;
                  packetAccess.setPitch(-5.0F);
               }
            }
         }
      }
   }

   @Override
   public void handlePlayerTick(LocalPlayerTickEvent event) {
      if (ClientAccess.zioMinecraft.player.onGround()) {
         this.takeoffPending = false;
      } else {
         if (ClientAccess.zioMinecraft.player.isFallFlying()) {
            this.takeoffPending = true;
         }
      }

      Input input = ClientAccess.zioMinecraft.player.input.keyPresses;
      boolean requestTakeoff = input.jump();
      Setting<TakeoffChoice> takeoffSetting = this.module.takeoffSetting;
      if (takeoffSetting.getValue() == ElytraFlyModule.TakeoffChoice.STRICT) {
         LocalPlayer strictGroundPlayer = ClientAccess.zioMinecraft.player;
         requestTakeoff = false;
         if (!strictGroundPlayer.onGround()) {
            Vec3 velocity = ClientAccess.zioMinecraft.player.getDeltaMovement();
            double verticalMotion = velocity.y();
            requestTakeoff = verticalMotion < 0.1D;
         }
      } else {
         ElytraFlyModule timerOwner = this.module;
         TickRateController timerController = ClientContext.tickRateController;
         timerController.releaseMultiplier(timerOwner);
      }

      if (requestTakeoff || this.awaitingFlight) {
         this.module.updateTakeoff();
         LocalPlayer playerAfterTakeoff = ClientAccess.zioMinecraft.player;
         this.awaitingFlight = !playerAfterTakeoff.isFallFlying();
      }
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      if (!ClientAccess.zioMinecraft.player.isFallFlying()
         && this.takeoffPending
         && !ClientAccess.zioMinecraft.player.horizontalCollision) {
         Vec3 fallingVelocity = ClientAccess.zioMinecraft.player.getDeltaMovement();
         if (fallingVelocity.y() < 0.0) {
            this.awaitingFlight = true;
         }
      }

      if (ClientAccess.zioMinecraft.player.isFallFlying()) {
         if (!MovementPhysics.isLocalPlayerMoving()) {
            this.accelerationStartTimeMillis = System.currentTimeMillis();
         }

         float maximumSpeed = this.module.strictSpeedSetting.getValue();
         float startSpeed = this.module.startSetting.getValue();
         float elapsedMillis = (float)(System.currentTimeMillis() - this.accelerationStartTimeMillis);

         float progress = Mth.clamp(elapsedMillis / 500.0F, 0.0F, 1.0F);
         float speed = Mth.lerp(progress, startSpeed, maximumSpeed);
         double[] horizontalMotion = MovementPhysics.applyHorizontalMotion(event, speed);
         ClientAccess.zioMinecraft.player.setDeltaMovement(horizontalMotion[0], 0.0D, horizontalMotion[1]);
         event.setY(0.0D);
         ClientAccess.zioMinecraft.player.setDeltaMovement(
            ClientAccess.zioMinecraft.player.getDeltaMovement().with(Axis.Y, 0.0D)
         );
      }
   }

   @Override
   public void handleIncomingPacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         if (ClientAccess.zioMinecraft.player.isFallFlying()) {
            if (!ClientAccess.zioMinecraft.player.onGround()) {
               this.accelerationStartTimeMillis = System.currentTimeMillis();
               ClientAccess.zioMinecraft.player.stopFallFlying();
            }
         }
      }
   }

   @Override
   public void handleSendPosition(LocalPlayerSendPositionEvent event) {
      if (event.getStage() == EventStage.Pre) {
         boolean adjustRotation = false;
         if (this.module.isElytraEquipped()) {
            if (!event.isOnGround()) {
               LocalPlayer player = ClientAccess.zioMinecraft.player;
               if (!player.isFallFlying()) {
                  adjustRotation = true;
                  this.fireworkLaunchPending = false;
               } else if (!this.fireworkLaunchPending) {
                  if (!MovementPhysics.isLocalPlayerMoving()) {
                     adjustRotation = true;
                  } else {
                     this.fireworkLaunchPending = true;
                  }
               }
            }
         }

         if (adjustRotation) {
            float originalYaw = event.getYaw();
            int yawTick = ClientAccess.zioMinecraft.player.tickCount;

            int yawOffset = yawTick % 2 == 0 ? -2 : 2;
            float yaw = originalYaw + yawOffset;
            event.setYaw(yaw);
            float originalPitch = event.getPitch();
            int pitchTick = ClientAccess.zioMinecraft.player.tickCount;
            int pitchOffset = pitchTick % 2 == 0 ? -2 : 2;
            float pitch = originalPitch + pitchOffset;
            event.setPitch(pitch);
         }
      }
   }
}
