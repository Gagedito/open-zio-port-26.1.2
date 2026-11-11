/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events.bus;

public interface EventListener {
   boolean isStatic();

   Class<?> getEventType();

   int getPriority();

   void call(Object event);
}
