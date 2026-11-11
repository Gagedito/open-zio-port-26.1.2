/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.gui.panels.Panel;

import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemListComponent extends AbstractItemListComponent<Item> {

   public ItemListComponent(Panel panel, Predicate<Item> filter) {
      super(panel, filter, BuiltInRegistries.ITEM.size());
   }

   @Override
   public void update(double mouseX, double mouseY) {
      this.entries.clear();
      BuiltInRegistries.ITEM
         .stream()
         .filter(this.filter)
         .forEach(this.entries::add);
   }

   @Override
   public String getEntryLabel(Item item) {
      return item.getName(new ItemStack(item)).getString();
   }

   @Override
   public ItemStack getEntryIcon(Item item) {
      return item.getDefaultInstance();
   }
}
