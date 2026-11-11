/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import java.util.Set;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.combat.AutoCrystalModule;
import dev.zio.client.modules.exploit.MultiTaskModule;
import dev.zio.client.modules.exploit.XCarryModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.ListFilterMode;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ReplenishModule extends Module {
   private static MultiTaskModule multiTaskModule;
   private static XCarryModule xCarryModule;
   private static AutoCrystalModule autoCrystalModule;
   private final Setting<Set<Item>> itemsSetting;
   private final Setting<ListFilterMode> selectionSetting;
   private final Setting<Integer> delaySetting;
   private final Setting<Integer> thresholdSetting;
   private final Setting<Boolean> sortSetting;
   private final Setting<Boolean> unstackableSetting;
   private final Setting<Boolean> renamedSetting;
   final Setting<Boolean> limitSetting;
   private final Setting<Boolean> motionSetting;
   private final Setting<Boolean> fallingSetting;
   private final List<RememberedStack> rememberedHotbar;
   private final ElapsedTimer replenishTimer;

   static {
      multiTaskModule = (MultiTaskModule)ClientContext.getModule(MultiTaskModule.class);
      xCarryModule = (XCarryModule)ClientContext.getModule(XCarryModule.class);
      autoCrystalModule = (AutoCrystalModule)ClientContext.getModule(AutoCrystalModule.class);
   }

   private boolean replenishStack(int hotbarSlot) {
      Inventory inventory = ClientAccess.zioMinecraft.player.getInventory();
      ItemStack target = inventory.getItem(hotbarSlot);
      if (!ListFilterMode.matches(this.selectionSetting.getValue(), target.getItem(),
            this.itemsSetting.getValue()) || target.isEmpty() || !target.isStackable()) return false;
      float threshold = (this.thresholdSetting.getValue()).floatValue() / 64.0F;
      if ((float)target.getCount() / target.getMaxStackSize() >= threshold) return false;
      int sourceSlot = -1;
      ItemStack source = ItemStack.EMPTY;
      for (int slot = 9; slot < 36; slot++) {
         ItemStack candidate = inventory.getItem(slot);
         if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, target) && candidate.getCount() > source.getCount()) {
            sourceSlot = slot;
            source = candidate;
         }
      }
      if (xCarryModule.isToggled() && !(ClientAccess.zioMinecraft.screen instanceof InventoryScreen)) {
         CraftingContainer crafting = ClientAccess.zioMinecraft.player.inventoryMenu.getCraftSlots();
         for (int slot = 0; slot < 4; slot++) {
            ItemStack candidate = crafting.getItem(slot);
            if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, target) && candidate.getCount() > source.getCount()) {
               sourceSlot = slot + 1;
               source = candidate;
            }
         }
      }
      if (sourceSlot == -1) return false;
      int count = source.getCount();
      boolean useSwap = count > source.getMaxStackSize() * threshold * 1.5D || count == source.getMaxStackSize();
      ClientContext.inventoryStateManager.setClickBatching(true);
      try {
         if (this.sortSetting.getValue() && count != 1 && target.getMaxStackSize() - target.getCount() > count) {
            InventoryUtils.clickSlot(sourceSlot, 0, ContainerInput.PICKUP);
            InventoryUtils.clickSlot(sourceSlot, 1, ContainerInput.PICKUP);
            InventoryUtils.clickSlot(InventoryUtils.toMenuSlot(hotbarSlot), 0, ContainerInput.PICKUP);
         } else if (InventoryUtils.isHotbarFull()) {
            InventoryUtils.clickSlot(sourceSlot, 0, ContainerInput.QUICK_MOVE);
         } else if (useSwap) {
            InventoryUtils.clickSlot(sourceSlot, hotbarSlot, ContainerInput.SWAP);
         } else {
            InventoryUtils.swapSlotsWithPickup(sourceSlot, InventoryUtils.toMenuSlot(hotbarSlot));
         }
      } finally {
         ClientContext.inventoryStateManager.setClickBatching(false);
      }
      return true;
   }

   private boolean areOtherScannedSlotsOccupied(int excludedSlot) {
      Inventory inventory = ClientAccess.zioMinecraft.player.getInventory();
      for (int slot = 0; slot < 9; slot++) {
         if (slot != excludedSlot && inventory.getItem(slot).isEmpty()) return false;
      }
      return true;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (this.isWorldUnavailable() || !this.replenishTimer.hasElapsed((this.delaySetting.getValue()).longValue())) return;
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      if (!player.containerMenu.getCarried().isEmpty()) return;
      if (MovementPhysics.isLocalPlayerMoving() && this.motionSetting.getValue()) return;
      if (player.getDeltaMovement().y() > 0.0D && this.fallingSetting.getValue()) return;
      if (multiTaskModule.isItemUseRestricted()) return;
      boolean containerOpen = ClientAccess.zioMinecraft.screen instanceof AbstractContainerScreen;
      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = player.getInventory().getItem(slot);
         RememberedStack remembered = this.rememberedHotbar.get(slot);
         boolean restore = this.unstackableSetting.getValue()
            && (remembered.item().getDefaultMaxStackSize() == 1 || this.renamedSetting.getValue() && remembered.customNamePresent());
         if (restore && stack.isEmpty() && remembered.item() != Items.AIR && !containerOpen && this.restoreMissingItem(remembered.item(), slot)) {
            this.replenishTimer.reset();
            return;
         }
         this.rememberedHotbar.set(slot, RememberedStack.from(stack));
      }
      if (containerOpen && !(ClientAccess.zioMinecraft.screen instanceof InventoryScreen)) return;
      for (int slot = 0; slot < 9; slot++) {
         if (this.replenishStack(slot)) {
            this.replenishTimer.reset();
            return;
         }
      }
   }

   private boolean restoreMissingItem(Item item, int hotbarSlot) {
      ListFilterMode selectionMode = this.selectionSetting.getValue();
      Collection selectedItemValues = this.itemsSetting.getValue();
      if (!ListFilterMode.matches(selectionMode, item, selectedItemValues)) {
         return false;
      }

      boolean forceSuicide = autoCrystalModule.isToggled() && autoCrystalModule.forceSuicideSetting.getValue();
      Predicate<ItemStack> restorePredicate = candidateStack -> {
         LocalPlayer offhandPlayer = ClientAccess.zioMinecraft.player;
         return candidateStack != offhandPlayer.getOffhandItem()
            && (!forceSuicide || candidateStack.getItem() != Items.TOTEM_OF_UNDYING);
      };
      int sourceSlot = InventoryUtils.findMenuSlot(item, restorePredicate, true);
      if (sourceSlot == -1 && item == Items.TOTEM_OF_UNDYING && !forceSuicide) {
         Predicate<ItemStack> fallbackPredicate = candidateStack -> {
            Item fallbackItem = candidateStack.getItem();
            if (fallbackItem == Items.ENCHANTED_GOLDEN_APPLE || fallbackItem == Items.GOLDEN_APPLE) {
               LocalPlayer offhandPlayer = ClientAccess.zioMinecraft.player;
               return candidateStack != offhandPlayer.getOffhandItem();
            }

            return false;
         };
         sourceSlot = InventoryUtils.findInventorySlot(fallbackPredicate, true);
      }

      if (sourceSlot < 36 && sourceSlot != -1) {
         if (this.areOtherScannedSlotsOccupied(hotbarSlot)) {
            MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
            int containerId = ClientAccess.zioMinecraft.player.containerMenu.containerId;
            int clickSourceSlot = sourceSlot;
            byte mouseButton = 0;
            ContainerInput quickMove = ContainerInput.QUICK_MOVE;
            LocalPlayer clickPlayer = ClientAccess.zioMinecraft.player;
            gameMode.handleContainerInput(containerId, clickSourceSlot, mouseButton, quickMove, clickPlayer);
         } else {
            int conversionHotbarSlot = hotbarSlot;
            int targetSlot = InventoryUtils.toMenuSlot(conversionHotbarSlot);
            InventoryUtils.swapSlotsWithPickup(sourceSlot, targetSlot);
         }

         return true;
      } else {
         return false;
      }
   }

   public static void bindModules(ModuleRegistry registry) {
      multiTaskModule = (MultiTaskModule)registry.getModule(
         MultiTaskModule.class
      );
      xCarryModule = (XCarryModule)registry.getModule(
         XCarryModule.class
      );
      autoCrystalModule = (AutoCrystalModule)registry.getModule(
         AutoCrystalModule.class
      );
   }

   public ReplenishModule() {
      super("Replenish", "Replenishes your hotbar.", ModuleCategory.PLAYER, new String[0]);
      this.itemsSetting = this.add(new RegistrySetSetting<>("Items", BuiltInRegistries.ITEM,
         new Item[]{Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.EXPERIENCE_BOTTLE, Items.ENCHANTED_GOLDEN_APPLE}));
      this.selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.ANY));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 50, 0, 500).withUnit("ms"));
      this.thresholdSetting = this.add(new NumberSetting<>("Threshold", 32, 16, 63));
      this.sortSetting = this.add(new BooleanSetting("Sort", false));
      this.unstackableSetting = this.add(new BooleanSetting("Unstackable", false).markVisibilityDependency());
      this.renamedSetting = this.add(new BooleanSetting("Renamed", true).dependsOn(this.unstackableSetting));
      this.limitSetting = this.add(new BooleanSetting("Limit", true).markAsGroup());
      Predicate<Object> motionVisibility = ignored -> this.limitSetting.isExpanded();
      this.motionSetting = this.add(new BooleanSetting("Motion", false, motionVisibility));
      Predicate<Object> fallingVisibility = ignored -> this.limitSetting.isExpanded();
      this.fallingSetting = this.add(new BooleanSetting("Falling", false, fallingVisibility));

      ReplenishModule.RememberedStack emptyEntry = ReplenishModule.RememberedStack.EMPTY;
      this.rememberedHotbar = NonNullList.withSize(9, emptyEntry);
      this.replenishTimer = new ElapsedTimer();

      this.setDrawn(false);
   }

   private record RememberedStack(
      Item item,
      boolean customNamePresent
   ) {
      public static final ReplenishModule.RememberedStack EMPTY = new ReplenishModule.RememberedStack(Items.AIR, false);

      public static ReplenishModule.RememberedStack from(
         ItemStack stack
      ) {
         return new RememberedStack(stack.getItem(), stack.has(DataComponents.CUSTOM_NAME));
      }

   }
}
