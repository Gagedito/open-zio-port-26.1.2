/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.managers;

import dev.zio.client.managers.AbstractRegistry;
import dev.zio.client.settings.NamedEntry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.function.Predicate;

public class Registry<T extends NamedEntry> extends AbstractRegistry<T> {

   public Registry() {
      super(new ObjectArrayList<>());
   }

   @Override
   public boolean register(Object entry) {
      return this.registerEntry((NamedEntry)entry);
   }

   @Override
   public boolean unregister(Object entry) {
      return this.unregisterEntry((NamedEntry)entry);
   }

   @SuppressWarnings("unchecked")
   public boolean registerEntry(NamedEntry entry) {
      if (entry == null) {
         return false;
      }

      for (T registeredEntry : this.entries) {
         if (registeredEntry != null && registeredEntry.getName() != null && registeredEntry.getName().equals(entry.getName())) {
            return false;
         }
      }

      this.entries.add((T)entry);
      return true;
   }

   public NamedEntry getByClass(Class entryClass) {
      return (NamedEntry)this.findFirstOrNull((Predicate<NamedEntry>)candidateEntry -> candidateEntry.getClass().equals(entryClass));
   }

   public boolean unregisterEntry(NamedEntry entry) {
      if (entry == null) {
         return false;
      }

      this.entries.remove(entry);
      return true;
   }
}
