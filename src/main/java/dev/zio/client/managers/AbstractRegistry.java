/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.managers;

import dev.zio.client.utils.inventory.RegistryContainer;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

public abstract class AbstractRegistry<T> implements RegistryContainer {
   protected final Collection<T> entries;

   protected AbstractRegistry(Collection<T> entries) {
      this.entries = entries;
   }

   @Override
   public Collection getRegistry() {
      return this.entries;
   }

   public Optional findFirst(Predicate predicate) {
      for (T entry : this.entries) {
         if (predicate.test(entry)) {
            return Optional.of(entry);
         }
      }

      return Optional.empty();
   }

   public Object findFirstOrNull(Predicate predicate) {
      for (T entry : this.entries) {
         if (predicate.test(entry)) {
            return entry;
         }
      }

      return null;
   }
}
