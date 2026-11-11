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
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.exploit.XCarryModule;
import dev.zio.client.settings.NamedEntry;

import java.util.Set;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum OffhandItemChoice implements ClientAccess, NamedEntry {
   Totem("Totem", Items.TOTEM_OF_UNDYING),
   Crystal("Crystal", Items.END_CRYSTAL),
   Gapple("Gapple", Items.GOLDEN_APPLE) {
      @Override
      public Item getItem(boolean useCrapple) {
         return useCrapple ? super.getItem(true) : Items.ENCHANTED_GOLDEN_APPLE;
      }
   },
   Custom("Custom", Items.SHIELD) {
      @Override
      public Item getItem(boolean useCrapple) {
         return (OffhandItemChoice.offhandModule.customSetting.getValue()).stream().findAny().orElse(null);
      }
   };

   private static XCarryModule xCarryModule = (XCarryModule)ClientContext.getModule(XCarryModule.class);
   private static OffhandModule offhandModule = (OffhandModule)ClientContext.getModule(OffhandModule.class);
   private final String displayName;
   private final Item defaultItem;

   @Override
   public String getName() {
      return this.displayName;
   }

   OffhandItemChoice(String displayName, Item defaultItem) {
      this.displayName = displayName;
      this.defaultItem = defaultItem;
   }

   public Item getItem(boolean useCrapple) {
      return this.defaultItem;
   }

   public int findContainerSlot(boolean useCrapple) {
      Item itemToFind = this.getItem(useCrapple);
      if (itemToFind == null) return -1;

      if (ClientAccess.zioMinecraft.player.getOffhandItem().is(itemToFind)) return -1;
      if (ClientAccess.zioMinecraft.screen instanceof ShulkerBoxScreen || ClientAccess.zioMinecraft.screen instanceof ContainerScreen) {
         NonNullList<Slot> containerSlots = ClientAccess.zioMinecraft.player.containerMenu.slots;
         for (Slot slot : containerSlots) {
            ItemStack slotStack = slot.getItem();
            if (slotStack.is(itemToFind)) return slot.index;
         }
      }
      boolean includeXCarry = xCarryModule.isToggled();
      return InventoryUtils.findMenuSlot(itemToFind, includeXCarry);
   }

   public static void bindModuleRegistry(ModuleRegistry modules) {
      xCarryModule = (XCarryModule)modules.getModule(
         XCarryModule.class
      );
      offhandModule = (OffhandModule)modules.getModule(
         OffhandModule.class
      );
   }
}
