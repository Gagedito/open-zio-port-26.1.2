/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public final class KeyboardHandlerCharTypedEvent extends CancellableEvent {
   private final int codePoint;

   public String getText() {
      return Character.toString(this.codePoint);
   }

   public char getCharacter() {
      return this.getText().charAt(0);
   }

   public KeyboardHandlerCharTypedEvent(int codePoint) {
      this.codePoint = codePoint;
   }
}
