/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;

public class FancyStringArgument implements ArgumentType<String> {
   private static final List<String> EXAMPLES = List.of("words_with_underscores_any_language", "слова_на_любом_языке");

   public static FancyStringArgument fancyString() {
      return new FancyStringArgument();
   }

   public String description() {
      return "fancyString()";
   }

   @Override
   public String parse(StringReader reader) throws CommandSyntaxException {
      return CommandStringReader.readString(reader);
   }

   @Override
   public Collection<String> getExamples() {
      return EXAMPLES;
   }
}
