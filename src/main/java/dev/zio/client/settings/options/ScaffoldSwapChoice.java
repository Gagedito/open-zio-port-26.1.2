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
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.settings.NamedEntry;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;

public enum ScaffoldSwapChoice implements ClientAccess, NamedEntry {
   NONE("None"),
   NORMAL("Normal") {
      @Override
      public void runWithSlot(int slot, Runnable action) {
         InventoryUtils.selectHotbarSlotForPlacement(slot);
         action.run();
      }
   },
   SILENT("Silent") {

      @Override
      public void runWithSlot(int slot, Runnable action) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         Inventory inventory = player.getInventory();
         int originalSlot = inventory.getSelectedSlot();

         InventoryUtils.selectHotbarSlotForPlacement(slot);
         action.run();

         InventoryUtils.selectHotbarSlotForPlacement(originalSlot);
      }

   };

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ScaffoldSwapChoice(String displayName) {
      this.displayName = displayName;
   }

   public void runWithSlot(int slot, Runnable action) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      Inventory inventory = player.getInventory();
      if (inventory.getSelectedSlot() == slot) {
         action.run();
      }
   }
}
