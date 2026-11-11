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

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;

public class OffhandSwapStrategy implements ActionSwapStrategy {

   @Override
   public SwapSlots findSlots(Item[] items) {

      return new SwapSlots(InventoryUtils.toMenuSlot(InventoryUtils.findInventorySlot(items)), 40);
   }

   @Override
   public void apply(SwapSlots slots) {
      MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
      int containerId = ClientAccess.zioMinecraft.player.containerMenu.containerId;
      gameMode.handleContainerInput(containerId, slots.sourceSlot(), 40, ContainerInput.SWAP, ClientAccess.zioMinecraft.player);
   }

   @Override
   public void undo(SwapSlots slots) {
      MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
      int containerId = ClientAccess.zioMinecraft.player.containerMenu.containerId;
      gameMode.handleContainerInput(containerId, slots.sourceSlot(), 40, ContainerInput.SWAP, ClientAccess.zioMinecraft.player);
   }

}
