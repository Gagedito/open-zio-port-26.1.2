/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public final class InputPressEvent extends CancellableEvent {
   private final int code;
   private final boolean mouse;

   public boolean isMouse() {
      return this.mouse;
   }

   public int getCode() {
      return this.code;
   }

   public InputPressEvent(int code, boolean mouse) {
      this.code = code;
      this.mouse = mouse;
   }
}
