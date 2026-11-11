/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.zio.client.ClientAccess;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;

public class PlayerNameArgumentType implements ClientAccess, ReifiedArgumentType {
   private static final List<String> EXAMPLES = List.of("cat", "fit", "asphyxia1337");

   @Override
   public Class<?> getReifiedType() {
      return String.class;
   }

   @Override
   public String parse(StringReader reader) throws CommandSyntaxException {
      return CommandStringReader.readString(reader);
   }

   @Override
   public Collection<String> getExamples() {
      return EXAMPLES;
   }

   @Override
   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      ClientPacketListener connection = ClientAccess.zioMinecraft.player.connection;
      Stream<String> names = connection.getOnlinePlayers().stream().map(playerInfo -> {
         GameProfile profile = playerInfo.getProfile();
         return profile.name();
      });
      return SharedSuggestionProvider.suggest(names, builder);
   }

}
