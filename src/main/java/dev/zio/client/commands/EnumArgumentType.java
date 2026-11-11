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
import dev.zio.client.settings.NamedEntry;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class EnumArgumentType implements ArgumentType {
   private final Class enumClass;
   private final String argumentLabel;

   public EnumArgumentType(Class enumClass) {
      this(enumClass, "Enum");
   }

   public EnumArgumentType(Class enumClass, String argumentLabel) {
      this.enumClass = enumClass;
      this.argumentLabel = argumentLabel;
   }

   private String getEnumName(Enum value) {
      if (value instanceof NamedEntry namedEntry) {
         return namedEntry.getName();
      }

      return value.name().toLowerCase(Locale.ROOT);
   }

   @Override
   public Enum parse(StringReader reader) throws CommandSyntaxException {

      String requestedName = reader.readString();
      Class enumType = this.enumClass;

      for (Enum enumConstant : (Enum[])enumType.getEnumConstants()) {
         String candidateName = this.getEnumName(enumConstant);
         if (candidateName.equalsIgnoreCase(requestedName)) {
            return enumConstant;
         }
      }

      DynamicCommandExceptionType unknownValueException = new DynamicCommandExceptionType(invalidValue -> {

         Object[] errorArguments = new Object[2];
         errorArguments[0] = this.argumentLabel;
         errorArguments[1] = invalidValue;
         String errorMessage = String.format(("%s %s doesn't exist"), (errorArguments));
         return Component.literal(errorMessage);
      });
      throw unknownValueException.create(requestedName);

   }

   @Override
   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      Class enumType = this.enumClass;
      Enum[] enumConstants = (Enum[])enumType.getEnumConstants();
      Stream<String> enumNames = Arrays.stream(enumConstants).map(this::getEnumName);
      return SharedSuggestionProvider.suggest(enumNames, builder);
   }
}
