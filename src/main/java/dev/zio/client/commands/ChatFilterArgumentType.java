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
import dev.zio.client.ClientContext;
import dev.zio.client.managers.ChatFilterRegistry;
import dev.zio.client.utils.chat.ChatFilterEntry;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class ChatFilterArgumentType implements ArgumentType<ChatFilterEntry> {
   private static final String UNKNOWN_FILTER_PREFIX = "Filter not found ";

   @Override
   public ChatFilterEntry parse(StringReader reader) throws CommandSyntaxException {

      String filterId = CommandStringReader.readString(reader);
      ChatFilterRegistry filterRegistry = ClientContext.chatFilterRegistry;
      Optional<ChatFilterEntry> matchingFilter = filterRegistry.findById(filterId);
      if (matchingFilter.isEmpty()) {
         DynamicCommandExceptionType unknownFilter = new DynamicCommandExceptionType(
            argument -> {
               String argumentText = String.valueOf(argument);
               String errorMessage = UNKNOWN_FILTER_PREFIX.concat(argumentText);
               return Component.nullToEmpty(errorMessage);
            }
         );
         throw unknownFilter.create(filterId);
      }
      return matchingFilter.get();

   }

   @Override
   public CompletableFuture<Suggestions> listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      ChatFilterRegistry filterRegistry = ClientContext.chatFilterRegistry;
      Stream<String> filterIds = filterRegistry.streamIds();
      return SharedSuggestionProvider.suggest(filterIds, builder);
   }

}
