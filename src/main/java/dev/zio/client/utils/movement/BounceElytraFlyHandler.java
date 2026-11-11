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
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.math.Vec3Utils;
import dev.zio.client.utils.network.PacketSender;

import static dev.zio.client.utils.math.MathConstants.NINETY_DEGREES;

import static dev.zio.client.utils.math.MathConstants.FORTY_FIVE_DEGREES;

import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.LivingEntityRotationEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.modules.movement.ObstaclePasserModule;

import dev.zio.mixin.ServerboundMovePlayerPacketAccessor;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BounceElytraFlyHandler extends ElytraFlyModeHandler {

   private static ObstaclePasserModule obstaclePasserModule;
   private final ElapsedTimer normalFlightRetryTimer = new ElapsedTimer();
   private final Queue<Packet<?>> queuedPackets = new ArrayDeque<>();
   private int bounceCooldownTicks;
   private int bounceInputTicks;
   private boolean durabilityBypassAtTickStart;
   private boolean fallFlyingAtTickStart;
   private boolean diagonalPacketPending;
   private float automaticPitch = 90.0F;

   private void flushQueuedPackets() {
      if (this.isWorldUnavailable()) {
         this.queuedPackets.clear();
      } else {
         while (!this.queuedPackets.isEmpty()) {
            Packet<?> packet = this.queuedPackets.poll();
            if (packet == null) {
               break;
            }
            PacketSender.sendDirect(packet);
         }
      }
   }

   public BounceElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void onEnable() {
      this.bounceInputTicks = 0;
      this.diagonalPacketPending = this.module.diagonalSetting.getValue()
         && this.module.packetSetting.getValue();
   }

   @Override
   public void onDisable() {
      ClientAccess.zioMinecraft.options.keyJump.setDown(false);
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      player.setSprinting(false);
      this.flushQueuedPackets();
   }

   private boolean canApplyBounce() {
      return this.durabilityBypassAtTickStart || this.module.isElytraEquipped();
   }

   @Override
   public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
      if (this.diagonalPacketPending) {
         return;
      }
      this.fallFlyingAtTickStart = PlayerStateUtils.isFallFlying();
      if (!this.isObstaclePasserActive() && this.canApplyBounce()
         && !this.module.isItemUseSlowingFlight()) {
         ClientContext.inputOverrides.requestJump(this.bounceInputTicks != 2);
         ClientContext.inputOverrides.requestForward(true);
         ClientContext.inputOverrides.requestSprint(true);
         ClientContext.inputOverrides.requestForwardMovement(1.0F);
         ClientAccess.zioMinecraft.player.setSprinting(true);
         this.bounceInputTicks++;
      } else {
         this.bounceInputTicks = 0;
      }

      this.durabilityBypassAtTickStart = this.module.isDurabilityBypassActive();
      if (ClientAccess.zioMinecraft.player.isFallFlying()) {
         int groundedTicks = ClientContext.playerStateTracker.getGroundedTicks();
         if (groundedTicks > 5) {
            ClientAccess.zioMinecraft.player.stopFallFlying();
            return;
         }
      }

      boolean bounceAllowed = this.canApplyBounce();
      if (PlayerStateUtils.isInLiquid(ClientAccess.zioMinecraft.player)) {
         return;
      }
      if (PlayerStateUtils.isInCobweb(ClientAccess.zioMinecraft.player) || this.module.isItemUseSlowingFlight()) {
         return;
      }
      if (this.isObstaclePasserActive() || !bounceAllowed) {
         return;
      }
      if (!PlayerStateUtils.isFallFlying()) {
         int takeoffGroundedTicks = ClientContext.playerStateTracker.getGroundedTicks();
         if (takeoffGroundedTicks < 3 && !this.durabilityBypassAtTickStart) {
            ClientAccess.zioMinecraft.player.stopFallFlying();
            PacketSender.sendStartFallFlying();
         }
      }
      if (!ClientAccess.zioMinecraft.player.horizontalCollision && MovementPhysics.isLocalPlayerMoving()) {
         ClientAccess.zioMinecraft.player.setSprinting(true);
         if (ClientAccess.zioMinecraft.player.isFallFlying()) {
            ClientAccess.zioMinecraft.player.setShiftKeyDown(false);
         }
      }
      Boolean pitchLocked = this.module.pitchLockSetting.getValue();
      if (pitchLocked) {
         Boolean silent = this.module.silentSetting.getValue();
         if (!silent) {
            LocalPlayer pitchPlayer = ClientAccess.zioMinecraft.player;
            Float pitchValue = this.getPitch();
            float configuredPitch = pitchValue;
            pitchPlayer.setXRot(configuredPitch);
         }
      }
   }

   @Override
   public void handleOutgoingPacket(PacketEvent.Send event) {
      if (this.isObstaclePasserActive() || this.diagonalPacketPending
         || !this.fallFlyingAtTickStart || !this.canApplyBounce()) {
         return;
      }
      if (event.getPacket() instanceof ServerboundMovePlayerPacketAccessor packetAccess) {
         Boolean silent = this.module.silentSetting.getValue();
         if (silent) {
            Boolean pitchLocked = this.module.pitchLockSetting.getValue();
            if (pitchLocked && !this.module.isItemUseSlowingFlight()) {
               Float configuredPitch = this.getPitch();
               packetAccess.setPitch(configuredPitch);
            }
         }
      }
      Boolean compensate = this.module.compensateSetting.getValue();
      if (compensate && !this.module.isItemUseSlowingFlight()) {
         if (!(event.getPacket() instanceof ServerboundPlayerCommandPacket)
            && !(event.getPacket() instanceof ServerboundPongPacket)) {
            Queue<Packet<?>> packetQueue = this.queuedPackets;
            Packet<?> packetToQueue = event.getPacket();
            packetQueue.add(packetToQueue);
            event.cancel();
         }
      }
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      Boolean packetMode = this.module.packetSetting.getValue();
      if (packetMode) {
         if (this.diagonalPacketPending) {
            Vec3 bounceTarget = this.getBounceTarget();
            Vec3 currentPosition = ClientAccess.zioMinecraft.player.position();

            float targetDistance = Vec3Utils.horizontalDistance(bounceTarget, currentPosition);

            float clampedDistance = Mth.clamp(targetDistance, 0.0F, 0.1F);
            double distanceAsDouble = clampedDistance;

            if (distanceAsDouble < 0.1D) {
               this.diagonalPacketPending = false;
               event.setHorizontalVelocity(0.0D, 0.0D);
               LocalPlayer playerToPosition = ClientAccess.zioMinecraft.player;
               double targetX = bounceTarget.x;
               double currentHeight = ClientAccess.zioMinecraft.player.getY();
               double targetZ = bounceTarget.z;
               playerToPosition.setPos(targetX, currentHeight, targetZ);
            } else {
               Vec3 playerPosition = ClientAccess.zioMinecraft.player.position();
               Vec3 targetDelta = bounceTarget.subtract(playerPosition);
               Vec3 targetDirection = targetDelta.normalize();
               Vec3 step = targetDirection.scale(0.11D);
               event.setHorizontalVelocity(0.0D, 0.0D);
               double stepZ = step.z;
               double stepX = step.x;
               event.setHorizontalVelocity(stepX, stepZ);
            }
         } else {
            if (ClientContext.serverStateManager.hasElapsedSinceSetback(200L)) {
               if (!PlayerStateUtils.isHungerLimited()) {
                  if (PlayerStateUtils.isFallFlying()) {
                     double displaySpeed = ClientContext.movementSpeedTracker.getDisplaySpeed();
                     if (ClientAccess.zioMinecraft.player.onGround()) {
                        Integer maximumBoost = this.module.maxBoostSetting.getValue();
                        if (displaySpeed < maximumBoost.intValue()) {
                           if (displaySpeed >= 97.0D) {
                              event.setY(0.0D);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void handlePlayerTick(LocalPlayerTickEvent event) {
      boolean canProcessPackets = !this.isObstaclePasserActive() || this.queuedPackets.isEmpty();
      if (canProcessPackets && !this.diagonalPacketPending) {

         if (!this.module.isItemUseSlowingFlight()) {
            Boolean packetMode = this.module.packetSetting.getValue();
            if (packetMode) {
               Boolean diagonal = this.module.diagonalSetting.getValue();
               if (diagonal) {
                  float playerYaw = ClientAccess.zioMinecraft.player.getYRot();

                  float yawSteps = (playerYaw + 1.0F) / FORTY_FIVE_DEGREES;

                  float diagonalYaw = Math.round(yawSteps) * FORTY_FIVE_DEGREES;

                  if (diagonalYaw % NINETY_DEGREES == 0.0F) {
                     diagonalYaw += FORTY_FIVE_DEGREES;
                  }

                  ClientAccess.zioMinecraft.player.setYRot(diagonalYaw);
               }
            }

            if (ClientAccess.zioMinecraft.player.isFallFlying()) {
               ClientAccess.zioMinecraft.player.avatarState().walkDist = 0.0F;
            }

            if (!PlayerStateUtils.isInLiquid(ClientAccess.zioMinecraft.player)) {
               if (!PlayerStateUtils.isInCobweb(ClientAccess.zioMinecraft.player)) {
                  Boolean compensate = this.module.compensateSetting.getValue();
                  if (compensate) {
                     if (this.bounceCooldownTicks == 0) {
                        this.flushQueuedPackets();
                     }
                  } else {
                     if (!this.queuedPackets.isEmpty()) {
                        this.flushQueuedPackets();
                     }
                  }

                  return;
               }
            }

            this.flushQueuedPackets();
         }
      } else {
         this.flushQueuedPackets();
      }
   }

   @Override
   public void handleClientTick(MinecraftRunTickEvent event) {
      if (!this.isWorldUnavailable()) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         if (!player.isFallFlying()) {
            if (this.normalFlightRetryTimer.hasElapsedAndReset(10L) && this.bounceInputTicks >= 4) {
               this.module.tryStartNormalFlight();
            }
         }
      }
   }

   @Override
   public void handleTravelRotation(LivingEntityRotationEvent event) {
      if (this.canApplyBounce()) {
         if (!this.isObstaclePasserActive()
            && !this.diagonalPacketPending) {
            Boolean pitchLocked = this.module.pitchLockSetting.getValue();
            if (pitchLocked) {

               if (!this.module.isItemUseSlowingFlight()) {
                  if (this.module.autoPitchSetting.getValue() && !event.isJump()) {
                     this.automaticPitch = ClientAccess.zioMinecraft.player.getDeltaMovement().y > -0.2D ? 90.0F : 2.0F;
                  }
                  Float pitchValue = this.getPitch();
                  float configuredPitch = pitchValue;
                  event.setPitch(configuredPitch);
                  event.cancel();
               }
            }

            return;
         }
      }
   }

   @Override
   public void handleSendPosition(LocalPlayerSendPositionEvent event) {
      if (this.diagonalPacketPending) {
         if (event.getStage() != EventStage.Post) {
            Vec3 bounceTarget = this.getBounceTarget();
            float[] targetRotations = RotationManager.getRotationsToPosition(bounceTarget);
            event.setRotation(targetRotations);
         }
      } else {
         cooldownUpdate:
         if (event.getStage() == EventStage.Pre) {
            double deltaX = ClientAccess.zioMinecraft.player.getX() - ClientAccess.zioMinecraft.player.xo;

            double deltaY = ClientAccess.zioMinecraft.player.getY() - ClientAccess.zioMinecraft.player.yo;

            double deltaZ = ClientAccess.zioMinecraft.player.getZ() - ClientAccess.zioMinecraft.player.zo;

            Vec3 playerPosition = ClientAccess.zioMinecraft.player.position();
            Vec3 projectedPosition = playerPosition.add(deltaX, deltaY, deltaZ);
            ClientLevel collisionLevel = ClientAccess.zioMinecraft.level;
            LocalPlayer collisionPlayer = ClientAccess.zioMinecraft.player;

            AABB collisionBox = AABB.ofSize(projectedPosition, 0.2D, 0.2D, 0.2D);
            boolean collisionFree = collisionLevel.noBlockCollision(collisionPlayer, collisionBox);
            if (collisionFree) {
               if (!ClientAccess.zioMinecraft.player.onGround()) {
                  if (this.bounceCooldownTicks > 0) {
                     this.bounceCooldownTicks--;
                  }
                  break cooldownUpdate;
               }
            }

            this.bounceCooldownTicks = 2;
         }

         if (!this.module.isItemUseSlowingFlight()) {
            if (this.canApplyBounce()) {
               if (!this.isObstaclePasserActive()) {
                  if (event.getStage() != EventStage.Post) {
                     Boolean pitchLock = this.module.pitchLockSetting.getValue();
                     if (pitchLock) {
                        Boolean silent = this.module.silentSetting.getValue();
                        if (silent) {
                           Float configuredPitch = this.getPitch();
                           float pitch = configuredPitch;
                           event.setPitch(pitch);
                        }
                     }

                     return;
                  }
               }
            }
         }
      }
   }

   @Override
   public void handleIncomingPacket(PacketEvent.Receive event) {
      if (!(event.getPacket() instanceof ClientboundPlayerCombatKillPacket)) {
         if (!(event.getPacket() instanceof ClientboundPlayerPositionPacket)) {
            return;
         }
      }

      var minecraft = ClientAccess.zioMinecraft;
      Runnable flushTask = this::flushQueuedPackets;
      minecraft.execute(flushTask);
   }

   private boolean isObstaclePasserActive() {
      return obstaclePasserModule != null && obstaclePasserModule.isPassingObstacles();
   }

   private float getPitch() {
      return this.module.autoPitchSetting.getValue() ? this.automaticPitch : this.module.pitchSetting.getValue();
   }

   public Vec3 getBounceTarget() {
      Direction openSide = this.getOpenHorizontalSide();
      BlockPos localPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      if (openSide == null) {
         return localPosition.getBottomCenter();
      }
      return localPosition.relative(openSide).getBottomCenter();
   }

   public Direction getOpenHorizontalSide() {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      BlockPos playerPosition = player.blockPosition();
      int blockedSides = 0;
      Direction lastOpenSide = null;
      Plane horizontal = Plane.HORIZONTAL;
      Iterator<Direction> sides = horizontal.iterator();
      while (sides.hasNext()) {
         Direction side = sides.next();
         ClientLevel level = ClientAccess.zioMinecraft.level;
         BlockPos adjacentPosition = playerPosition.relative(side);
         BlockState adjacentState = level.getBlockState(adjacentPosition);
         if (!adjacentState.canBeReplaced()) {
            blockedSides++;
         } else {
            lastOpenSide = side;
         }
      }
      return blockedSides == 0 ? null : lastOpenSide;
   }

   public boolean isDiagonalPacketPending() {
      return this.diagonalPacketPending;
   }

   public static void bindModuleReferences(ModuleRegistry registry) {
      obstaclePasserModule = (ObstaclePasserModule)registry.getModule(ObstaclePasserModule.class);
   }
}
