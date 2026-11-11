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

public class ContiguousCharacterRange implements CharacterRange {
   private final int firstCodePoint;
   private final int lastCodePoint;
   public ContiguousCharacterRange(int firstCodePoint, int lastCodePoint) {
      this.firstCodePoint = firstCodePoint;
      this.lastCodePoint = lastCodePoint;
   }

   @Override
   public void forEachCharacter(CharacterConsumer consumer) {
      for (int codePoint = this.firstCodePoint;
         codePoint <= this.lastCodePoint;
         codePoint++
      ) {
         consumer.accept((char)codePoint);
      }
   }
}
