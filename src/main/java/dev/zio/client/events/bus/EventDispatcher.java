/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events.bus;

import dev.zio.client.events.Cancellable;

public interface EventDispatcher {
   void subscribe(Object subscriber);

   void unsubscribeStatic(Class<?> listenerClass);

   void unsubscribe(Object subscriber);

   void subscribeListener(EventListener listener);

   Cancellable postCancellable(Cancellable event);

   void unsubscribeListener(EventListener listener);

   Object post(Object event);

   void subscribeStatic(Class<?> listenerClass);
}
