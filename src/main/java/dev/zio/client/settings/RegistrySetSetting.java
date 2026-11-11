/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public final class RegistrySetSetting<E> extends SetSetting<E> {
   private final Registry<E> registry;

   @SafeVarargs
   public RegistrySetSetting(String name, Registry<E> entryRegistry, E... defaultEntries) {
      super(name, defaultEntries);
      this.registry = entryRegistry;
   }

   @SafeVarargs
   public RegistrySetSetting(String name, Registry<E> entryRegistry, Predicate<? super Set<E>> visibility, E... defaultEntries) {
      super(name, visibility, defaultEntries);
      this.registry = entryRegistry;
   }

   @Override
   public E parseEntry(String text) {
      Registry<E> lookupRegistry = this.registry;
      Identifier identifier = Identifier.parse(text);
      Optional<Reference<E>> holder = lookupRegistry.get(identifier);
      return holder.map(Reference::value).orElse(null);
   }

   @Override
   public Collection<E> getAvailableEntries() {
      return this.registry.stream().toList();
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json.isJsonArray()) {
         this.getValue().clear();
         JsonArray array = json.getAsJsonArray();
         for (JsonElement element : array) {
            Registry<E> lookupRegistry = this.registry;
            String identifierText = element.getAsString();
            Identifier identifier = Identifier.parse(identifierText);
            E entry = lookupRegistry.getValue(identifier);
            // MC 26.1.2: si el filtro requiere data components sin bind (se reintenta
            // al entrar al primer mundo), la registry queda vacia y getValue resuelve
            // al default ("air"). Descartar el fallback evita destruir los valores
            // guardados en ese estado temporal; CustomItemRegistries completea el
            // contenido y Configuration reload los aplica post-bind.
            if (!identifier.equals(lookupRegistry.getKey(entry))) {
               continue;
            }

            this.getValue().add(entry);
         }
      }
   }

   public void modifyEntries(Consumer<Set<E>> editor) {
      editor.accept(this.getValue());
      this.runChangeCallback();
   }

   @Override
   public String formatEntry(E entry) {
      Registry<E> entryRegistry = this.registry;
      Identifier identifier = entryRegistry.getKey(entry);
      if (identifier == null) {
         return null;
      }

      return identifier.toShortLanguageKey();
   }

   public Registry<E> getRegistry() {
      return this.registry;
   }
}
