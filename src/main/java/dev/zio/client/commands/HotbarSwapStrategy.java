/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.ClientAccess;
import dev.zio.client.utils.inventory.InventoryUtils;

import net.minecraft.world.item.Item;

public class HotbarSwapStrategy implements ActionSwapStrategy {

   @Override
   public SwapSlots findSlots(Item[] items) {
      int sourceSlot = InventoryUtils.findHotbarSlot(items);

      return new SwapSlots(sourceSlot, ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot());
   }

   @Override
   public void apply(SwapSlots slots) {
      InventoryUtils.selectHotbarSlot(slots.sourceSlot());
   }

   @Override
   public void undo(SwapSlots slots) {
      InventoryUtils.selectHotbarSlot(slots.targetSlot());
   }

}
