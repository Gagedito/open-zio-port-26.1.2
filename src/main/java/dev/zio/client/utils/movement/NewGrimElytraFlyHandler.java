/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

import dev.zio.client.modules.movement.ElytraFlyModule.StopMotionChoice;

import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.managers.TickRateController;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.math.Vec3Utils;
import dev.zio.client.utils.network.PacketSender;

import static dev.zio.client.utils.math.MathConstants.NINETY_DEGREES;

import static dev.zio.client.utils.math.MathConstants.FORTY_FIVE_DEGREES;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.FireworkRocketEntityTickEvent;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.combat.AutoMaceModule;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.settings.Setting;

import java.util.Iterator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public class NewGrimElytraFlyHandler extends ElytraFlyModeHandler {
   private static AutoMaceModule autoMaceModule;
   private boolean movementStarted;
   private boolean negativeYawJitter;
   private boolean pitchJitterPhase;
   private float serverYaw;
   private float serverPitch;

   public NewGrimElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void handleFireworkTick(FireworkRocketEntityTickEvent event) {
      if (autoMaceModule.isToggled()) {
         if (autoMaceModule.isFlightControlActive()) {
            return;
         }
      }

      float yaw = this.serverYaw;
      event.setYaw(yaw);
      float pitch = this.serverPitch;
      event.setPitch(pitch);
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      if (EntityUtils.hasCollisionBelow(ClientAccess.zioMinecraft.player)) {
         this.movementStarted = false;
      }

      if (this.isIdleAirborne()) {
         if (this.hasDurabilityFlightCondition()) {
            Setting<StopMotionChoice> stopMotionSetting = this.module.stopMotionSetting;
            if (stopMotionSetting.getValue() == ElytraFlyModule.StopMotionChoice.STATIC) {
               if (autoMaceModule.isToggled()) {
                  if (autoMaceModule.isFlightControlActive()) {
                     return;
                  }
               }

               Vec3 stoppedVelocity = Vec3.ZERO;
               event.setVelocity(stoppedVelocity);
            }
         }
      }
   }

   @Override
   public void handleClientTick(MinecraftRunTickEvent event) {
      ElytraFlyModule moduleForTimerRelease = this.module;
      TickRateController timerControllerForRelease = ClientContext.tickRateController;
      timerControllerForRelease.releaseMultiplier(moduleForTimerRelease);
      if (autoMaceModule.isToggled()) {
         if (autoMaceModule.isFlightControlActive()) {
            return;
         }
      }

      if (!this.isWorldUnavailable()) {
         Setting<StopMotionChoice> stopMotionSetting = this.module.stopMotionSetting;
         if (stopMotionSetting.getValue() == ElytraFlyModule.StopMotionChoice.TIMER) {
            if (this.hasDurabilityFlightCondition()) {
               this.movementStarted = true;

               if (this.isIdleAirborne()) {
                  TickRateController timerControllerForRequest = ClientContext.tickRateController;
                  ElytraFlyModule moduleForTimerRequest = this.module;

                  timerControllerForRequest.requestMultiplier(moduleForTimerRequest, 0.02F);
               }

               return;
            }
         }
      }
   }

   @Override
   public void handleRotationManagerEvent(RotationRequestEvent event) {
      if (!EntityUtils.hasCollisionBelow(ClientAccess.zioMinecraft.player)) {
         if (autoMaceModule.isToggled()) {
            if (autoMaceModule.isFlightControlActive()) {
               return;
            }
         }
         if (PlayerStateUtils.isLocalWearingElytra()) {
            ElytraFlyModule.InfDurabilityChoice durabilityChoice = this.module.infDurabilitySetting.getValue();
            if (durabilityChoice.isDisabled()) {
               if (!PlayerStateUtils.isFallFlying()) {
                  ClientAccess.zioMinecraft.player.startFallFlying();

                  PacketSender.sendStartFallFlying();
               }
            }
         }
         if (this.hasDurabilityFlightCondition()) {
            float playerYaw = ClientAccess.zioMinecraft.player.getYRot();
            ClientInput movementInput = ClientAccess.zioMinecraft.player.input;
            double[] horizontalMotion = MovementPhysics.getHorizontalMotion(
               playerYaw, movementInput, 1.0D
            );
            double directionZ = horizontalMotion[1];
            double directionX = horizontalMotion[0];
            double directionRadians = Math.atan2(directionZ, directionX);
            float yaw = (float)(Math.toDegrees(directionRadians)
               - NINETY_DEGREES);

            if (!MovementPhysics.isLocalPlayerMoving()) {
               yaw = playerYaw;
            }
            yaw += (this.negativeYawJitter ? -1 : 1) * AntiCheatModule.ROTATION_JITTER_STEP;

            this.negativeYawJitter = !this.negativeYawJitter;

            float pitch = -1.0F;
            if (ClientAccess.zioMinecraft.player.input.keyPresses.jump()) {
               pitch = MovementPhysics.isLocalPlayerMoving()
                  ? -FORTY_FIVE_DEGREES
                  : -NINETY_DEGREES;

               if (!MovementPhysics.isLocalPlayerMoving()) {
                  float stationaryJumpYaw = yaw + 180.0F;
                  yaw = Mth.wrapDegrees(stationaryJumpYaw);
               }
            }

            if (ClientAccess.zioMinecraft.player.input.keyPresses.shift()) {
               Boolean responsiveDescend = this.module.responsiveDescendSetting.getValue();
               if (responsiveDescend) {
                  float responsiveDescendPitch;
                  if (MovementPhysics.isLocalPlayerMoving()) {
                     responsiveDescendPitch = ClientAccess.zioMinecraft.player.getXRot();
                  } else {
                     responsiveDescendPitch = NINETY_DEGREES;
                  }

                  pitch = responsiveDescendPitch;
               } else {
                  pitch = MovementPhysics.isLocalPlayerMoving()
                     ? FORTY_FIVE_DEGREES
                     : NINETY_DEGREES;
               }
            }

            Setting<StopMotionChoice> stopMotionSetting = this.module.stopMotionSetting;
            if (stopMotionSetting.getValue() == ElytraFlyModule.StopMotionChoice.MOTION) {
               if (this.isIdleAirborne()) {
                  if (this.pitchJitterPhase) {
                     float jitteredYaw = yaw + 180.0F;
                     yaw = Mth.wrapDegrees(jitteredYaw);
                  }

                  this.pitchJitterPhase = !this.pitchJitterPhase;
               }
            }

            this.serverYaw = yaw;
            this.serverPitch = pitch;
            RotationManager rotationManagerForRequest = ClientContext.rotationManager;

            float[] rotation = {yaw, pitch};

            rotationManagerForRequest.requestRotation(rotation, 999999, true);
            ClientContext.rotationManager.forceRotationOverride();
         }
      }
   }

   private boolean isIdleAirborne() {
      Input movementInput = ClientAccess.zioMinecraft.player.input.keyPresses;
      boolean inputRequested = MovementPhysics.isLocalPlayerMoving()
         || movementInput.jump() || movementInput.shift() || this.isMovementKeyDown();
      return !EntityUtils.hasCollisionBelow(ClientAccess.zioMinecraft.player) && !inputRequested;
   }

   public boolean shouldFallWithChestplate() {
      if (!this.module.isToggled() || !this.movementStarted) {
         return false;
      }
      if (!ClientAccess.zioMinecraft.player.input.keyPresses.shift()) {
         return false;
      }
      Boolean fallChestplateEnabled = this.module.fallChestplateSetting.getValue();
      if (!fallChestplateEnabled) {
         return false;
      }
      float nearestPlayerDistance = 999.0F;
      ClientLevel level = ClientAccess.zioMinecraft.level;
      Iterator<AbstractClientPlayer> players = level.players().iterator();
      while (players.hasNext()) {
         AbstractClientPlayer otherPlayer = players.next();
         if (otherPlayer == ClientAccess.zioMinecraft.player) {
            continue;
         }
         SocialRegistry socialRegistry = ClientContext.socialRegistry;
         if (socialRegistry.isFriendPlayer(otherPlayer)) {
            continue;
         }
         double otherPlayerY = otherPlayer.getY();
         LocalPlayer playerForHeightCheck = ClientAccess.zioMinecraft.player;
         if (otherPlayerY > playerForHeightCheck.getY()) {
            continue;
         }
         otherPlayerY = otherPlayer.getY();
         LocalPlayer playerForHeightDifference = ClientAccess.zioMinecraft.player;
         double heightDifference = otherPlayerY - playerForHeightDifference.getY();
         double verticalDistance = Math.abs(heightDifference);
         if (verticalDistance > 15.0D) {
            continue;
         }
         Vec3 otherPosition = otherPlayer.position();
         Vec3 localPosition = ClientAccess.zioMinecraft.player.position();
         float horizontalDistance = Vec3Utils.horizontalDistance(otherPosition, localPosition);
         nearestPlayerDistance = Math.min(nearestPlayerDistance, horizontalDistance);
      }

      return nearestPlayerDistance < 5.0F;
   }

   private boolean hasDurabilityFlightCondition() {
      ElytraFlyModule.InfDurabilityChoice durabilityChoice = this.module.infDurabilitySetting.getValue();
      if (durabilityChoice.isDisabled()) {
         return PlayerStateUtils.isFallFlying();
      } else {
         return !EntityUtils.hasCollisionBelow(ClientAccess.zioMinecraft.player);
      }
   }

   private boolean isMovementKeyDown() {
      KeyMapping forwardKey = ClientAccess.zioMinecraft.options.keyUp;
      KeyMapping backwardKey = ClientAccess.zioMinecraft.options.keyDown;
      KeyMapping rightKey = ClientAccess.zioMinecraft.options.keyRight;
      KeyMapping leftKey = ClientAccess.zioMinecraft.options.keyLeft;
      KeyMapping sneakKey = ClientAccess.zioMinecraft.options.keyShift;
      KeyMapping jumpKey = ClientAccess.zioMinecraft.options.keyJump;
      return forwardKey.isDown() || backwardKey.isDown() || rightKey.isDown()
         || leftKey.isDown() || sneakKey.isDown() || jumpKey.isDown();
   }

   public static void bindModuleReferences(ModuleRegistry registry) {
      autoMaceModule = (AutoMaceModule)registry.getModule(AutoMaceModule.class);
   }
}
