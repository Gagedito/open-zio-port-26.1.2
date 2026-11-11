/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.ClientAccess;
import dev.zio.client.render.TextUtils;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TradeItemSelectorPanel extends TradeSelectorPanel<Item> implements ClientAccess {
   private final ItemListComponent itemList = new ItemListComponent(
      this,
      item -> TextUtils.containsIgnoreCase(
         item.getName(new ItemStack(item)).getString(),
         this.searchInput
            .getText()
      )
   );

   public TradeItemSelectorPanel(TradeItemsComponent tradeItems, boolean fromSide) {
      super(tradeItems, fromSide);
      this.children
         .add(this.itemList);
   }

   @Override
   protected void applySelection(Item item) {
      if (this.isFromSide()) {
         this.tradeItems
            .setToStack(
               Items.EMERALD.getDefaultInstance()
            );
         this.tradeItems
            .setFromStack(
               item.getDefaultInstance()
            );
      } else {
         this.tradeItems
            .setFromStack(
               Items.EMERALD.getDefaultInstance()
            );
         this.tradeItems
            .setToStack(
               item.getDefaultInstance()
            );
      }
   }

   @Override
   protected Item getSelection() {
      return this.itemList
         .getSelectedEntry();
   }

}
