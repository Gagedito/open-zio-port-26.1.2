/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils;

public interface Toggleable {
   void enable();

   boolean isToggled();

   void disable();

   default void toggle() {
      if (this.isToggled()) {
         this.disable();
      } else {
         this.enable();
      }
   }

   default void setToggled(boolean toggled) {
      if (this.isToggled() != toggled) {
         this.toggle();
      }
   }
}
