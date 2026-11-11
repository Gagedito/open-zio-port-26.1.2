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
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class ModuleArgumentType implements ArgumentType {
   private static final DynamicCommandExceptionType UNKNOWN_MODULE;

   static {
      UNKNOWN_MODULE = new DynamicCommandExceptionType(argument ->
         Component.nullToEmpty("Module not found " + String.valueOf(argument)));
   }

   @Override
   public Module parse(StringReader reader) throws CommandSyntaxException {

      String moduleName = reader.readString();
      ModuleRegistry registry = ClientContext.moduleRegistry;
      Predicate<Module> moduleMatches = module -> {

         for (String alias : module.getAliases()) {
            String requestedName = moduleName;
            String capturedAlias = alias;
            if (capturedAlias.equalsIgnoreCase(requestedName)) {
               return true;
            }
         }

         return false;
      };
      Optional matchingModule = registry.findFirst(moduleMatches);
      if (matchingModule.isEmpty()) {
         String unmatchedName = moduleName;
         DynamicCommandExceptionType exceptionType = UNKNOWN_MODULE;
         throw exceptionType.create(unmatchedName);
      } else {
         return (Module)matchingModule.get();
      }

   }

   @Override
   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      ModuleRegistry modules = ClientContext.moduleRegistry;
      Stream<String> moduleNames = ((List<Module>)modules.getRegistry()).stream().map(NamedFeature::getName);
      return SharedSuggestionProvider.suggest(moduleNames, builder);
   }

}
