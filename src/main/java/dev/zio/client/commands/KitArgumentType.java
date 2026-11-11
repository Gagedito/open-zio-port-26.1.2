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
import dev.zio.client.managers.KitRegistry;
import dev.zio.client.utils.inventory.Kit;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class KitArgumentType implements ArgumentType {
   private static final String unknownKitFormat = "Kit %s doesn't exist";

   @Override
   public Kit parse(StringReader reader) throws CommandSyntaxException {

      String requestedKitName = CommandStringReader.readString(reader);
      KitRegistry kitRegistry = ClientContext.kitRegistry;
      Iterator kits = ((List)kitRegistry.getRegistry()).iterator();

      while (kits.hasNext()) {
         Kit kit = (Kit)kits.next();
         String kitName = kit.getName();
         if (kitName.equalsIgnoreCase(requestedKitName)) {
            return kit;
         }
      }

      DynamicCommandExceptionType unknownKitException = new DynamicCommandExceptionType(argument -> {
         String format = unknownKitFormat;
         Object[] formatArguments = new Object[]{argument};
         String errorMessage = String.format(format, formatArguments);
         return Component.literal(errorMessage);
      });
      throw unknownKitException.create(requestedKitName);

   }

   @Override
   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      KitRegistry kitRegistry = ClientContext.kitRegistry;
      Stream<String> kitNames = ((List<Kit>)kitRegistry.getRegistry()).stream().map(Kit::getName);
      return SharedSuggestionProvider.suggest(kitNames, builder);
   }

}
