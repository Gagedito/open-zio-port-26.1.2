/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events.bus;

import dev.zio.client.events.EventHandler;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

public final class MethodEventListener implements EventListener {
   private final Class<?> eventType;
   private final boolean staticListener;
   private final int priority;
   private final Consumer<Object> consumer;

   @SuppressWarnings("unchecked")
   public MethodEventListener(Class<?> declaringClass, Object subscriber, Method handlerMethod) {
      this.eventType = handlerMethod.getParameterTypes()[0];
      this.staticListener = Modifier.isStatic(handlerMethod.getModifiers());
      this.priority = handlerMethod.getAnnotation(EventHandler.class).priority();
      try {
         MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
         MethodType handlerType = MethodType.methodType(void.class, this.eventType);
         MethodHandle implementation = this.staticListener
            ? lookup.findStatic(declaringClass, handlerMethod.getName(), handlerType)
            : lookup.findVirtual(declaringClass, handlerMethod.getName(), handlerType);
         MethodType factoryType = this.staticListener
            ? MethodType.methodType(Consumer.class)
            : MethodType.methodType(Consumer.class, declaringClass);
         MethodHandle factory = LambdaMetafactory.metafactory(
            lookup, "accept", factoryType,
            MethodType.methodType(void.class, Object.class), implementation, handlerType
         ).getTarget();
         this.consumer = this.staticListener
            ? (Consumer<Object>)factory.invoke()
            : (Consumer<Object>)factory.invoke(subscriber);
      } catch (Throwable failure) {
         throw new IllegalStateException("Cannot register event handler " + handlerMethod, failure);
      }
   }

   @Override
   public Class<?> getEventType() {
      return this.eventType;
   }

   @Override
   public boolean isStatic() {
      return this.staticListener;
   }

   @Override
   public int getPriority() {
      return this.priority;
   }

   @Override
   public void call(Object event) {
      this.consumer.accept(event);
   }
}
