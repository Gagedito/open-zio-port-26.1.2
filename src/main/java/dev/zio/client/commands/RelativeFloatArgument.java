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

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;

public class RelativeFloatArgument implements ArgumentType {

   public static RelativeFloatArgument relativeFloatArg() {
      return new RelativeFloatArgument();
   }

   @Override
   public RelativeFloat parse(StringReader reader) throws CommandSyntaxException {
      boolean relative = reader.canRead() && reader.peek() == '~';
      if (relative) reader.skip();
      float value = relative && (!reader.canRead() || reader.peek() == ' ') ? 0.0F : reader.readFloat();
      return new RelativeFloat(value, relative);
   }

   public record RelativeFloat(
      float value,
      boolean relative
   ) {

   }
}
