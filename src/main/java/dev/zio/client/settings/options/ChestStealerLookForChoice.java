/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.settings.NamedEntry;

public enum ChestStealerLookForChoice implements NamedEntry {
   NAME("Name"),
   ITEM("Item");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ChestStealerLookForChoice(String displayName) {
      this.displayName = displayName;
   }
}
