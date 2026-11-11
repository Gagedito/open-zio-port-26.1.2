/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public interface Cancellable {
   boolean isCancelled();

   default void cancel() {
      this.setCancelled(true);
   }

   void setCancelled(boolean cancelled);
}
