/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.utils.inventory.InventorySortStrategy;
import dev.zio.client.settings.NamedEntry;

public enum InventorySorterModeChoice implements NamedEntry {
   TYPE("Type", ComparatorInventorySortStrategy.BY_TYPE),
   NAME("Name", ComparatorInventorySortStrategy.BY_NAME),
   KIT("Kit", KitInventorySortStrategy.INSTANCE);

   private final String displayName;
   private final InventorySortStrategy strategy;

   @Override
   public String getName() {
      return this.displayName;
   }

   InventorySorterModeChoice(String displayName, InventorySortStrategy strategy) {
      this.displayName = displayName;
      this.strategy = strategy;
   }

   public InventorySortStrategy getStrategy() {
      return this.strategy;
   }
}
