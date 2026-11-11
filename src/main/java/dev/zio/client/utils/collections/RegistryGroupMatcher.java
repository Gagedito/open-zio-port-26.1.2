/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.collections;

import dev.zio.client.utils.inventory.ItemRegistryGroupMatcher;
import dev.zio.client.utils.world.BlockRegistryGroupMatcher;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public interface RegistryGroupMatcher {
   List MATCHERS = createDefaultMatchers();
   EmptyRegistryGroupMatcher EMPTY = new EmptyRegistryGroupMatcher();

   Identifier getRegistryId();

   Collection getGroupNames();

   Collection getGroupEntries(String groupName);

   static RegistryGroupMatcher forRegistry(Registry registry) {
      Iterator matchers = MATCHERS.iterator();

      while (true) {
         if (!matchers.hasNext()) {
            return EMPTY;
         }
         RegistryGroupMatcher matcher = (RegistryGroupMatcher)matchers.next();
         ResourceKey registryKey = registry.key();
         Identifier registryId = registryKey.identifier();
         if (registryId.equals(matcher.getRegistryId())) {
            return matcher;
         }
      }
   }

   private static List createDefaultMatchers() {
      BlockRegistryGroupMatcher blocks = new BlockRegistryGroupMatcher();
      ItemRegistryGroupMatcher items = new ItemRegistryGroupMatcher();
      return List.of(blocks, items);
   }
}
