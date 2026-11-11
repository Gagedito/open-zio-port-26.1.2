/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.KitRegistry;
import dev.zio.client.managers.InventoryStateManager;
import dev.zio.client.utils.inventory.Kit;
import dev.zio.client.utils.inventory.InventoryUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public class ShulkerRefillController extends ChestStealerController {
   private int savedSelectedSlot = -1;
   private boolean pendingClose;

   public ShulkerRefillController(ChestStealerModule module) {
      super(module);
   }

   private RefillSelection findBestShulker(ChestMenu menu, Kit profile) {
      int bestSlot = -1;
      float bestScore = 0.001F;
      for (int slot = 0; slot < 27; slot++) {
         ItemStack stack = menu.getSlot(slot).getItem();
         if (!stack.is(ItemTags.SHULKER_BOXES)) continue;
         float score = this.scoreContents(stack, profile);
         if (score > bestScore) {
            bestScore = score;
            bestSlot = slot;
         }
      }
      return new RefillSelection(bestSlot, bestScore);
   }

   private float scoreContents(ItemStack shulkerStack, Kit profile) {
      ItemContainerContents contents = (ItemContainerContents)shulkerStack.get(DataComponents.CONTAINER);
      if (contents == null) {
         return 0;
      }

      ArrayList<ItemStack> contentStacks = new ArrayList<>();
      for (ItemStack contentStack : contents.nonEmptyItemCopyStream().toList()) {
         contentStacks.add(contentStack);
      }

      float score = 0.0F;

      Iterator profileEntries = profile.getItemsBySlot().entrySet().iterator();

      while (profileEntries.hasNext()) {
         Entry profileEntry = (Entry)profileEntries.next();
         Kit.ItemDescriptor desiredStack = (Kit.ItemDescriptor)profileEntry.getValue();
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         Inventory inventory = player.getInventory();
         int inventorySlot = (Integer)profileEntry.getKey();
         ItemStack inventoryStack = inventory.getItem(inventorySlot);
         float missingFraction = 1.0F - (float)inventoryStack.getCount() / this.getStackCapacity(inventoryStack);
         if (desiredStack.matches(inventoryStack) && missingFraction == 0.0F) {
            continue;
         }
         if (inventoryStack.is(ItemTags.SWORDS)) {
            continue;
         }
         float bestFraction = 0.0F;
         for (ItemStack candidateStack : contentStacks) {
            float fraction = (float)candidateStack.getCount() / this.getStackCapacity(candidateStack);
            if (desiredStack.matches(candidateStack) && fraction > bestFraction) {
               bestFraction = fraction;
            }
         }
         if (bestFraction > 0.0F) {
            score += missingFraction * bestFraction;
         }
      }

      return score;
   }

   private int getStackCapacity(ItemStack stack) {
      return stack.isEmpty() ? 64 : stack.getMaxStackSize();
   }

   private int findSwordOrEmptySlot(ChestMenu menu) {
      for (int slot = 54; slot <= 62; slot++) {
         if (menu.getSlot(slot).getItem().is(ItemTags.SWORDS)) return slot;
      }
      for (int slot = 54; slot <= 62; slot++) {
         if (menu.getSlot(slot).getItem().isEmpty()) return slot;
      }
      return 54;
   }

   private void restoreSelectedSlot() {
      if (this.savedSelectedSlot != -1) {
         int selectedSlot = this.savedSelectedSlot;

         InventoryUtils.selectHotbarSlot(selectedSlot);
         this.savedSelectedSlot = -1;
      }
   }

   public void tick() {
      if (this.pendingClose) {
         ClientAccess.zioMinecraft.player.closeContainer();
         this.pendingClose = false;
         return;
      }
      if (!InventoryUtils.isEnderChestScreen(ClientAccess.zioMinecraft.screen)) {
         this.restoreSelectedSlot();
         return;
      }
      if (!this.module.hasScreenOpenTimedOut()
            || !(ClientAccess.zioMinecraft.player.containerMenu instanceof ChestMenu menu) || menu.getRowCount() != 3) return;
      if (InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 1)) return;
      Kit profile = KitRegistry.getCurrentKit(ClientContext.kitRegistry);
      if (profile == null) return;
      RefillSelection selection = this.findBestShulker(menu, profile);
      if (selection.slotIndex() == -1) return;
      for (int slot = 54; slot <= 62; slot++) {
         ItemStack stack = menu.getSlot(slot).getItem();
         if (stack.is(ItemTags.SHULKER_BOXES) && this.scoreContents(stack, profile) >= selection.score()) return;
      }
      int hotbarSlot = this.findSwordOrEmptySlot(menu) - 54;
      InventoryUtils.swapWithHotbarSlot(selection.slotIndex(), hotbarSlot);
      this.module.openController.requestOpen();
      this.savedSelectedSlot = hotbarSlot;
      if (this.module.shouldCloseAutomatically()) this.pendingClose = true;
   }

   record RefillSelection(int slotIndex, float score) {
   }
}
