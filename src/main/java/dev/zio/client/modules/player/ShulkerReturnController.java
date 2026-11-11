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
import dev.zio.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import dev.zio.client.managers.SwingInteractionManager;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.InventoryStateManager;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.entity.SwingAction;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.managers.WorldStateManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ShulkerReturnController extends ChestStealerController {
   private final List<TrackedShulker> trackedShulkers = new ArrayList<>();
   private final Queue<TrackedShulker> pendingShulkers = new ArrayDeque<>();
   private boolean reopenRequested;
   private boolean handlingReturnClick;
   private boolean returnedThisTick;
   private boolean leftEnderChest;

   private boolean isPlayerInventorySlot(int slotIndex) {
      return slotIndex >= 0
         && slotIndex <= (26);
   }

   public ShulkerReturnController(ChestStealerModule module) {
      super(module);
   }

   public void onContainerSlotUpdate(int containerId, ItemStack stack) {
      Screen screen = ClientAccess.zioMinecraft.screen;
      if (!InventoryUtils.isEnderChestScreen(screen)) {
         if (containerId == ClientAccess.zioMinecraft.player.inventoryMenu.containerId) {
            for (TrackedShulker trackedShulker : this.trackedShulkers) {
               if (trackedShulker.matchesItemAndName(stack)) {
                  this.reopenRequested = true;
               }
            }
         }
      }
   }

   public void onInventoryMouseClick(
      MultiPlayerGameModeHandleInventoryMouseClickEvent event
   ) {
      Screen screen = ClientAccess.zioMinecraft.screen;
      if (InventoryUtils.isEnderChestScreen(screen)
         && !this.handlingReturnClick) {
         AbstractContainerMenu menu = ClientAccess.zioMinecraft.player.containerMenu;
         int clickedSlotId = event.getSlotId();
         Slot slot = menu.getSlot(clickedSlotId);
         ShulkerReturnController.TrackedShulker trackedShulker = new ShulkerReturnController.TrackedShulker(
            getTrackedItem(slot), (Component)slot.getItem().get(DataComponents.CUSTOM_NAME), event.getSlotId()
         );
         int clickedSlot = event.getSlotId();
         if (this.isPlayerInventorySlot(clickedSlot)) {
            ItemStack clickedStack = slot.getItem();
            TagKey shulkerTag = ItemTags.SHULKER_BOXES;
            if (clickedStack.is(shulkerTag)) {
               if (event.getClickType() == ContainerInput.SWAP) {
                  if (event.getButton() < (9)) {
                     this.pendingShulkers.add(trackedShulker);
                  }
               }

               if (event.getClickType() == ContainerInput.QUICK_MOVE) {
                  this.pendingShulkers.add(trackedShulker);
               }

               if (event.getClickType() == ContainerInput.PICKUP) {
                  this.pendingShulkers.poll();
               }

               return;
            }
         }
      }
   }

   private static Item getTrackedItem(Slot slot) {
      return slot.getItem().getItem();
   }

   private int findTrackedShulkerSlot(ShulkerReturnController.TrackedShulker trackedShulker) {
      int slotIndex = 27;
      while (true) {
         if (slotIndex > (62)) {
            return -1;
         }

         ItemStack stack = ClientAccess.zioMinecraft.player.containerMenu.getSlot(slotIndex).getItem();
         if (trackedShulker.matchesItemAndName(stack)) {
            return slotIndex;
         }

         slotIndex++;
      }
   }

   public boolean hasReturnedThisTick() {
      return this.returnedThisTick;
   }

   public void reopenRequestedShulker() {
      if (!this.reopenRequested) {
         return;
      }
      BlockPos position = this.findReopenPosition();
      if (position != null) {
         Direction fallbackDirection = Direction.UP;
         Direction direction = (Direction)BlockInteractionUtils.getStrictDirectionsOrFallback(position, fallbackDirection).getFirst();

         double hitOffset = 0.5D;
         BlockHitResult hitResult = new BlockHitResult(position.getCenter().relative(direction, hitOffset), direction, position, false);
         ClientAccess.zioMinecraft.player.closeContainer();
         InteractionHand interactionHand = InteractionHand.MAIN_HAND;
         PacketSender.sendUseItemOn(interactionHand, hitResult);
         SwingInteractionManager swingManager = ClientContext.swingManager;
         InteractionHand swingHand = InteractionHand.MAIN_HAND;
         SwingAction swingType = SwingAction.INTERACT;
         swingManager.swing(swingHand, swingType);
         RotationManager rotationManager = ClientContext.rotationManager;

         float[] rotations = RotationManager.getRotationsToBlock(position);
         rotationManager.requestRotation(rotations, 250);
      }
      this.reopenRequested = false;
   }

   private BlockPos findReopenPosition() {
      BlockPos lastInteracted = ChestStealerModule.getLastInteractedBlock(this.module);
      if (lastInteracted != null && this.isWithinReopenRange(lastInteracted)
         && ClientAccess.zioMinecraft.level.getBlockState(lastInteracted).is(Blocks.ENDER_CHEST)) {
         return lastInteracted;
      }
      BlockPos nearest = null;
      double nearestDistance = Double.MAX_VALUE;
      WorldStateManager containerTracker = ClientContext.worldStateManager;
      for (BlockEntity blockEntity : containerTracker.getBlockEntities()) {
         if (!(blockEntity instanceof EnderChestBlockEntity)) {
            continue;
         }
         BlockPos candidate = blockEntity.getBlockPos();
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         Vec3 eyePosition = player.getEyePosition();
         Vec3 center = candidate.getCenter();
         double distance = eyePosition.distanceTo(center);
         if (distance <= 5.0D && distance < nearestDistance) {
            nearestDistance = distance;
            nearest = candidate;
         }
      }
      return nearest;
   }

   private boolean isWithinReopenRange(BlockPos position) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      Vec3 eyePosition = player.getEyePosition();
      Vec3 center = position.getCenter();
      return eyePosition.distanceTo(center) <= 5.0D;
   }

   public void tick() {
      this.returnedThisTick = false;
      Screen screen = ClientAccess.zioMinecraft.screen;
      if (!InventoryUtils.isEnderChestScreen(screen)) {
         this.leftEnderChest = true;
         while (!this.pendingShulkers.isEmpty()) {
            this.trackedShulkers.add(this.pendingShulkers.poll());
         }
      } else if (!this.trackedShulkers.isEmpty() && this.leftEnderChest) {
         if (!this.module.hasScreenOpenTimedOut()) {
            return;
         }
         if (ClientAccess.zioMinecraft.player.containerMenu instanceof ChestMenu menu) {
            if (menu.getRowCount() == (3)) {
               InventoryStateManager inventoryCoordinator = ClientContext.inventoryStateManager;

               if (!InventoryStateManager.wouldReachClickLimit(inventoryCoordinator, (byte)1)) {
                  this.handlingReturnClick = true;
                  for (TrackedShulker selectedShulker : this.trackedShulkers) {
                     int matchingSlot = this.findTrackedShulkerSlot(selectedShulker);
                     if (matchingSlot != -1) {
                        if (matchingSlot >= (54)
                           && matchingSlot <= (62)) {
                           int originalSlot = selectedShulker.slotIndex();
                           int hotbarSlot = matchingSlot - (54);
                           InventoryUtils.swapWithHotbarSlot(originalSlot, hotbarSlot);
                        } else {
                           int originalSlot = selectedShulker.slotIndex();
                           InventoryUtils.swapSlotsWithPickup(matchingSlot, originalSlot);
                        }

                        this.returnedThisTick = true;
                        this.leftEnderChest = false;
                     }
                  }

                  this.handlingReturnClick = false;
                  this.trackedShulkers.clear();
               }
            }
         }
      }
   }

   private record TrackedShulker(
      Item item,
      Component customName,
      int slotIndex
   ) {
      public boolean matchesItemAndName(ItemStack stack) {
         if (stack.getItem() != this.item) {
            return false;
         }
         Object stackName = stack.get(DataComponents.CUSTOM_NAME);
         return Objects.equals(stackName, this.customName);
      }

   }
}
