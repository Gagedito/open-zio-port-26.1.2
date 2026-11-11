/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.utils.math.MathConstants;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.zio.client.ClientAccess;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;

public final class ClipCommand extends Command {

   public ClipCommand() {
      super("clip");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      LiteralArgumentBuilder verticalCommand = Command.literal("v");
      ArgumentType verticalType = FloatArgumentType.floatArg();
      RequiredArgumentBuilder verticalValue = Command.argument("value", verticalType);
      com.mojang.brigadier.Command<Object> verticalAction = context -> {
         Float argument = context.getArgument("value", Float.class);
         float requestedDistance = argument;
         float minimumDistance = -1e+04F;
         float maximumDistance = 1e+04F;
         float distance = Math.clamp(requestedDistance, minimumDistance, maximumDistance);
         sendAdditionalMovementPackets(distance, false);
         ChatUtils.printChat(Component.literal("Clipped ").append(this.formatText("%.1f".formatted(distance))).append(" blocks vertically."), ChatUtils.DEFAULT_SIGNATURE);
         LocalPlayer playerForPassengerCheck = ClientAccess.zioMinecraft.player;
         if (playerForPassengerCheck.isPassenger()) {
            LocalPlayer playerForVehicle = ClientAccess.zioMinecraft.player;
            Entity vehicle = playerForVehicle.getVehicle();
            double x = ClientAccess.zioMinecraft.player.getVehicle().getX();
            double y = ClientAccess.zioMinecraft.player.getVehicle().getY() + distance;
            double z = ClientAccess.zioMinecraft.player.getVehicle().getZ();
            vehicle.setPos(x, y, z);
         } else {
            LocalPlayer player = ClientAccess.zioMinecraft.player;
            double x = player.getX();
            double y = ClientAccess.zioMinecraft.player.getY() + distance;
            double z = ClientAccess.zioMinecraft.player.getZ();
            player.setPos(x, y, z);
            double packetX = ClientAccess.zioMinecraft.player.getX();
            double packetY = ClientAccess.zioMinecraft.player.getY();
            double packetZ = ClientAccess.zioMinecraft.player.getZ();
            PacketSender.sendPosition(packetX, packetY, packetZ, true);
         }
         return 1;
      };
      ArgumentBuilder verticalArguments = verticalValue.executes(verticalAction);
      ArgumentBuilder verticalSubcommand = verticalCommand.then(verticalArguments);
      LiteralArgumentBuilder parent = (LiteralArgumentBuilder)builder.then(verticalSubcommand);

      LiteralArgumentBuilder horizontalCommand = Command.literal("h");
      ArgumentType horizontalType = FloatArgumentType.floatArg();
      RequiredArgumentBuilder horizontalValue = Command.argument("value", horizontalType);
      com.mojang.brigadier.Command<Object> horizontalAction = context -> {
         Float argument = context.getArgument("value", Float.class);
         float requestedDistance = argument;
         float minimumDistance = -3e+07F;
         float maximumDistance = 3e+07F;
         float distance = Math.clamp(requestedDistance, minimumDistance, maximumDistance);
         LocalPlayer playerForCosine = ClientAccess.zioMinecraft.player;
         double cosineYaw = playerForCosine.getYRot()
            + MathConstants.NINETY_DEGREES;
         double cosine = Math.cos(Math.toRadians(cosineYaw));
         double sineYaw = ClientAccess.zioMinecraft.player.getYRot()
            + MathConstants.NINETY_DEGREES;
         double sine = Math.sin(Math.toRadians(sineYaw));
         sendAdditionalMovementPackets(distance, true);
         ChatUtils.printChat(Component.literal("Clipped ").append(this.formatText("%.1f".formatted(distance))).append(" blocks horizontally."), ChatUtils.DEFAULT_SIGNATURE);
         if (ClientAccess.zioMinecraft.player.isPassenger()) {
            LocalPlayer playerForVehicle = ClientAccess.zioMinecraft.player;
            Entity vehicle = playerForVehicle.getVehicle();
            double x = ClientAccess.zioMinecraft.player.getVehicle().getX() + distance * cosine;
            double y = ClientAccess.zioMinecraft.player.getVehicle().getY();
            double z = ClientAccess.zioMinecraft.player.getVehicle().getZ() + distance * sine;
            vehicle.setPos(x, y, z);
         } else {
            LocalPlayer player = ClientAccess.zioMinecraft.player;
            double x = player.getX() + distance * cosine;
            double y = ClientAccess.zioMinecraft.player.getY();
            double z = ClientAccess.zioMinecraft.player.getZ() + distance * sine;
            player.setPos(x, y, z);
            double packetX = ClientAccess.zioMinecraft.player.getX();
            double packetY = ClientAccess.zioMinecraft.player.getY();
            double packetZ = ClientAccess.zioMinecraft.player.getZ();
            PacketSender.sendPosition(packetX, packetY, packetZ, true);
         }
         return 1;
      };
      ArgumentBuilder horizontalArguments = horizontalValue.executes(horizontalAction);
      ArgumentBuilder horizontalSubcommand = horizontalCommand.then(horizontalArguments);
      parent.then(horizontalSubcommand);
   }

   private void sendAdditionalMovementPackets(float distance, boolean horizontal) {
      float step;
      if (horizontal) {
         step = 9.0F;
      } else {
         step = 1e+01F;
      }
      float steps = distance / step;
      int packetCount = (int)Math.ceil(Math.abs(steps));
      if (packetCount > (20)) {
         packetCount = 1;
      }

      for (int packetIndex = 0; packetIndex < packetCount - 1; packetIndex++) {
         LocalPlayer playerForPassengerCheck = ClientAccess.zioMinecraft.player;
         if (playerForPassengerCheck.isPassenger()) {
            LocalPlayer playerForVehicle = ClientAccess.zioMinecraft.player;
            Entity vehicle = playerForVehicle.getVehicle();
            Packet packet = ServerboundMoveVehiclePacket.fromEntity(vehicle);
            PacketSender.send(packet);
         } else {
            Packet packet = new StatusOnly(true, ClientAccess.zioMinecraft.player.horizontalCollision);
            PacketSender.send(packet);
         }
      }
   }
}
