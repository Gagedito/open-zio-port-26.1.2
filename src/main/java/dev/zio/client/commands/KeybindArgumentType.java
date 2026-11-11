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
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.modules.Keybind;

import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.zio.mixin.InputConstantsTypeAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class KeybindArgumentType implements ArgumentType {

   private static final DynamicCommandExceptionType UNKNOWN_KEY = new DynamicCommandExceptionType(
      argument -> {
         String argumentText = String.valueOf(argument);
         String errorMessage = "Key not found: " + argumentText;
         return Component.literal(errorMessage);
      }
   );
   @Override
   public Keybind parse(StringReader reader) throws CommandSyntaxException {
      String name = reader.readString().trim();
      try {
         return KeyUtils.parseKeybind(name);
      } catch (IllegalArgumentException failure) {
         throw UNKNOWN_KEY.create(name);
      }
   }

   @Override
   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining();
      boolean uppercaseSuggestions = false;
      for (int characterIndex = remaining.length(); characterIndex > 0; characterIndex--) {
         char character = remaining.charAt(characterIndex - 1);
         if (Character.isLetter(character)) {
            uppercaseSuggestions = Character.isUpperCase(character);
            break;
         }
      }

      InputConstantsTypeAccessor keyboardType = (InputConstantsTypeAccessor)(Object)Type.KEYSYM;
      Int2ObjectMap<Key> keyMap = keyboardType.getKeyMap();
      ObjectCollection<Key> keyValues = keyMap.values();
      Iterator<Key> keyboardSuggestions = keyValues.iterator();
      while (keyboardSuggestions.hasNext()) {
         Key key = keyboardSuggestions.next();
         String suggestionText = key.getName();
         String normalizedKeyName = suggestionText.replaceAll("key.keyboard.", "").replace(".", "_");
         String uppercaseKeyName = normalizedKeyName.toUpperCase(Locale.ROOT);
         String uppercaseQuery = remaining.toUpperCase(Locale.ROOT);
         if (SharedSuggestionProvider.matchesSubStr(uppercaseQuery, uppercaseKeyName)) {
            builder.suggest(uppercaseSuggestions ? uppercaseKeyName : uppercaseKeyName.toLowerCase(Locale.ROOT));
         }
      }

      Iterator<String> aliases = KeyUtils.getKeyAliases().keySet().iterator();
      while (aliases.hasNext()) {
         String alias = aliases.next();
         String uppercaseQuery = remaining.toUpperCase(Locale.ROOT);
         if (SharedSuggestionProvider.matchesSubStr(uppercaseQuery, alias)) {
            builder.suggest(uppercaseSuggestions ? alias : alias.toLowerCase(Locale.ROOT));
         }
      }

      int mouseIndex = 0;
      while (mouseIndex < (5)) {
         String mouseName = "MOUSE" + String.valueOf(mouseIndex);
         if (SharedSuggestionProvider.matchesSubStr(remaining, mouseName)) {
            builder.suggest(uppercaseSuggestions ? mouseName : mouseName.toLowerCase(Locale.ROOT));
         }
         mouseIndex++;
      }
      return builder.buildFuture();
   }

}
