/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import com.mojang.serialization.Lifecycle;
import dev.zio.client.ZioClient;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class CustomItemRegistries {

   private static final List<RegistryInstaller> registryInstallers = new ArrayList<>();
   private static boolean pendingInstall;
   public static final DefaultedRegistry foodItems = createFilteredItemRegistry(
      "food", (Predicate<Item>)ItemUtils::isFood
   );
   public static final DefaultedRegistry smithingTemplates = createFilteredItemRegistry(
      "templates", (Predicate<Item>)ItemUtils::isSmithingTemplate
   );
   public static final DefaultedRegistry trimMaterials = createFilteredItemRegistry(
      "trim_materials", (Predicate<Item>)ItemUtils::providesTrimMaterial
   );

   private CustomItemRegistries() {
   }

   public static void initialize() {
      attemptInstall();
   }

   /**
    * MC 26.1.2: los filtros que consultan Item.components() solo son
    * evaluables cuando vanilla bindea los componentes (primer join al mundo,
    * RegistryDataCollector#updateComponents). La instalacion que falla queda
    * pendiente y se reintenta desde ahi.
    */
   public static void retryPendingInstall() {
      if (!pendingInstall || registryInstallers.isEmpty()) {
         return;
      }

      attemptInstall();
   }

   public static boolean hasPendingInstall() {
      return pendingInstall;
   }

   private static void attemptInstall() {
      boolean failed = false;
      for (RegistryInstaller installer : registryInstallers) {
         if (!installer.tryInstall()) {
            failed = true;
         }
      }

      if (!failed) {
         pendingInstall = false;
      }
   }

   static void markPendingInstall() {
      pendingInstall = true;
   }

   private static DefaultedRegistry createDeferredRegistry(
      String path, String defaultEntry, Consumer installer
   ) {
      Identifier registryId = Identifier.fromNamespaceAndPath("zio", path);
      ResourceKey registryKey = ResourceKey.createRegistryKey(registryId);
      DefaultedMappedRegistry registry = new DefaultedMappedRegistry(defaultEntry, registryKey, Lifecycle.stable(), false);
      registryInstallers.add(new RegistryInstaller(registry, installer));
      return registry;
   }

   private static DefaultedRegistry createFilteredItemRegistry(String path, Predicate itemFilter) {
      return createDeferredRegistry(
         path,
         "air",
         (Consumer<WritableRegistry>)targetRegistry -> BuiltInRegistries.ITEM.listElements()
            .filter((Predicate<Reference>)candidateHolder -> itemFilter.test((Item)candidateHolder.value()))
            .forEach((Consumer<Reference>)matchingHolder -> {
               ResourceKey itemKey = matchingHolder.key();
               Item item = (Item)matchingHolder.value();
               // Retry del install tras el bind de componentes: evita re-registrar
               // lo que ya quedo en una pasada previa (parcial) del install.
               if (targetRegistry.getResourceKey(item).isPresent()) {
                  return;
               }

               targetRegistry.register(itemKey, item, RegistrationInfo.BUILT_IN);
            })
      );
   }

   record RegistryInstaller(
      WritableRegistry registry,
      Consumer installer
   ) {

      public Consumer getInstaller() {
         return this.installer;
      }

      public boolean tryInstall() {
         try {
            this.installer.accept(this.registry);
            return true;
         } catch (Throwable unboundFilter) {
            CustomItemRegistries.markPendingInstall();
            ZioClient.LOGGER.debug(
               "Custom registry '{}' deferred until components are bound ({}).",
               this.registry.key(), unboundFilter.toString()
            );
            return false;
         }
      }

      public void installContents() {
         this.installer.accept(this.registry);
      }

      public WritableRegistry getRegistry() {
         return this.registry;
      }

   }
}
