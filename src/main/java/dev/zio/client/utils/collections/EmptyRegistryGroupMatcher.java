/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.collections;

import java.util.Collection;
import java.util.Collections;
import net.minecraft.resources.Identifier;

public class EmptyRegistryGroupMatcher implements RegistryGroupMatcher {
   @Override
   public Identifier getRegistryId() {
      return null;
   }

   @Override
   public Collection getGroupNames() {
      return Collections.emptyList();
   }

   @Override
   public Collection getGroupEntries(String groupName) {
      return Collections.emptyList();
   }
}
