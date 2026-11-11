/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.ClientAccess;
import dev.zio.client.utils.inventory.InventorySortStrategy;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ComparatorInventorySortStrategy implements InventorySortStrategy {
   public static final InventorySortStrategy BY_TYPE;
   public static final InventorySortStrategy BY_NAME;
   private final Comparator<Integer> primaryComparator;

   public ComparatorInventorySortStrategy(Comparator primaryComparator) {
      this.primaryComparator = primaryComparator;
   }

   static {
      BY_TYPE = comparingStackProperty((Function<ItemStack, Integer>)ComparatorInventorySortStrategy::getCreativeTabIndex);
      BY_NAME = comparingStackProperty((Function<ItemStack, String>)stack -> stack.getHoverName().getString());
   }

   private Comparator<Integer> getSlotComparator() {
      return this.primaryComparator.thenComparingInt(slotValue -> {
         int slot = slotValue;
         ItemStack stack = InventorySortStrategy.getInventoryStack(slot);
         return stack.getDamageValue();
      }).thenComparingInt(slotValue -> {
         int slot = slotValue;
         ItemStack stack = InventorySortStrategy.getInventoryStack(slot);
         return -stack.getCount();
      });
   }

   @Override
   public Integer[] getSourceSlots(InventorySorterScopeChoice scope) {
      int startInclusive = scope.getStartInclusive();
      int endExclusive = scope.getEndExclusive();
      Stream<Integer> occupiedSlots = IntStream.range(startInclusive, endExclusive).filter(slot -> {
         ItemStack stack = InventorySortStrategy.getInventoryStack(slot);
         return !stack.isEmpty();
      }).boxed();
      return occupiedSlots.sorted(getSlotComparator()).toArray(Integer[]::new);
   }

   private static int getCreativeTabIndex(ItemStack stack) {
      List<CreativeModeTab> tabs = CreativeModeTabs.allTabs();
      CreativeModeTab firstTab = tabs.getFirst();
      if (!firstTab.hasAnyItems()) {
         ClientPacketListener connection = ClientAccess.zioMinecraft.player.connection;
         FeatureFlagSet enabledFeatures = connection.enabledFeatures();
         ClientLevel level = ClientAccess.zioMinecraft.level;
         Provider registries = level.registryAccess();
         CreativeModeTabs.tryRebuildTabContents(enabledFeatures, true, registries);
      }

      for (int tabIndex = 0; tabIndex < tabs.size(); tabIndex++) {
         CreativeModeTab tab = tabs.get(tabIndex);
         Item item = stack.getItem();
         ItemStack defaultStack = item.getDefaultInstance();
         if (tab.contains(defaultStack)) {
            return tabIndex;
         }
      }

      return tabs.size();
   }

   private static InventorySortStrategy comparingStackProperty(Function property) {
      Comparator<Integer> comparator = Comparator.comparing((Integer slotValue) -> {
         int inventorySlot = slotValue;
         return (Comparable)property.apply(InventorySortStrategy.getInventoryStack(inventorySlot));
      });
      return new ComparatorInventorySortStrategy(comparator);
   }
}
