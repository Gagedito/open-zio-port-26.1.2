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
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.InputPressEvent;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;

import dev.zio.mixin.AbstractContainerScreenAccessor;
import dev.zio.mixin.KeyMappingAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.Type;
import net.minecraft.world.item.ItemStack;

public class InventoryTweaksModule extends Module {
   private final Setting<Boolean> lockSlotSetting;
   private final ElapsedTimer dropTimer = new ElapsedTimer();
   public final Setting<Boolean> fastMoveSetting;
   private final Setting<Boolean> onlyContainerSetting;
   private final Setting<Boolean> spamSetting;
   public final Setting<Boolean> fastArmorSetting;
   public final Setting<Boolean> bundleTweaksSetting;
   public final Setting<Boolean> xCarryTweaksSetting;
   private int lastQuickMovedSlotId;

   @EventHandler
   private void onDropKey(InputPressEvent event) {
      if (!this.lockSlotSetting.getValue() || this.isWorldUnavailable() || event.isMouse()) return;
      var dropKey = ((KeyMappingAccessor)ClientAccess.zioMinecraft.options.keyDrop).getKey();
      if (dropKey.getValue() != event.getCode() || ClientAccess.zioMinecraft.screen != null) return;
      if (ClientAccess.zioMinecraft.player.getMainHandItem().getMaxStackSize() != 1 || !this.dropTimer.hasElapsed(300L)) return;
      this.dropTimer.reset();
      event.cancel();
   }

   private int getClickSlotId(Slot slot) {
      CreativeModeTab selectedTab = CreativeModeInventoryScreen.selectedTab;
      boolean inventoryTab = Type.INVENTORY == selectedTab.getType();
      if (ClientAccess.zioMinecraft.screen instanceof CreativeModeInventoryScreen && inventoryTab) {
         return slot.getContainerSlot();
      }
      return slot.index;
   }

   public boolean shouldSwapBundleClickButton(Slot slot, int mouseButton) {
      if (!this.isToggled()) {
         return false;
      }
      Boolean bundleTweaksEnabled = this.bundleTweaksSetting.getValue();
      if (!bundleTweaksEnabled || (mouseButton != 1 && mouseButton != 0) || slot == null) {
         return false;
      }
      ItemStack carriedStack = ClientAccess.zioMinecraft.player.containerMenu.getCarried();
      TagKey bundleTag = ItemTags.BUNDLES;
      if (carriedStack.is(bundleTag)) {
         ItemStack targetStack = slot.getItem();
         if (!targetStack.isEmpty()) {
            return true;
         }
      }
      if (!carriedStack.isEmpty()) {
         ItemStack targetBundleStack = slot.getItem();
         TagKey targetBundleTag = ItemTags.BUNDLES;
         return targetBundleStack.is(targetBundleTag);
      }
      return false;
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent unusedEvent) {
      Boolean fastMoveEnabled = this.fastMoveSetting.getValue();
      if (!fastMoveEnabled || !(ClientAccess.zioMinecraft.screen instanceof AbstractContainerScreen)) {
         return;
      }
      if ((this.onlyContainerSetting.getValue()) && !(ClientAccess.zioMinecraft.screen instanceof ContainerScreen)
         && !(ClientAccess.zioMinecraft.screen instanceof ShulkerBoxScreen)) {
         return;
      }
      ItemStack carriedStack = ClientAccess.zioMinecraft.player.containerMenu.getCarried();
      if (!carriedStack.isEmpty()) {
         return;
      }
      Slot focusedSlot = ((AbstractContainerScreenAccessor)ClientAccess.zioMinecraft.screen).zio$getFocusedSlot();
      if (focusedSlot == null) {
         return;
      }
      if (focusedSlot.getItem().isEmpty()) {
         return;
      }
      Boolean spamEnabled = this.spamSetting.getValue();
      if (!spamEnabled) {
         if (this.getClickSlotId(focusedSlot) == this.lastQuickMovedSlotId) {
            return;
         }
      }
      byte mouseButton = -1;
      byte queriedMouseButton = 0;
      if (KeyUtils.isPhysicalMouseButtonDown(queriedMouseButton)) {
         mouseButton = 0;
      }
      if (KeyUtils.isPhysicalKeyDown(340) && mouseButton != -1) {
         MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
         int containerId = ClientAccess.zioMinecraft.player.containerMenu.containerId;
         int clickedSlotId = this.getClickSlotId(focusedSlot);
         ContainerInput clickType = ContainerInput.QUICK_MOVE;
         LocalPlayer clickPlayer = ClientAccess.zioMinecraft.player;
         gameMode.handleContainerInput(containerId, clickedSlotId, mouseButton, clickType, clickPlayer);
         this.lastQuickMovedSlotId = this.getClickSlotId(focusedSlot);
      }
   }

   public InventoryTweaksModule() {
      super("InventoryTweaks", "Modifies your inventory actions.", ModuleCategory.PLAYER, new String[0]);
      this.lockSlotSetting = this.add(new BooleanSetting("LockSlot", false));
      BooleanSetting fastMoveOption = new BooleanSetting("FastMove", true);
      fastMoveOption.markVisibilityDependency();
      this.fastMoveSetting = this.add(fastMoveOption);
      this.onlyContainerSetting = this.add(new BooleanSetting("OnlyContainer", false).dependsOn(this.fastMoveSetting));
      this.spamSetting = this.add(new BooleanSetting("Spam", false).dependsOn(this.fastMoveSetting));
      this.fastArmorSetting = this.add(new BooleanSetting("FastArmor", false));
      this.bundleTweaksSetting = this.add(new BooleanSetting("BundleTweaks", false));
      this.xCarryTweaksSetting = this.add(new BooleanSetting("XCarryTweaks", true));
   }

}
