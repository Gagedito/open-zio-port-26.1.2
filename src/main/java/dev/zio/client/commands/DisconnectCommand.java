/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.zio.client.ClientAccess;
import dev.zio.client.modules.exploit.IllegalDisconnectModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

public final class DisconnectCommand extends Command {

   public DisconnectCommand() {
      super("disconnect");
      String[] aliases = {"dc"};
      this.setAliases(aliases);
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      LiteralArgumentBuilder illegal = Command.literal("illegal");
      com.mojang.brigadier.Command<Object> disconnectIllegally = context -> {
         if (!this.isWorldUnavailable()) {
            IllegalDisconnectModule.sendInvalidChatPacket();
         }
         return 1;
      };
      ArgumentBuilder illegalCommand = illegal.executes(disconnectIllegally);
      LiteralArgumentBuilder commandWithIllegal = (LiteralArgumentBuilder)builder.then(illegalCommand);
      com.mojang.brigadier.Command<Object> disconnect = context -> {
         if (!this.isWorldUnavailable()) {
            ClientPacketListener listener = ClientAccess.zioMinecraft.player.connection;
            Connection connection = listener.getConnection();
            Component reason = Component.nullToEmpty("Disconnected");
            connection.disconnect(reason);
         }
         return 1;
      };
      commandWithIllegal.executes(disconnect);
   }
}
