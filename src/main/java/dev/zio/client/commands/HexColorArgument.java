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
import dev.zio.client.render.ColorUtils;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.awt.Color;

public class HexColorArgument implements ArgumentType {
   private static final LiteralMessage INVALID_COLOR_MESSAGE;

   static {
      INVALID_COLOR_MESSAGE = new LiteralMessage("Invalid color provided.");
   }

   @Override
   public Color parse(StringReader reader) throws CommandSyntaxException {
      try {
         if (reader.canRead() && reader.peek() == '#') reader.skip();
         String text = reader.readString();
         return text.isEmpty() ? Color.WHITE : ColorUtils.parseHexColor(text);
      } catch (IllegalArgumentException failure) {
         throw new SimpleCommandExceptionType(INVALID_COLOR_MESSAGE).createWithContext(reader);
      }
   }
}
