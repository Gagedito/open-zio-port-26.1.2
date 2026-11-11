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
import dev.zio.client.modules.Module;
import dev.zio.client.settings.JsonConfigurable;
import dev.zio.client.settings.Setting;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class ModuleSettingArgumentType implements ArgumentType, ClientAccess {
   private final Module module;
   private static final String UNKNOWN_SETTING_PREFIX = "Setting not found ";

   public ModuleSettingArgumentType(Module module) {
      this.module = module;
   }

   @Override
   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.readString();
   }

   public static Setting<?> getSetting(CommandContext context, Module module, String argumentName) throws CommandSyntaxException {
      String requestedName = (String)context.getArgument(argumentName, String.class);
      Stream<Setting<?>> settings = module.getSettings().stream().map(setting -> (Setting<?>)setting);
      Stream<Setting<?>> matchingSettings = settings.filter(setting -> {
         String configName = setting.getConfigName();
         return configName.equalsIgnoreCase(requestedName);
      });
      Optional<Setting<?>> matchedSetting = matchingSettings.findFirst();
      if (matchedSetting.isEmpty()) {
         DynamicCommandExceptionType unknownSettingError = new DynamicCommandExceptionType(
            argument -> {
               String argumentText = String.valueOf(argument);
               String message = UNKNOWN_SETTING_PREFIX.concat(argumentText);
               return Component.literal(message);
            }
         );
         throw unknownSettingError.create(requestedName);
      }
      return matchedSetting.get();
   }

   @Override
   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      Module module = this.module;
      Stream<Setting<?>> settings = module.getSettings().stream().map(setting -> (Setting<?>)setting);
      Stream<String> settingNames = settings.filter(setting -> {
         return !setting.isGroupOnly();
      }).map(JsonConfigurable::getConfigName);
      return SharedSuggestionProvider.suggest(settingNames, builder);
   }

}
