/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public abstract class LocalPlayerTickPhaseEvent extends CancellableEvent {
   public static final class LocalPlayerTickEvent extends LocalPlayerTickPhaseEvent {
   }

   public static final class BeforePassengerCheck extends LocalPlayerTickPhaseEvent {
   }
}
