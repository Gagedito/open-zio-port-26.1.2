/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.ClientContext;
import dev.zio.client.managers.KitRegistry;
import dev.zio.client.utils.inventory.InventorySortStrategy;
import dev.zio.client.utils.inventory.Kit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.IntStream;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class KitInventorySortStrategy implements InventorySortStrategy {
   public static final KitInventorySortStrategy INSTANCE;

   private KitInventorySortStrategy() {
   }

   static {
      INSTANCE = new KitInventorySortStrategy();
   }

   @Override
   public Integer[] getSourceSlots(InventorySorterScopeChoice scope) {
      KitRegistry kitManager = ClientContext.kitRegistry;
      Kit kit = KitRegistry.getCurrentKit(kitManager);
      int startInclusive = scope.getStartInclusive();
      int endExclusive = scope.getEndExclusive();
      List<Integer> inventorySlots = IntStream.range(startInclusive, endExclusive).boxed().toList();
      ArrayList<ItemStack> stacks = new ArrayList<>(
         inventorySlots.stream().map(InventorySortStrategy::getInventoryStack).toList()
      );
      ArrayList<Integer> sourceSlots = new ArrayList<>(inventorySlots);
      boolean[] claimedSlots = new boolean[stacks.size()];

      ArrayList kitEntries = new ArrayList(
         kit.getItemsBySlot().entrySet()
      );
      kitEntries.sort(Entry.comparingByKey());
      Iterator entries = kitEntries.iterator();

      kitTraversal:
      while (entries.hasNext()) {
         Entry entry = (Entry)entries.next();
         Integer slotValue = (Integer)entry.getKey();
         int kitSlot = slotValue;
         if (kitSlot >= scope.getStartInclusive()) {
            if (kitSlot < scope.getEndExclusive()) {
               int targetIndex = kitSlot - scope.getStartInclusive();
               Kit.ItemDescriptor kitItem = (Kit.ItemDescriptor)entry.getValue();
               int desiredItemId = kitItem.itemId();
               Item desiredItem = Item.byId(desiredItemId);
               ItemStack targetStack = stacks.get(targetIndex);
               if (!targetStack.isEmpty()) {
                  if (targetStack.getItem() == desiredItem) {
                     claimedSlots[targetIndex] = true;
                     continue;
                  }
               }

               for (int candidateIndex = 0; candidateIndex < stacks.size(); candidateIndex++) {
                  if (!claimedSlots[candidateIndex] && candidateIndex != targetIndex) {
                     ItemStack candidateStack = stacks.get(candidateIndex);
                     if (!candidateStack.isEmpty()) {
                        if (candidateStack.getItem() == desiredItem) {
                           Collections.swap(sourceSlots, targetIndex, candidateIndex);
                           Collections.swap(stacks, targetIndex, candidateIndex);
                           claimedSlots[targetIndex] = true;
                           continue kitTraversal;
                        }
                     }
                  }
               }
            }
         }
      }

      return sourceSlots.toArray(Integer[]::new);
   }

}
