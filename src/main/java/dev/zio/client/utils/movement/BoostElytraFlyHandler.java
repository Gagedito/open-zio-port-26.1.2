/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.TickRateController;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.network.PacketSender;

import static dev.zio.client.utils.math.MathConstants.NINETY_DEGREES;

import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.movement.ElytraFlyModule;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public class BoostElytraFlyHandler extends ElytraFlyModeHandler {
   private static AntiCheatModule antiCheatModule;
   private final ElapsedTimer takeoffGraceTimer = new ElapsedTimer();
   private boolean boostPitchInitialized;
   private float targetPitch;
   private float movementYaw;

   public BoostElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   private float computeMovementYaw() {
      if (MovementPhysics.isLocalPlayerMoving()) {
         if (this.module.responsiveSetting.getValue()) {
            LocalPlayer player = ClientAccess.zioMinecraft.player;
            float yaw = player.getYRot();
            return MovementPhysics.getMovementYaw(yaw);
         }
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      return player.getYRot();
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      float limit = this.module.limitSetting.getValue();
      if (limit > 200.0F) {
         if (this.isEarlyNcpBoost()) {
            limit = 200.0F;
         }
      }
      if (this.module.limitSetting.isAtMaximum()) {
         if (!this.isEarlyNcpBoost()) {
            return;
         }
      }
      Vec3 velocity = event.getVelocity();
      double velocityX = velocity.x();
      double velocityZ = velocity.z();
      double horizontalSpeed = Math.hypot(velocityX, velocityZ);
      double blocksPerSecond = horizontalSpeed * 20.0D;
      if (!(blocksPerSecond * 3.6D <= limit)) {
         Vec3 limitedVelocity = event.getVelocity();
         Vec3 direction = limitedVelocity.normalize();
         double limitAsDouble = limit;
         double metersPerSecond = limitAsDouble / 3.6D;
         double blocksPerTick = metersPerSecond / 20.0D;
         direction = direction.scale(blocksPerTick);
         double limitedX = direction.x();
         double limitedZ = direction.z();
         event.setHorizontalVelocity(limitedX, limitedZ);
      }
   }

   @Override
   public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
      if (this.module.autoJumpSetting.getValue()) {
         if (ClientAccess.zioMinecraft.player.isFallFlying()) {
            ClientContext.inputOverrides.requestJump(true);
         }
      }
      this.movementYaw = this.computeMovementYaw();
      this.boostPitchInitialized = false;
      LocalPlayer motionPlayer = ClientAccess.zioMinecraft.player;
      if (!PlayerStateUtils.isInWater(motionPlayer)) {
         if (antiCheatModule.is2b2tEnabled()) {
            if (ClientAccess.zioMinecraft.player.isUsingItem()) {
               return;
            }
         }
         Input input = ClientAccess.zioMinecraft.player.input.keyPresses;
         boolean takeoffRequested = input.jump()
            && this.module.takeoffSetting.getValue() != ElytraFlyModule.TakeoffChoice.NONE;
         if (this.module.takeoffSetting.getValue() == ElytraFlyModule.TakeoffChoice.STRICT) {
            boolean strictTakeoffRequested;
            if (!ClientAccess.zioMinecraft.player.onGround()) {
               Vec3 velocity = ClientAccess.zioMinecraft.player.getDeltaMovement();
               double verticalSpeed = velocity.y();
               strictTakeoffRequested = verticalSpeed < 0.1D;
            } else {
               strictTakeoffRequested = false;
            }
            takeoffRequested = strictTakeoffRequested;
         } else {
            ElytraFlyModule moduleForTickRate = this.module;
            TickRateController tickRateController = ClientContext.tickRateController;
            tickRateController.releaseMultiplier(moduleForTickRate);
         }
         if (ClientAccess.zioMinecraft.player.isFallFlying()) {
            this.takeoffGraceTimer.reset();
         }
         takeoffWindow: {
            if (!takeoffRequested) {
               if (this.takeoffGraceTimer.hasElapsed(1500L)) {
                  break takeoffWindow;
               }
            }
            if (!ClientAccess.zioMinecraft.player.isFallFlying()) {
               if (!EntityUtils.hasCollisionBelow(ClientAccess.zioMinecraft.player)) {
                  if (this.module.isElytraEquipped()) {
                     PacketSender.sendStartFallFlying();
                     ClientAccess.zioMinecraft.player.startFallFlying();
                  }
               }
            }
         }
         if (this.module.isFallFlyingWithoutOverride()) {
            float movementYawRadians = (float)Math.toRadians(this.movementYaw);
            float minimumBoost = this.module.minBoostSetting.getValue();
            float boostMagnitude = minimumBoost * 0.1F;
            if (this.module.minBoostSetting.isAtMaximum()) {
               if (antiCheatModule.is2b2tEnabled()) {
                  boostMagnitude = 0.0205F;
                  if (this.targetPitch <= 0.0F) {
                     ElapsedTimer setbackTimer = ClientContext.serverStateManager.getSetbackTimer();
                     double setbackWindowSeconds = 10.0D;
                     TimeUnit seconds = TimeUnit.SECONDS;
                     if (!setbackTimer.hasElapsed(setbackWindowSeconds, seconds)) {
                        boostMagnitude = 0.008F;
                     }
                  }
               }
            }
            if (this.isEarlyNcpBoost()) {
               boostMagnitude = 3.0F;
            }
            if (this.module.nCPBoostSetting.getValue()) {
               this.updateBoostPitch();
            }
            if (!this.boostPitchInitialized) {
               if (this.module.inLavaSetting.getValue()) {
                  if (ClientAccess.zioMinecraft.player.isInLava()) {
                     float lavaBoostScale = 0.2F;
                     Float configuredSpeed = this.module.speedSetting.getValue();
                     boostMagnitude = lavaBoostScale * configuredSpeed;
                  }
               }
               forwardBoost: {
                  if (this.module.factorizeSetting.getValue()) {
                     if (this.targetPitch > 0.0F) {
                        break forwardBoost;
                     }
                  } else {
                     if (ClientAccess.zioMinecraft.options.keyUp.isDown()) {
                        break forwardBoost;
                     }
                     if (this.module.alwaysSetting.getValue()) {
                        break forwardBoost;
                     }
                  }
                  if (ClientAccess.zioMinecraft.options.keyDown.isDown()) {
                     motionPlayer = ClientAccess.zioMinecraft.player;
                     double backwardYawForSine = movementYawRadians;
                     double backwardX = Mth.sin(backwardYawForSine) * boostMagnitude;
                     double backwardYawForCosine = movementYawRadians;
                     double backwardZ = Mth.cos(backwardYawForCosine) * -boostMagnitude;
                     double backwardY = 0.0;
                     motionPlayer.push(backwardX, backwardY, backwardZ);
                  }
                  return;
               }
               motionPlayer = ClientAccess.zioMinecraft.player;
               double forwardYawForSine = movementYawRadians;
               double forwardX = Mth.sin(forwardYawForSine) * -boostMagnitude;
               double forwardYawForCosine = movementYawRadians;
               double forwardZ = Mth.cos(forwardYawForCosine) * boostMagnitude;
               double forwardY = 0.0;
               motionPlayer.push(forwardX, forwardY, forwardZ);
            }
         }
      }
   }

   @Override
   public void handleClientTick(MinecraftRunTickEvent event) {
      if (this.module.nCPBoostSetting.getValue()) {
         return;
      }
      if (!this.isWorldUnavailable()) {
         if (this.module.isFallFlyingWithoutOverride()) {
            if (event.hasPendingTicks()) {
               this.updateBoostPitch();
            }
         }
      }
   }

   private boolean isEarlyNcpBoost() {
      if (this.module.nCPBoostSetting.getValue()) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         int fallFlyingTicks = player.getFallFlyingTicks();
         if (fallFlyingTicks <= 5) {
            return true;
         }
      }
      return false;
   }

   private void updateBoostPitch() {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      int randomSample = random.nextInt(6);
      float pitchJitter;
      if (RotationManager.isMovementSyncEnabled()) {
         float jitterStep = AntiCheatModule.ROTATION_JITTER_STEP;
         pitchJitter = jitterStep * (-3 + randomSample);
      } else {
         pitchJitter = 0.0F;
      }
      if (MovementPhysics.isLocalPlayerMoving()) {
         RotationManager rotationForMovement = ClientContext.rotationManager;
         float[] movementRotation = new float[2];
         movementRotation[0] = this.movementYaw;
         LocalPlayer playerForMovementPitch = ClientAccess.zioMinecraft.player;
         movementRotation[1] = playerForMovementPitch.getXRot();
         rotationForMovement.requestRotation(movementRotation, 10000, true);
         ClientContext.rotationManager.forceRotationOverride();
      }
      if (!RotationManager.isMovementSyncEnabled()) {
         this.targetPitch = ClientAccess.zioMinecraft.player.getXRot();
      } else {
         float previousTargetPitch = this.targetPitch;
         LocalPlayer playerForPitchDifference = ClientAccess.zioMinecraft.player;
         float pitchDifference = previousTargetPitch - playerForPitchDifference.getXRot();
         float absolutePitchDifference = Math.abs(pitchDifference);
         if (absolutePitchDifference < 15.0F) {
            this.targetPitch = ClientAccess.zioMinecraft.player.getXRot();
         } else {
            float targetPitchForDirection = this.targetPitch;
            LocalPlayer playerForPitchDirection = ClientAccess.zioMinecraft.player;
            if (targetPitchForDirection > playerForPitchDirection.getXRot()) {
               float targetBeforeDecrease = this.targetPitch;
               this.targetPitch = targetBeforeDecrease - 10.0F;
            } else {
               float targetBeforeIncrease = this.targetPitch;
               this.targetPitch = targetBeforeIncrease + 10.0F;
            }
         }
      }
      if (this.module.responsiveSetting.getValue()) {
         float desiredPitch = this.targetPitch + pitchJitter;
         if (ClientAccess.zioMinecraft.player.input.keyPresses.jump()) {
            float jumpPitch;
            if (RotationManager.isMovementSyncEnabled()) {
               jumpPitch = -45.0F;
            } else {
               jumpPitch = -60.0F;
            }
            desiredPitch = jumpPitch + pitchJitter;
         } else {
            if (ClientAccess.zioMinecraft.player.input.keyPresses.shift()) {
               desiredPitch = 60.0F + pitchJitter;
            }
         }
         desiredPitch = Mth.clamp(
            desiredPitch,
            (float)(-NINETY_DEGREES),
            (float)NINETY_DEGREES
         );
         if (ClientAccess.zioMinecraft.player.getXRot() != desiredPitch) {
            RotationManager rotationForPitch = ClientContext.rotationManager;
            float[] pitchRotation = new float[2];
            pitchRotation[0] = this.movementYaw;
            pitchRotation[1] = desiredPitch;
            rotationForPitch.requestRotation(pitchRotation, 1000001, true);
            ClientContext.rotationManager.forceRotationOverride();
            this.targetPitch = desiredPitch;
         }
      }
      boostWindow: {
         if (this.targetPitch < 0.0F) {
            if (this.module.nCPBoostSetting.getValue()) {
               int flightTicks = ClientAccess.zioMinecraft.player.getFallFlyingTicks();
               if (this.module.isDurabilityBypassActive()) {
                  flightTicks = ClientAccess.zioMinecraft.player.tickCount;
               }
               int boostPhase = flightTicks % 6;
               if (boostPhase > 2) {
                  break boostWindow;
               }
               if (flightTicks >= 1) {
                  if (flightTicks < 20) {
                     break boostWindow;
                  }
               }
               RotationManager rotationForBoost = ClientContext.rotationManager;
               float[] boostRotation = new float[2];
               boostRotation[0] = this.movementYaw;
               boostRotation[1] = 0.0F;
               rotationForBoost.requestRotation(boostRotation, 100002, true);
               ClientContext.rotationManager.forceRotationOverride();
            }
         }
         return;
      }
      this.boostPitchInitialized = true;
   }

   @Override
   public void handleIncomingPacket(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundSetHealthPacket healthPacket) {
         float healthAndSaturation = healthPacket.getHealth();
         healthAndSaturation += healthPacket.getSaturation();
         if (healthAndSaturation < EntityUtils.getPlayerHealthWithAbsorption()) {
            this.onDisable();
            this.onEnable();
         }
      }
   }

   public static void bindModuleReferences(ModuleRegistry registry) {
      antiCheatModule = (AntiCheatModule)registry.getModule(
         AntiCheatModule.class
      );
   }
}
