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
import dev.zio.client.gui.screens.ShulkerPreviewScreen;
import dev.zio.client.managers.InventoryStateManager;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.ListFilterMode;

import java.util.Iterator;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class InventoryCleanerModule extends Module {
   private final Setting<Set<Item>> itemsSetting;
   private final Setting<ListFilterMode> selectionSetting;
   private final Setting<Integer> delaySetting;
   private final Setting<Integer> frequencySetting;
   private final Setting<Boolean> offhandSetting;
   private final Setting<Boolean> containersSetting;
   private final Setting<Boolean> onlyUISetting;
   private final Setting<Boolean> ignoreHotbarSetting;
   private final ElapsedTimer cleaningTimer;

   @EventHandler(priority = 100)
   private void discardSelectedStacks(LocalPlayerTickEvent event) {
      int discardAttempts = 0;
      ElapsedTimer capturedTimer = this.cleaningTimer;
      long delayMillis = (this.delaySetting.getValue()).intValue();
      if (!capturedTimer.hasElapsed(delayMillis)) {
         return;
      }
      Boolean cleanContainers = this.containersSetting.getValue();
      if (!cleanContainers
         && ClientAccess.zioMinecraft.player.inventoryMenu
            != ClientAccess.zioMinecraft.player.containerMenu) {
         return;
      }
      Screen screen = ClientAccess.zioMinecraft.screen;
      boolean supportedScreen = screen instanceof InventoryScreen || screen instanceof ContainerScreen
         || (screen instanceof ShulkerBoxScreen && !(screen instanceof ShulkerPreviewScreen));
      if ((this.onlyUISetting.getValue()) && !supportedScreen) {
         return;
      }
      boolean playerInventoryMenu = false;
      int slotCountForHotbar = ClientAccess.zioMinecraft.player.containerMenu.slots.size();
      if (ClientAccess.zioMinecraft.player.containerMenu instanceof InventoryMenu) {
         slotCountForHotbar--;
         playerInventoryMenu = true;
      }

      Iterator<Slot> slots = ClientAccess.zioMinecraft.player.containerMenu.slots.iterator();

      while (slots.hasNext()) {
         Slot slot = slots.next();

         ItemStack stack = slot.getItem();
         InventoryStateManager capturedClickManager = ClientContext.inventoryStateManager;

         byte clickGateKind = 1;

         if (InventoryStateManager.wouldReachClickLimit(capturedClickManager, clickGateKind)) {
            break;
         }

         if (stack.isEmpty()) {
            continue;
         }
         ListFilterMode selectedPolicy = this.selectionSetting.getValue();

         Item selectedItem = stack.getItem();
         Setting<Set<Item>> itemsOption = this.itemsSetting;

         if (!ListFilterMode.matches(selectedPolicy, selectedItem, itemsOption)) {
            continue;
         }

         if (slot.index >= slotCountForHotbar - (9)) {
            if (slot.index != (45)) {
               if (this.ignoreHotbarSetting.getValue()) {
                  continue;
               }
            }
         }

         if (slot.index == (45)) {
            Boolean cleanOffhand = this.offhandSetting.getValue();
            if (!cleanOffhand) {
               continue;
            }
         }

         if (playerInventoryMenu) {
            if (slot.index >= (5)) {
               if (slot.index <= (8)) {
                  continue;
               }
            }

            if (slot.index == 0) {
               continue;
            }
         }

         ContainerInput clickType = ContainerInput.THROW;
         byte throwButton = 1;
         int throwSlot = slot.index;
         InventoryUtils.clickSlot(throwSlot, throwButton, clickType);
         capturedTimer = this.cleaningTimer;

         capturedTimer.reset();
         discardAttempts++;
         Integer frequency = this.frequencySetting.getValue();
         if (discardAttempts >= frequency) {
            break;
         }
      }
   }

   public InventoryCleanerModule() {
      super("InventoryCleaner", "Gets rid of the selected items in your inventory.", ModuleCategory.PLAYER, new String[0]);
      DefaultedRegistry itemRegistry = BuiltInRegistries.ITEM;
      Item[] defaultItems = new Item[0];
      this.itemsSetting = this.add(new RegistrySetSetting<>("Items", itemRegistry, (Object[]) defaultItems));
      this.selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.WHITELIST));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 100, 0, 1000).withUnit("ms"));
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 4));
      this.offhandSetting = this.add(new BooleanSetting("Offhand", false));
      this.containersSetting = this.add(new BooleanSetting("Containers", false));
      this.onlyUISetting = this.add(new BooleanSetting("OnlyUI", false));
      this.ignoreHotbarSetting = this.add(new BooleanSetting("IgnoreHotbar", true));
      this.cleaningTimer = new ElapsedTimer();
   }
}
