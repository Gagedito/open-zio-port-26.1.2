/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.utils.inventory.ArmorSlot;
import dev.zio.client.managers.InventoryStateManager;
import dev.zio.client.utils.inventory.InventoryUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ArmorSwapAction implements ClientAccess {
   private boolean quickMove;
   private boolean alternative;
   private final ArmorSlot armorKind;
   private final int inventorySlot;

   ArmorSwapAction(ArmorSlot armorKind, int inventorySlot) {
      this.armorKind = armorKind;
      this.inventorySlot = inventorySlot;
   }

   public void setQuickMove(boolean quickMove) {
      this.quickMove = quickMove;
   }

   public void setAlternative(boolean alternative) {
      this.alternative = alternative;
   }

   protected void execute() {
      if (this.quickMove) {
         int armorContainerSlot = 8;
         ArmorSlot armorKindForRemoval = this.armorKind;
         armorContainerSlot -= armorKindForRemoval.getArmorIndex();
         ContainerInput quickMoveClick = ContainerInput.QUICK_MOVE;
         InventoryUtils.clickSlot(armorContainerSlot, 0, quickMoveClick);
         return;
      }

      boolean firstModeBlocked = InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 4);

      boolean secondModeBlocked = InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 3);

      int sourceContainerSlot = InventoryUtils.toMenuSlot(this.inventorySlot);

      ClientContext.inventoryStateManager.setClickBatching(true);
      if (this.alternative && !firstModeBlocked) {
         boolean canPlaceCarriedStack = true;
         ItemStack carriedBeforePlacement = InventoryUtils.getCarriedStack();
         if (!carriedBeforePlacement.isEmpty()) {
            ItemStack carriedForPlacement = InventoryUtils.getCarriedStack();
            canPlaceCarriedStack = this.returnCarriedStack(carriedForPlacement);
         }
         if (canPlaceCarriedStack) {
            int destinationContainerSlot = 8;
            ArmorSlot destinationArmorKind = this.armorKind;
            destinationContainerSlot -= destinationArmorKind.getArmorIndex();
            InventoryUtils.swapMenuSlots(sourceContainerSlot, destinationContainerSlot);
         }
      } else if (!secondModeBlocked) {
         int destinationContainerSlot = 8;
         ArmorSlot destinationArmorKind = this.armorKind;
         destinationContainerSlot -= destinationArmorKind.getArmorIndex();
         InventoryUtils.swapSlotsWithPickup(sourceContainerSlot, destinationContainerSlot);
      }

      ClientContext.inventoryStateManager.setClickBatching(false);
   }

   private boolean returnCarriedStack(ItemStack carriedStack) {
      Collection<Slot> slots = ClientAccess.zioMinecraft.player.containerMenu.slots;
      Stream<Slot> candidates = slots.stream()
         .filter(slot -> {
            int slotIndex = slot.index;
            return slotIndex > 8;
         })
         .filter(slot -> AbstractContainerMenu.canItemQuickReplace(slot, carriedStack, false));
      Comparator<Slot> slotOrder = Comparator.comparingInt(slot -> slot.index);
      Optional<Slot> destination = candidates.min(slotOrder);
      Consumer<Slot> pickup = slot -> {
         int slotIndex = slot.index;
         ContainerInput pickupClick = ContainerInput.PICKUP;
         InventoryUtils.clickSlot(slotIndex, 0, pickupClick);
      };
      destination.ifPresent(pickup);
      return destination.isPresent();
   }
}
