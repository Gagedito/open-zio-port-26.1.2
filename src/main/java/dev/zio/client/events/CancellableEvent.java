/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public class CancellableEvent {
   private boolean cancelled;

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void resetCancellation() {
      this.cancelled = false;
   }

   public void cancel() {
      this.cancelled = true;
   }
}
