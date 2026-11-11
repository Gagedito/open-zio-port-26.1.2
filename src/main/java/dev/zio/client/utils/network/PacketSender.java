/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.network;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;

import io.netty.buffer.Unpooled;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

public final class PacketSender implements ClientAccess {

   private PacketSender() {
   }

   public static void sendUseItemOn(InteractionHand hand, BlockHitResult hitResult) {
      send(
         new ServerboundUseItemOnPacket(hand, hitResult, nextPredictionSequence())
      );
   }

   public static void sendUseItem(InteractionHand hand) {
      float yaw = ClientAccess.zioMinecraft.player.getYRot();
      float pitch = ClientAccess.zioMinecraft.player.getXRot();
      sendUseItem(hand, yaw, pitch);
   }

   public static void sendUseItem(
      InteractionHand hand, float yaw, float pitch
   ) {
      send(
         new ServerboundUseItemPacket(hand, nextPredictionSequence(), yaw, pitch)
      );
   }

   public static void sendSwing(InteractionHand hand) {
      send(new ServerboundSwingPacket(hand));
   }

   public static void sendRotation(float yaw, float pitch, boolean onGround) {
      send(
         new Rot(yaw, pitch, onGround, ClientAccess.zioMinecraft.player.horizontalCollision)
      );
   }

   public static void sendRotation(float[] rotation, boolean onGround) {
      if (rotation != null) {
         int angleCount = rotation.length;

         if (angleCount >= 2) {
            float yaw = rotation[0];
            float pitch = rotation[1];
            sendRotation(yaw, pitch, onGround);
         }
      }
   }

   public static void sendPosition(
      double x, double y, double z, boolean onGround
   ) {
      send(
         new Pos(x, y, z, onGround, ClientAccess.zioMinecraft.player.horizontalCollision)
      );
   }

   public static void sendPositionRotation(
      double x, double y, double z, float yaw, float pitch, boolean onGround
   ) {
      send(
         new PosRot(
            x, y, z, yaw, pitch, onGround, ClientAccess.zioMinecraft.player.horizontalCollision
         )
      );
   }

   public static void sendAttack(int entityId) {
      // 26.1.2: ATTACK es un packet dedicado ServerboundAttackPacket(entityId).
      // El record interact (hand/location) solo cubre interact/interact-at; su
      // codec no acepta nulls, asi que el viejo attack "aplanado" con hand=null
      // lanzaba EncoderException al codificar y el servidor integrado cerraba
      // la conexion (kick "Internal Exception ... serverbound/minecraft:interact").
      send(new ServerboundAttackPacket(entityId));
   }

   public static void sendPlayerAction(
      Action action, BlockPos position, Direction direction
   ) {
      int sequenceSnapshot;
      if (action == Action.ABORT_DESTROY_BLOCK) {
         sequenceSnapshot = 0;
      } else {
         sequenceSnapshot = nextPredictionSequence();
      }

      send(new ServerboundPlayerActionPacket(action, position, direction, sequenceSnapshot));
   }

   public static void markImportant(Packet packet) {
      if (packet instanceof ImportantPacket importantPacket) {
         importantPacket.zio$markImportant();
      }
   }

   public static void sendPlayerCommand(net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action action) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      sendPlayerCommand(player, action);
   }

   public static void sendPlayerCommand(
      Entity entity, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action action
   ) {
      send(new ServerboundPlayerCommandPacket(entity, action, 0));
   }

   public static void sendStartFallFlying() {
      net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action action = net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING;
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      sendPlayerCommand(player, action);
   }

   public static void sendSwapOffhand() {
      send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
   }

   public static void sendSelectedSlot(int slot) {
      send(new ServerboundSetCarriedItemPacket(slot));
   }

   public static void send(Packet packet) {
      ClientAccess.zioMinecraft.player.connection.send(packet);
   }

   public static int nextPredictionSequence() {
      return ClientContext.worldStateManager.nextPredictionSequence();
   }

   public static void sendDirect(Packet packet) {
      if (packet != null) {
         Connection connection = ClientAccess.zioMinecraft.player.connection.getConnection();
         if (connection.isConnected()) {
            connection.sentPackets++;
            if (connection.channel.eventLoop().inEventLoop()) {
               connection.channel.writeAndFlush(packet);
            } else {
               connection.channel.eventLoop().execute(() -> connection.channel.writeAndFlush(packet));
            }
         }
      }
   }

}
