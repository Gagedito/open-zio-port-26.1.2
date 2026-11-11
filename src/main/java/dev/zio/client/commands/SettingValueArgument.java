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
import dev.zio.client.ClientContext;
import dev.zio.client.utils.ExperimentalFeatures;
import dev.zio.client.managers.SoundManager;
import dev.zio.client.utils.collections.RegistryGroupMatcher;
import dev.zio.client.utils.collections.NamedIdentifier;
import dev.zio.client.modules.Module;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.IdentifierSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.SetSetting;
import dev.zio.client.settings.Setting;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;

public class SettingValueArgument implements ArgumentType, ClientAccess {
   private final String settingArgumentName;
   private final Module module;

   private static String getEnumSuggestionName(Enum value) {
      if (value instanceof NamedEntry namedValue) {
         String displayName = namedValue.getName();
         return displayName.toLowerCase();
      } else {
         String enumName = value.name();
         return enumName.toLowerCase();
      }
   }

   public SettingValueArgument(Module module, String settingArgumentName) {
      this.settingArgumentName = settingArgumentName;
      this.module = module;
   }

   public static SettingValueArgument value(Module module, String settingArgumentName) {
      return new SettingValueArgument(module, settingArgumentName);
   }

   @Override
   public String parse(StringReader reader) {
      String remaining = reader.getRemaining();
      int inputLength = reader.getTotalLength();
      reader.setCursor(inputLength);
      return remaining;
   }

   @Override
   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      Setting<?> setting;
      try {
         Module capturedModule = this.module;
         String capturedSettingArgumentName = this.settingArgumentName;
         setting = ModuleSettingArgumentType.getSetting(context, capturedModule, capturedSettingArgumentName);
      } catch (Exception optionFailure) {
         return Suggestions.empty();
      }
      if (setting instanceof BooleanSetting booleanSetting) {
         BooleanSettingArgument booleanArgument = BooleanSettingArgument.bool(booleanSetting);
         return booleanArgument.listSuggestions(context, builder);
      } else if (setting instanceof EnumSetting<?> enumSetting) {
         Enum defaultEnumValue = enumSetting.getDefaultValue();
         Class enumClass = defaultEnumValue.getDeclaringClass();
         Enum[] enumConstants = (Enum[])enumClass.getEnumConstants();
         Stream<Enum> enumValues = Arrays.stream(enumConstants);
         Predicate<Enum> enumFilter = enumCandidate -> !ExperimentalFeatures.isEnumConstantHidden(enumCandidate);
         Stream<Enum> visibleEnumValues = enumValues.filter(enumFilter);
         Function<Enum, String> enumNames = SettingValueArgument::getEnumSuggestionName;
         Stream<String> suggestedEnumNames = visibleEnumValues.map(enumNames);
         return SharedSuggestionProvider.suggest(suggestedEnumNames, builder);
      } else if (setting instanceof NumberSetting<?> numberSetting) {
         Number currentNumber = numberSetting.getValue();
         String remainingInput = builder.getRemaining();
         if (remainingInput.isBlank()) {
            Object defaultNumber = numberSetting.getDefaultValue();
            String defaultSuggestion = String.valueOf(defaultNumber);
            SuggestionsBuilder suggestedDefaultBuilder = builder.suggest(defaultSuggestion);
            return suggestedDefaultBuilder.buildFuture();
         } else if (currentNumber instanceof Float) {
            float floatMinimum = (Float)(numberSetting.getMinimumValue());
            Float boxedFloatMaximum = (Float)(numberSetting.getMaximumValue());
            FloatArgumentType numberArgument = FloatArgumentType.floatArg(floatMinimum, boxedFloatMaximum);
            return numberArgument.listSuggestions(context, builder);
         } else if (currentNumber instanceof Double) {
            double doubleMinimum = (Double)(numberSetting.getMinimumValue());
            Double boxedDoubleMaximum = (Double)(numberSetting.getMaximumValue());
            DoubleArgumentType numberArgument = DoubleArgumentType.doubleArg(doubleMinimum, boxedDoubleMaximum);
            return numberArgument.listSuggestions(context, builder);
         } else {
            int integerMinimum = (Integer)(numberSetting.getMinimumValue());
            Integer boxedIntegerMaximum = (Integer)(numberSetting.getMaximumValue());
            IntegerArgumentType numberArgument = IntegerArgumentType.integer(integerMinimum, boxedIntegerMaximum);
            return numberArgument.listSuggestions(context, builder);
         }
      } else if (setting instanceof SetSetting<?> setSetting) {
         Collection suggestedValues = setSetting.getFormattedAvailableEntries();
         if (setting instanceof RegistrySetSetting<?> registrySetting) {
            Registry registry = registrySetting.getRegistry();
            RegistryGroupMatcher registrySuggestions = RegistryGroupMatcher.forRegistry(registry);
            if (registrySuggestions != RegistryGroupMatcher.EMPTY) {
               Collection groupNames = registrySuggestions.getGroupNames();
               suggestedValues.addAll(groupNames);
            }
         }
         return SharedSuggestionProvider.suggest(suggestedValues, builder);
      } else if (setting instanceof IdentifierSetting identifierSetting) {
         SoundManager capturedIdentifierRegistry = ClientContext.soundManager;
         Collection soundIds = capturedIdentifierRegistry.getSoundIds();
         Stream<NamedIdentifier> identifiers = soundIds.stream();
         Function<NamedIdentifier, String> identifierName = identifier -> {
            String identifierId = identifier.getCategory();
            return String.valueOf(identifierId) + ":" + String.valueOf(identifier.getName());
         };
         Stream<String> suggestedIdentifiers = identifiers.map(identifierName);
         return SharedSuggestionProvider.suggest(suggestedIdentifiers, builder);
      } else {
         return ArgumentType.super.listSuggestions(context, builder);
      }
   }

}
