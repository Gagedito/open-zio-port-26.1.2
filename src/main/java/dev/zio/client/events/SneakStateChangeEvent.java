/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public final class SneakStateChangeEvent extends CancellableEvent {
   private final boolean sneaking;

   public boolean isSneaking() {
      return this.sneaking;
   }

   public SneakStateChangeEvent(boolean sneaking) {
      this.sneaking = sneaking;
   }
}
