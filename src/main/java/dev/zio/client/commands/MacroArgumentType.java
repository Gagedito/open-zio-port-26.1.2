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
import dev.zio.client.NamedFeature;
import dev.zio.client.managers.MacroRegistry;
import dev.zio.client.modules.misc.Macro;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class MacroArgumentType implements ArgumentType {
   private static final String unknownMacroFormat = "Macro %s doesn't exists";

   @Override
   public Macro parse(StringReader reader) throws CommandSyntaxException {

      String requestedName = reader.readString();
      MacroRegistry macroRegistry = ClientContext.macroRegistry;
      Iterator macros = ((List)macroRegistry.getRegistry()).iterator();

      while (macros.hasNext()) {
         Macro macro = (Macro)macros.next();
         String macroName = macro.getName();
         if (macroName.equalsIgnoreCase(requestedName)) {
            return macro;
         }
      }

      DynamicCommandExceptionType unknownMacroException = new DynamicCommandExceptionType(invalidName -> {
         String capturedErrorFormat = unknownMacroFormat;
         Object[] formatArguments = new Object[]{invalidName};
         String errorMessage = String.format(capturedErrorFormat, formatArguments);
         return Component.literal(errorMessage);
      });
      throw unknownMacroException.create(requestedName);

   }

   @Override
   public CompletableFuture listSuggestions(CommandContext unusedContext, SuggestionsBuilder suggestions) {
      MacroRegistry macroRegistry = ClientContext.macroRegistry;
      Stream names = ((List)macroRegistry.getRegistry()).stream().map((Function<Macro, String>)NamedFeature::getName);
      return SharedSuggestionProvider.suggest(names, suggestions);
   }

}
