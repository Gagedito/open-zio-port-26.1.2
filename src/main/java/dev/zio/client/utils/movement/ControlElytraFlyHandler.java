/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

import dev.zio.client.events.EventStage;
import dev.zio.client.managers.RotationManager;

import static dev.zio.client.utils.math.MathConstants.NINETY_DEGREES;

import static dev.zio.client.utils.math.MathConstants.FORTY_FIVE_DEGREES;

import dev.zio.client.utils.InputUtils;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.LivingEntityRotationEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.combat.AutoMaceModule;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.modules.movement.FireworksModule;
import dev.zio.client.modules.movement.ObstaclePasserModule;
import dev.zio.client.utils.math.MathUtils;
import dev.zio.mixin.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public class ControlElytraFlyHandler extends ElytraFlyModeHandler {
   private static ObstaclePasserModule obstaclePasserModule;
   private static FireworksModule fireworksModule;
   private static AutoMaceModule autoMaceModule;
   private Vec3 savedVelocity = Vec3.ZERO;
   private int verticalInputTicks;
   private double flightSpeed;
   private long accelerationStartTimeMillis;
   private long verticalAccelerationStartTimeMillis;
   private boolean verticalVelocityApplied;
   private boolean verticalInputOverride;
   private int verticalInputDirection;

   public ControlElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void onEnable() {
      this.flightSpeed = 0.0;
      this.verticalAccelerationStartTimeMillis = this.accelerationStartTimeMillis = System.currentTimeMillis();
   }

   public void setVerticalInputOverride(int direction) {
      this.verticalInputOverride = true;
      this.verticalInputDirection = direction;
   }

   private float computeFlightPitch() {
      float pitch = ClientAccess.zioMinecraft.player.getXRot();
      if (this.isVerticalInputRequested()) {
         pitch = NINETY_DEGREES;
         if (MovementPhysics.isLocalPlayerMoving()) {
            pitch = FORTY_FIVE_DEGREES;
         }
         if (ClientAccess.zioMinecraft.player.input.keyPresses.jump()) {
            pitch = -pitch;
         }
      }
      return pitch;
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      if (!this.module.isFallFlyingWithoutOverride()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
         return;
      }
      if (this.module.isManualVerticalControlActive()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
         return;
      }
      if (!this.isFireworkMovementAllowed()) {
         return;
      }
      if (this.isObstaclePasserPathActive()) {
         return;
      }
      if (!MovementPhysics.isLocalPlayerMoving()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
      }
      float verticalSpeed = this.module.vSpeedSetting.getValue();
      if (this.module.accelerateSetting.getValue()) {
         double targetHorizontalSpeed = this.module.flightSpeedSetting.getValue();
         double minimumHorizontalSpeed = this.module.accelMinSetting.getValue();
         double horizontalAccelerationDuration = this.module.accelTimeSetting.getValue();
         long horizontalAccelerationStart = this.accelerationStartTimeMillis;
         this.flightSpeed = (float)MovementPhysics.accelerateTo(
            targetHorizontalSpeed, minimumHorizontalSpeed, horizontalAccelerationDuration, horizontalAccelerationStart
         );
         double targetVerticalSpeed = verticalSpeed;
         double minimumVerticalSpeed = this.module.verAccelMinSetting.getValue();
         double verticalAccelerationDuration = this.module.accelTimeSetting.getValue();
         long verticalAccelerationStart = this.verticalAccelerationStartTimeMillis;
         verticalSpeed = (float)MovementPhysics.accelerateTo(
            targetVerticalSpeed, minimumVerticalSpeed, verticalAccelerationDuration, verticalAccelerationStart
         );
      } else {
         this.flightSpeed = this.module.flightSpeedSetting.getValue();
      }
      this.flightSpeed += fireworksModule.getAdditionalBoost(false);
      if (this.verticalInputTicks == 0) {
         if (ClientAccess.zioMinecraft.player.input.keyPresses.jump()) {
            if (this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.STRICT) {
               double speedBeforeLimit = this.flightSpeed;
               if (speedBeforeLimit >= 2.0D) {
                  this.flightSpeed = 2.0D;
               }
            }
         }
         double horizontalSpeed = this.flightSpeed;
         double[] horizontalMotion = MovementPhysics.applyHorizontalMotion(
            event, horizontalSpeed
         );
         if (autoMaceModule.isToggled()) {
            if (autoMaceModule.isFlightControlActive()) {
               float[] maceRotation = autoMaceModule.getFlightRotation();
               float maceSpeedMultiplier;
               if (maceRotation[1] >= 0.0F) {
                  maceSpeedMultiplier = 1.0F;
               } else {
                  maceSpeedMultiplier = 1.0F - (-maceRotation[1]) / 90.0F;
               }
               float maceYaw = maceRotation[0];
               ClientInput maceMovementInput = InputUtils.createInput();
               double maceRequestedSpeed = this.flightSpeed * maceSpeedMultiplier;
               double maceSpeedCap = 1.0D;
               double maceLimitedSpeed = Math.min(maceRequestedSpeed, maceSpeedCap);
               horizontalMotion = MovementPhysics.getHorizontalMotion(
                  maceYaw, maceMovementInput, maceLimitedSpeed
               );
               double maceMotionX = horizontalMotion[0];
               double maceMotionZ = horizontalMotion[1];
               event.setHorizontalVelocity(maceMotionX, maceMotionZ);
            }
         }
         ClientAccess.zioMinecraft.player.setDeltaMovement(
            horizontalMotion[0], ClientAccess.zioMinecraft.player.getDeltaMovement().y, horizontalMotion[1]
         );
         this.savedVelocity = ClientAccess.zioMinecraft.player.getDeltaMovement();
         int playerTickCount = ClientAccess.zioMinecraft.player.tickCount;
         if (playerTickCount % 4 == 0) {
            double motionBeforeDescent = event.getY();
            double descendingMotionY = motionBeforeDescent - 0.0001D;
            event.setY(descendingMotionY);
            ClientAccess.zioMinecraft.player.setDeltaMovement(
               ClientAccess.zioMinecraft.player.getDeltaMovement().add(0.0, -0.0001D, 0.0)
            );
         }
      }
      boolean jumping = ClientAccess.zioMinecraft.player.input.keyPresses.jump();
      boolean sneaking = ClientAccess.zioMinecraft.player.input.keyPresses.shift();
      if (this.verticalInputOverride) {
         jumping = this.verticalInputDirection > 0;
         sneaking = this.verticalInputDirection < 0;
         this.verticalInputOverride = false;
      }
      if (jumping) {
            if (this.module.isFallFlyingWithoutOverride()) {
            if (this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.STRICT) {
               this.verticalInputTicks++;
            }
            if (this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.PLAIN) {
               double ascendingMotionY = verticalSpeed;
               event.setY(ascendingMotionY);
               ClientAccess.zioMinecraft.player.setDeltaMovement(
                  ClientAccess.zioMinecraft.player.getDeltaMovement().with(Axis.Y, verticalSpeed)
               );
            } else {
               if (this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.STRICT) {
                  if (MovementPhysics.isLocalPlayerMoving()) {
                     this.applyVerticalPitchVelocity();
                  }
                  return;
               }
            }
            return;
         }
      }
      if (sneaking) {
         this.verticalInputTicks = 0;
         double descendingMotionY = -verticalSpeed;
         event.setY(descendingMotionY);
         ClientAccess.zioMinecraft.player.setDeltaMovement(
            ClientAccess.zioMinecraft.player.getDeltaMovement().with(Axis.Y, -verticalSpeed)
         );
      } else {
         this.verticalInputTicks = 0;
         double zeroMotionY = 0.0;
         event.setY(zeroMotionY);
         ClientAccess.zioMinecraft.player.setDeltaMovement(
            ClientAccess.zioMinecraft.player.getDeltaMovement().with(Axis.Y, 0.0)
         );
         if (!ClientAccess.zioMinecraft.player.verticalCollision) {
            Float configuredGlide = this.module.glideSetting.getValue();
            if (configuredGlide != 0.0F) {
               ClientAccess.zioMinecraft.player.setDeltaMovement(
                  ClientAccess.zioMinecraft.player.getDeltaMovement().add(0.0, -this.module.glideSetting.getValue(), 0.0)
               );
               double glidingMotionY = ClientAccess.zioMinecraft.player.getDeltaMovement().y;
               event.setY(glidingMotionY);
            }
         }
      }
      return;
   }

   @Override
   public void handleOutgoingPacket(
      PacketEvent.Send event
   ) {
      if (!this.isObstaclePasserPathActive()) {
         if (event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket) {
                  if (this.module.isFallFlyingWithoutOverride()) {
                        if (this.module.isManualVerticalControlActive()) {
                  return;
               }
               if (!this.isFireworkMovementAllowed()) {
                  return;
               }
               if (this.isVerticalInputRequested()) {
                  ServerboundMovePlayerPacketAccessor verticalPacket = (ServerboundMovePlayerPacketAccessor)movementPacket;
                  verticalPacket.setPitch(this.computeFlightPitch());
                  return;
               }
               if (!MovementPhysics.isLocalPlayerMoving()) {
                  return;
               }
               if (this.module.spoofPitchSetting.getValue()) {
                  ServerboundMovePlayerPacketAccessor spoofedPacket = (ServerboundMovePlayerPacketAccessor)movementPacket;
                  Float spoofedPitch = this.module.spoofedPitchSetting.getValue();
                  spoofedPacket.setPitch(spoofedPitch);
               }
               if (this.verticalInputTicks > 0) {
                  ServerboundMovePlayerPacketAccessor ascendingPacket = (ServerboundMovePlayerPacketAccessor)movementPacket;
                  Float ascendingPitch = this.module.vPitchSetting.getValue();
                  ascendingPacket.setPitch(-ascendingPitch);
               }
            }
         }
      }
   }

   @Override
   public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
      if (!this.isObstaclePasserPathActive()) {
            if (this.module.isManualVerticalControlActive()) {
            ClientContext.inputOverrides.requestJump(true);
            ClientContext.inputOverrides.requestForward(true);
            ClientContext.inputOverrides.requestForwardMovement(1.0F);
            return;
         }
      }
   }

   @Override
   public void handlePlayerTick(LocalPlayerTickEvent event) {
      if (!this.module.isFallFlyingWithoutOverride()) {
         this.verticalAccelerationStartTimeMillis = System.currentTimeMillis();
      }
      if (!ClientAccess.zioMinecraft.player.input.keyPresses.jump()) {
         if (!ClientAccess.zioMinecraft.player.input.keyPresses.shift()) {
            this.verticalAccelerationStartTimeMillis = System.currentTimeMillis();
         }
      }
      if (ClientAccess.zioMinecraft.player.input.keyPresses.jump()) {
            if (this.module.isFallFlyingWithoutOverride()) {
            if (!this.verticalVelocityApplied) {
               if (this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.MANUAL) {
                  float yaw = ClientAccess.zioMinecraft.player.getYRot();
                  ClientInput forwardInput = InputUtils.createInput();
                  double horizontalSpeed = this.flightSpeed;
                  double[] motion = MovementPhysics.getHorizontalMotion(
                     yaw, forwardInput, horizontalSpeed
                  );
                  ClientAccess.zioMinecraft.player.push(motion[0], 0.0, motion[1]);
               }
            }
            this.verticalVelocityApplied = true;
            return;
         }
      }
      this.verticalVelocityApplied = false;
   }

   @Override
   public void handleTravelRotation(LivingEntityRotationEvent event) {
      if (this.module.isFallFlyingWithoutOverride()) {
            if (!this.module.isManualVerticalControlActive()) {
            if (this.isFireworkMovementAllowed()) {
               if (this.isObstaclePasserPathActive()) {
                  return;
               }
               if (this.isVerticalInputRequested()) {
                  float pitch = this.computeFlightPitch();
                  event.setPitch(pitch);
               }
               if (!MovementPhysics.isLocalPlayerMoving()) {
                  return;
               }
               float yaw = MovementPhysics.getMovementYaw(ClientAccess.zioMinecraft.player.getYRot());
               event.setYaw(yaw);
               return;
            }
         }
      }
   }

   @Override
   public void handleSendPosition(LocalPlayerSendPositionEvent event) {
      if (this.module.isFallFlyingWithoutOverride()) {
            if (!this.module.isManualVerticalControlActive()) {
            if (this.isFireworkMovementAllowed()) {
               if (event.getStage() == EventStage.Pre) {
                  if (this.isObstaclePasserPathActive()) {
                     return;
                  }
                  if (this.isVerticalInputRequested()) {
                     float pitch = this.computeFlightPitch();
                     event.setPitch(pitch);
                     return;
                  }
                  if (!MovementPhysics.isLocalPlayerMoving()) {
                     return;
                  }
                  float yaw = MovementPhysics.getMovementYaw(ClientAccess.zioMinecraft.player.getYRot());
                  event.setYaw(yaw);
                  if (this.module.spoofPitchSetting.getValue()) {
                     float spoofedPitch = this.module.spoofedPitchSetting.getValue();
                     event.setPitch(spoofedPitch);
                  }
                  if (this.verticalInputTicks > 0) {
                     Float configuredVerticalPitch = this.module.vPitchSetting.getValue();
                     float verticalPitch = -configuredVerticalPitch;
                     event.setPitch(verticalPitch);
                  }
                  return;
               }
            }
         }
      }
   }

   @Override
   public void handleIncomingPacket(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
      }
   }

   private boolean isFireworkMovementAllowed() {
      if (this.module.isFallFlyingWithoutOverride()) {
         if (fireworksModule.autoLaunchSetting.getValue()) {
            if (fireworksModule.isToggled()) {
               ElapsedTimer rocketUseTimer = fireworksModule.rocketUseTimer;
               if (!rocketUseTimer.hasElapsed(500L)) {
                  return true;
               }
               return fireworksModule.isRocketBoostActive();
            }
         }
      }
      return true;
   }

   private boolean isObstaclePasserPathActive() {
      if (obstaclePasserModule == null) {
         return false;
      }
      return obstaclePasserModule.isPathActive();
   }

   private boolean isVerticalInputRequested() {
      if (ClientAccess.zioMinecraft.player.input.keyPresses.jump()) {
         return true;
      }
      return ClientAccess.zioMinecraft.player.input.keyPresses.shift()
         && this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.PLAIN
         && RotationManager.isMovementSyncEnabled();
   }

   private void applyVerticalPitchVelocity() {
      Float anglePitch = this.module.vPitchSetting.getValue();
      float negativeAnglePitch = -anglePitch;
      double pitchRadians = MathUtils.toRadians(negativeAnglePitch);
      Vec3 velocity = this.savedVelocity;
      Float directionPitch = this.module.vPitchSetting.getValue();
      float negativeDirectionPitch = -directionPitch;
      float yaw = ClientAccess.zioMinecraft.player.getYRot();
      Vec3 lookDirection = MathUtils.directionFromPitchYaw(negativeDirectionPitch, yaw);
      double horizontalSpeed = velocity.horizontalDistance();
      double horizontalLookLength = lookDirection.horizontalDistance();
      double lookLength = lookDirection.length();
      double pitchCosine = Math.cos(pitchRadians);
      double squaredPitchCosine = pitchCosine * pitchCosine;
      double lookLengthScale = lookLength / 0.4D;
      double lift = squaredPitchCosine * Math.min(1.0D, lookLengthScale);
      double gravity = 0.08D * (-1.0D + lift * 0.75D);
      velocity = velocity.add(0.0, gravity, 0.0);
      if (pitchRadians < 0.0 && horizontalLookLength > 0.0) {
         double upwardProjection = horizontalSpeed * -Math.sin(pitchRadians);
         double climbAcceleration = upwardProjection * 0.04D;
         double accelerationX = -lookDirection.x * climbAcceleration / horizontalLookLength;
         double accelerationY = climbAcceleration * 3.2D;
         double accelerationZ = -lookDirection.z * climbAcceleration / horizontalLookLength;
         velocity = velocity.add(accelerationX, accelerationY, accelerationZ);
      }
      LocalPlayer playerForVelocity = ClientAccess.zioMinecraft.player;
      double horizontalDrag = 0.99D;
      double verticalDrag = 0.98D;
      double depthDrag = 0.99D;
      Vec3 slowedVelocity = velocity.multiply(horizontalDrag, verticalDrag, depthDrag);
      playerForVelocity.setDeltaMovement(slowedVelocity);
      this.savedVelocity = ClientAccess.zioMinecraft.player.getDeltaMovement();
   }

   public static void bindModuleReferences(ModuleRegistry registry) {
      obstaclePasserModule = (ObstaclePasserModule)registry.getModule(
         ObstaclePasserModule.class
      );
      fireworksModule = (FireworksModule)registry.getModule(
         FireworksModule.class
      );
      autoMaceModule = (AutoMaceModule)registry.getModule(
         AutoMaceModule.class
      );
   }
}
