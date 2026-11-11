/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import dev.zio.client.ClientAccess;
import dev.zio.client.settings.options.InventorySorterScopeChoice;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public interface InventorySortStrategy {
   static ItemStack getInventoryStack(int inventorySlot) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      return player.getInventory().getItem(inventorySlot);
   }

   Integer[] getSourceSlots(InventorySorterScopeChoice scope);
}
