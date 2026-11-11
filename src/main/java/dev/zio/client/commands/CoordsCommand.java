/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.ClientAccess;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.settings.options.DimensionType;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;

public final class CoordsCommand extends Command {

   private String formatCoordinates() {
      if (ClientAccess.zioMinecraft.player == null) {
         return "";
      }

      LocalPlayer playerForX = ClientAccess.zioMinecraft.player;
      int x = (int)playerForX.getX();
      LocalPlayer playerForY = ClientAccess.zioMinecraft.player;
      int y = (int)playerForY.getY();
      LocalPlayer playerForZ = ClientAccess.zioMinecraft.player;
      int z = (int)playerForZ.getZ();

      DimensionType dimension = WorldUtils.getDimension();
      String dimensionName = dimension.getDisplayName();
      double randomValue = Math.random();

      double scaledRandom = randomValue * 10000.0D;
      int randomSuffix = (int)Math.floor(scaledRandom);
      return "X: " + x + ", Y: " + y + ", Z: " + z + " in The " + dimensionName + " [" + randomSuffix + "]";
   }

   public CoordsCommand() {
      super("coords");
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {

      PlayerNameArgumentType playerNameType = new PlayerNameArgumentType();
      RequiredArgumentBuilder nameBuilder = Command.argument("name", playerNameType);
      com.mojang.brigadier.Command<Object> whisperAction = context -> {

         String playerName = (String)context.getArgument("name", String.class);

         String coordinates = this.formatCoordinates();
         this.whisperCoordinates(playerName, coordinates);
         return 1;
      };
      ArgumentBuilder nameArgument = nameBuilder.executes(whisperAction);
      LiteralArgumentBuilder clipboardRoot = (LiteralArgumentBuilder)root.then(nameArgument);
      com.mojang.brigadier.Command<Object> clipboardAction = context -> {
            try {
               KeyboardHandler capturedKeyboard = ClientAccess.zioMinecraft.keyboardHandler;

               String coordinates = this.formatCoordinates();
               capturedKeyboard.setClipboard(coordinates);

               MutableComponent confirmation = Component.literal("Your position has been copied to the clipboard.");

               MessageSignature signature = ChatUtils.createSignature(-1);
               ChatUtils.printChat(confirmation, signature);
            } catch (Exception ignoredFailure) {
            }

            return 1;
         };
      clipboardRoot.executes(clipboardAction);
   }

   private void whisperCoordinates(String recipient, String coordinates) {

      String whisperCommand = "w " + String.valueOf(recipient) + " " + String.valueOf(coordinates);
      ClientPacketListener connection = ClientAccess.zioMinecraft.player.connection;
      connection.sendCommand(whisperCommand);
   }

}
