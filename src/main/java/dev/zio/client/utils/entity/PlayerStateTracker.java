/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.entity;

import dev.zio.client.integrations.ProtocolCompatibility;

import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.math.MathUtils;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.AbstractClientPlayerTickEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.SneakStateChangeEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.bus.EventBus;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public final class PlayerStateTracker implements ClientAccess {
   private double packetX;
   private double packetY;
   private double packetZ;
   private boolean packetOnGround;
   private boolean sneaking;
   private Input lastSentInput;
   private double lastGroundedY;
   private double currentTickY;
   private double previousTickY;
   private int groundedTicks;
   private int airborneTicks;
   private float fallDistance;
   private float[] packetRotations;
   private Vec3 tickDisplacement;
   private final ElapsedTimer fallFlyingTimer;

   public boolean isPacketOnGround() {
      return this.packetOnGround;
   }

   public double getPreviousTickY() {
      return this.previousTickY;
   }

   public float[] getPacketRotations() {
      return this.packetRotations;
   }

   public double getLastGroundedY() {
      return this.lastGroundedY;
   }

   public boolean isSneaking() {
      return this.sneaking;
   }

   public Input getLastSentInput() {
      return this.lastSentInput;
   }

   public ElapsedTimer getFallFlyingTimer() {
      return this.fallFlyingTimer;
   }

   public double getCurrentTickY() {
      return this.currentTickY;
   }

   public Vec3 getTickDisplacement() {
      return this.tickDisplacement != null ? this.tickDisplacement : ClientAccess.zioMinecraft.player.getDeltaMovement();
   }

   public Vec3 getPacketPosition() {
      return new Vec3(
         this.packetX,
         this.packetY,
         this.packetZ
      );
   }

   public int getGroundedTicks() {
      return this.groundedTicks;
   }

   public float getFallDistance() {
      return this.fallDistance;
   }

   public int getAirborneTicks() {
      return this.airborneTicks;
   }

   public PlayerStateTracker() {
      this.packetRotations = new float[2];
      this.fallFlyingTimer = new ElapsedTimer();
      ClientAccess.eventBus.subscribe(this);
   }

   public void setFallDistance(float distance) {
      this.fallDistance = distance;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {

      if (PlayerStateUtils.isFallFlying()) {
         this.fallFlyingTimer.reset();
      }

      this.previousTickY = this.currentTickY;
      LocalPlayer heightPlayer = ClientAccess.zioMinecraft.player;
      this.currentTickY = heightPlayer.getY();
      this.tickDisplacement = MathUtils.getPositionDelta(heightPlayer);
      LocalPlayer flightPlayer = ClientAccess.zioMinecraft.player;

      flightPlayer.isFallFlying();
      LocalPlayer groundPlayer = ClientAccess.zioMinecraft.player;
      if (!groundPlayer.onGround() && !this.packetOnGround) {
         this.groundedTicks = 0;
         this.airborneTicks++;
      } else {
         this.airborneTicks = 0;
         this.groundedTicks++;
         LocalPlayer groundedHeightPlayer = ClientAccess.zioMinecraft.player;
         this.lastGroundedY = groundedHeightPlayer.getY();
      }
   }

   public void runSneaking(Runnable action) {
      PlayerStateTracker tracker = ClientContext.playerStateTracker;
      if (!tracker.isSneaking()) {
         this.setSneaking(true);
         action.run();
         this.setSneaking(false);
      } else {
         action.run();
      }
   }

   public void setSneaking(boolean requestedSneaking) {
      EventBus eventBus = ClientAccess.eventBus;
      SneakStateChangeEvent event = new SneakStateChangeEvent(requestedSneaking);
      SneakStateChangeEvent postedEvent = (SneakStateChangeEvent)eventBus.post(event);
      if (!postedEvent.isCancelled()) {
         ProtocolCompatibility.setSneaking(requestedSneaking);
      }
   }

   public void runWithoutSneaking(Runnable action) {
      PlayerStateTracker tracker = ClientContext.playerStateTracker;
      if (tracker.isSneaking()) {
         this.setSneaking(false);
         action.run();
         this.setSneaking(true);
      } else {
         action.run();
      }
   }

   @EventHandler(priority = -9999)
   private void onConnectionDoSendPacketEvent(
      PacketEvent.PostSend event
   ) {
      if (event.isCancelled()) {
         return;
      }
      if (event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket) {
         this.packetOnGround = movementPacket.isOnGround();
         if (movementPacket.hasPosition()) {
            this.packetX = movementPacket.getX(0.0);
            this.packetY = movementPacket.getY(0.0);
            this.packetZ = movementPacket.getZ(0.0);
         }

         if (movementPacket.hasRotation()) {

            float[] rotations = new float[2];
            rotations[0] = movementPacket.getYRot(0.0F);
            rotations[1] = movementPacket.getXRot(0.0F);
            this.packetRotations = rotations;
         }
      }

      if (event.getPacket() instanceof ServerboundPlayerInputPacket inputPacket) {
         Input decodedInput;
         try {
            decodedInput = inputPacket.input();
         } catch (Throwable failure) {
            throw new MatchException(failure.toString(), failure);
         }

         this.sneaking = decodedInput.shift();
         this.lastSentInput = decodedInput;
      }

   }

   @EventHandler
   private void onAbstractClientPlayerTickEvent(AbstractClientPlayerTickEvent event) {
      double previousY = ClientAccess.zioMinecraft.player.yo;
      LocalPlayer heightPlayer = ClientAccess.zioMinecraft.player;
      double downwardDistance = previousY - heightPlayer.getY();
      LocalPlayer groundPlayer = ClientAccess.zioMinecraft.player;
      if (!groundPlayer.onGround()) {
         LocalPlayer liquidPlayer = ClientAccess.zioMinecraft.player;
         if (!PlayerStateUtils.isInLiquid(liquidPlayer)) {
            if (downwardDistance > 0.0) {
               this.fallDistance += (float)downwardDistance;
            }

            return;
         }
      }

      this.fallDistance = 0.0F;
   }

}
