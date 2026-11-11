/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.bootstrap;

import dev.zio.client.ZioClient;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * MC 26.1.2: los data components de los elementos de registro ya no viven en un
 * campo del Item (1.21.11) sino en el Holder del registro, y vanilla los bindea
 * al cargar los registros de juego (RegistryDataCollector#updateComponents),
 * es decir, al conectarse a un mundo.
 * <p>
 * Los filtros de CustomItemRegistries consultan Item.components() durante
 * initializeClient para llenar las registries custom antes de cargar la
 * configuracion, como el campo estatico de 1.21.11 lo permitia. Aqui se replica
 * la porcion del bootstrap: si los componentes aun no estan disponibles (los
 * inicializadores de Item consultan tags de datapack que solo existen post-join,
 * p. ej. minecraft:is_fire), la operacion se salta con WARN y
 * CustomItemRegistries reintenta cuando vanilla complete el bind.
 */
public final class ItemComponentsBootstrapper {
   private ItemComponentsBootstrapper() {
   }

   public static void bindItemComponents() {
      try {
         RegistryAccess.Frozen access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
         HolderLookup.Provider provider = access;
         for (DataComponentInitializers.PendingComponents<?> pending : BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider)) {
            if (!BuiltInRegistries.ITEM.key().equals(pending.key())) {
               continue;
            }

            pending.apply();
            return;
         }
      } catch (Throwable unboundComponents) {
         // Estado esperado pre-join: tags de datapack ausentes (HolderGetter$Provider
         // getOrThrow) o componentes sin bind. CustomItemRegistries.retryPendingInstall
         // completa la instalacion cuando vanilla pase por updateComponents en el join.
         ZioClient.LOGGER.info(
            "Built-in item components are not bound during client initialization (expected, bound at world join); deferring custom registries.",
            unboundComponents
         );
      }
   }
}
