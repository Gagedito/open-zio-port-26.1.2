/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public final class MinecraftTickEvent extends CancellableEvent {
   public static final MinecraftTickEvent INSTANCE = new MinecraftTickEvent();

   private MinecraftTickEvent() {
   }
}
