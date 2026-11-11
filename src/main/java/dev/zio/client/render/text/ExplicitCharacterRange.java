/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render.text;

import dev.zio.client.utils.inventory.CharacterConsumer;

public class ExplicitCharacterRange implements CharacterRange {
   private final char[] characters;
   public ExplicitCharacterRange(char... characters) {
      this.characters = characters;
   }

   @Override
   public void forEachCharacter(CharacterConsumer consumer) {
      for (char character : this.characters) {
         consumer.accept(character);
      }
   }
}
