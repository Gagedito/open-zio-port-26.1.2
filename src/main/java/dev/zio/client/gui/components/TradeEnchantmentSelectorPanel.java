/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.utils.inventory.EnchantmentUtils;
import dev.zio.client.render.TextUtils;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

public class TradeEnchantmentSelectorPanel extends TradeSelectorPanel<EnchantmentListEntry> {
   private final EnchantmentListComponent enchantmentList = new EnchantmentListComponent(
      this, this::matchesEnchantment
   );

   public TradeEnchantmentSelectorPanel(TradeItemsComponent tradeItems, boolean fromSide) {
      super(tradeItems, fromSide);
      this.children
         .add(this.enchantmentList);
   }

   @Override
   protected void applySelection(EnchantmentListEntry entry) {
      ItemStack stack = this.isFromSide()
         ? this.tradeItems
            .getFromStack()
         : this.tradeItems
            .getToStack();
      stack.enchant(
         EnchantmentUtils.getHolder(
            entry.enchantmentKey()
         ),
         entry.level()
      );
   }

   @Override
   protected EnchantmentListEntry getSelection() {
      return this.enchantmentList
         .getSelectedEntry();
   }

   @Override
   public int getWidth() {
      return super.getWidth()
         + 48;
   }

   private boolean matchesEnchantment(EnchantmentListEntry entry) {
      ItemStack stack = this.isFromSide()
         ? this.tradeItems
            .getFromStack()
         : this.tradeItems
            .getToStack();
      boolean supported = ((Enchantment)EnchantmentUtils.getHolder(
               entry.enchantmentKey()
            )
            .value())
         .isSupportedItem(stack);
      if (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK)) {
         supported = true;
      }

      return supported && TextUtils.containsIgnoreCase(
            entry.name(),
            this.searchInput
               .getText()
         );
   }

}
