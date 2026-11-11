/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public final class MinecraftRunTickEvent extends CancellableEvent {
   private final boolean hasPendingTicks;

   public boolean hasPendingTicks() {
      return this.hasPendingTicks;
   }

   public MinecraftRunTickEvent(boolean hasPendingTicks) {
      this.hasPendingTicks = hasPendingTicks;
   }
}
