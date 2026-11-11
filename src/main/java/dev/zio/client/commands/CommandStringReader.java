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

public final class CommandStringReader {

   private CommandStringReader() {
   }

   public static String readString(StringReader reader) throws CommandSyntaxException {

      if (!reader.canRead()) {
         return "";
      }

      char firstCharacter = reader.peek();
      if (StringReader.isQuotedStringStart(firstCharacter)) {
         reader.skip();
         return reader.readStringUntil(firstCharacter);
      }

      int startCursor = reader.getCursor();

      while (reader.canRead() && !isStringTerminator(reader.peek())) {
         reader.read();
      }

      String sourceSnapshot = reader.getString();
      int endCursor = reader.getCursor();
      return sourceSnapshot.substring(startCursor, endCursor);

   }

   private static boolean isStringTerminator(char character) {
      return character == ' ' || character == '\t';
   }

}
