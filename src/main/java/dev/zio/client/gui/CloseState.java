/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui;

public interface CloseState {
   CloseState ALWAYS_OPEN = () -> false;

   boolean isClosed();

   default boolean isOpen() {
      return !this.isClosed();
   }
}
