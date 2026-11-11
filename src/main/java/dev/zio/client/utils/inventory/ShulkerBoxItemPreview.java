/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public record ShulkerBoxItemPreview(
   ItemStack containerStack,
   ItemStack previewStack
) {
   public static ShulkerBoxItemPreview create(ItemStack stack) {
      Item containerItem = stack.getItem();

      if (!ItemUtils.isShulkerBox(containerItem)) {
         return null;
      }

      DataComponentType<ItemContainerContents> containerType = DataComponents.CONTAINER;
      ItemContainerContents contents = ((DataComponentHolder)stack).get(containerType);
      if (contents == null) {
         return null;
      }

      Map<Item, Integer> occupiedSlotCounts = new HashMap<>();
      Iterator<ItemStack> stacksIterator = contents.allItemsCopyStream().toList().iterator();

      while (stacksIterator.hasNext()) {
         ItemStack containedStack = stacksIterator.next();
         if (!containedStack.isEmpty()) {
            Item containedItem = containedStack.getItem();
            byte zeroCount = 0;
            Integer previousSlotCount = occupiedSlotCounts.getOrDefault(containedItem, Integer.valueOf(zeroCount));
            int nextSlotCount = 1 + previousSlotCount;
            occupiedSlotCounts.put(containedItem, nextSlotCount);
         }
      }

      if (occupiedSlotCounts.isEmpty()) {
         return null;
      }

      Entry<Item, Integer> selectedEntry = occupiedSlotCounts.entrySet().stream().max(Entry.comparingByValue()).orElse(null);
      if (selectedEntry == null) {
         return null;
      }

      return new ShulkerBoxItemPreview(stack, selectedEntry.getKey().getDefaultInstance());
   }
}
