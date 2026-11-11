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
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.zio.client.utils.inventory.ItemUtils;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.player.AutoEatModule;
import dev.zio.client.modules.player.NoInteractModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class MainhandModule extends Module implements LethalDamageTracker.LethalCheckPolicy {
   private static NoInteractModule noInteractModule = (NoInteractModule)ClientContext.getModule(NoInteractModule.class);
   private static AutoCrystalModule autoCrystalModule = (AutoCrystalModule)ClientContext.getModule(AutoCrystalModule.class);
   private static AutoEatModule autoEatModule = (AutoEatModule)ClientContext.getModule(AutoEatModule.class);
   private static AutoExpModule autoExpModule = (AutoExpModule)ClientContext.getModule(AutoExpModule.class);
   private static OffhandModule offhandModule = (OffhandModule)ClientContext.getModule(OffhandModule.class);
   final Setting<Integer> slotSetting;
   final Setting<Integer> delaySetting;
   private final Setting<Float> safeSetting;
   private final Setting<Boolean> autoSelectSetting;
   private final Setting<Integer> autoSelectDelaySetting;
   private final Setting<Boolean> lethalSetting;
   private final Setting<Float> healthSetting;
   private final Setting<Boolean> gappleBindSetting;
   private final Setting<Boolean> fallbackSetting;
   private final Setting<Boolean> crappleSetting;
   private final Setting<Boolean> hotbarSetting;
   private final Setting<Boolean> weaponSetting;
   private final Setting<Boolean> totemSetting;
   private final ElapsedTimer replacementTimer;
   private final ElapsedTimer autoSelectTimer;
   private final ElapsedTimer interactionCooldownTimer;
   private boolean tickReady;
   private int previousReplacementSlot;
   private boolean temporaryHotbarSelection;
   private int previousSelectedHotbarSlot;
   private final LethalDamageTracker lethalDamageTracker;

   @Override
   public String getInfo() {
      int itemCount = ClientAccess.zioMinecraft.player.getInventory().getNonEquipmentItems().stream().filter(stack -> {
         Item stackItem = stack.getItem();
         return stackItem == getDesiredItem();
      }).mapToInt(ItemStack::getCount).sum();
      return String.valueOf(itemCount);
   }

   @Override
   public void onEnable() {
      this.previousSelectedHotbarSlot = this.previousReplacementSlot = -1;
   }

   private boolean shouldTemporarilySelectGapple() {
      ItemStack mainhandStack = ClientAccess.zioMinecraft.player.getMainHandItem();

      if (offhandModule.isToggled()) {
         if (offhandModule.isGappleBindSafeInBlocks()) {
            OffhandModule bindOwner = offhandModule;
            Item heldItem = mainhandStack.getItem();
            if (bindOwner.canUseGappleBind(heldItem)) {
               return false;
            }
         }
      }
      if (!canUseGappleBind()) {
         return false;
      }
      TagKey swords = ItemTags.SWORDS;
      if (mainhandStack.is(swords) || mainhandStack.is(Items.MACE)) {
         if (this.weaponSetting.getValue()) {
            return true;
         }
      }
      Item totem = Items.TOTEM_OF_UNDYING;
      if (mainhandStack.is(totem)) {
         if (this.totemSetting.getValue()) {
            return true;
         }
      }
      return this.temporaryHotbarSelection;
   }

   public int getConfiguredHotbarSlot() {
      return this.slotSetting.getValue() - 1;
   }

   private boolean canUseGappleBind() {
      HitResult hitResult = ClientAccess.zioMinecraft.hitResult;
      if (WorldUtils.isInteractive(hitResult)) {
         if (!ClientAccess.zioMinecraft.player.isShiftKeyDown()) {
            if (!noInteractModule.isToggled()) {
               if (!ClientAccess.zioMinecraft.player.isUsingItem()) return false;

               if (ClientAccess.zioMinecraft.player.getUsedItemHand() != InteractionHand.MAIN_HAND) return false;

               if (!ItemUtils.isFood(ClientAccess.zioMinecraft.player.getMainHandItem())) return false;
            }
         }
      }
      KeyMapping useKey = ClientAccess.zioMinecraft.options.keyUse;
      if (!useKey.isDown()) return false;
      float health = EntityUtils.getPlayerHealthWithAbsorption();
      Float safeHealth = this.safeSetting.getValue();
      return health >= safeHealth;
   }

   private void autoSelectTotem() {
      if (this.autoSelectSetting.getValue()) {
         ItemStack configuredStack = ClientAccess.zioMinecraft.player.getInventory().getItem(getConfiguredHotbarSlot());
         Item configuredItem = configuredStack.getItem();
         if (configuredItem == Items.TOTEM_OF_UNDYING) {
            if (ClientAccess.zioMinecraft.player.isUsingItem()) {
               if (ClientAccess.zioMinecraft.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                  return;
               }
            }

            if (!this.lethalDamageTracker.shouldHoldTotem()) {
               float health = EntityUtils.getPlayerHealthWithAbsorption();
               Float requiredHealth = this.healthSetting.getValue();
               if (!(health <= requiredHealth)) {
                  return;
               }
            }
            ElapsedTimer selectTimer = this.autoSelectTimer;

            long delayMillis = (this.autoSelectDelaySetting.getValue()).intValue();
            if (selectTimer.hasElapsedAndReset(delayMillis)) {
               InventoryUtils.selectHotbarSlotForPlacement(getConfiguredHotbarSlot());
            }
         }
      }
   }

   private Item getGappleItem() {
      return this.crappleSetting.getValue() ? Items.GOLDEN_APPLE : Items.ENCHANTED_GOLDEN_APPLE;
   }

   private Item getDesiredItem() {
      int selectedSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
      if (selectedSlot == getConfiguredHotbarSlot()) {
         if (this.gappleBindSetting.getValue()) {
            if (canStartGappleBind()) {
               if (canUseGappleBind()) {
                  return getGappleItem();
               }
            }
         }
      }

      if (this.gappleBindSetting.getValue()) {
         if (this.fallbackSetting.getValue()) {
            if (!hasTotemAvailable()) {
               return getGappleItem();
            }
         }
      }
      return Items.TOTEM_OF_UNDYING;
   }

   private boolean hasTotemAvailable() {
      ItemStack configuredStack = ClientAccess.zioMinecraft.player.getInventory().getItem(getConfiguredHotbarSlot());
      Item totem = Items.TOTEM_OF_UNDYING;
      if (configuredStack.is(totem)) {
         return true;
      }
      int totemSlot = InventoryUtils.findInventorySlot((Predicate<ItemStack>)stack -> {
         Item searchedTotem = Items.TOTEM_OF_UNDYING;
         return stack.is(searchedTotem);
      }, true);
      return totemSlot != -1;
   }

   private void selectGappleTemporarily() {
      if (!ClientAccess.zioMinecraft.player.isUsingItem()) {
         if (!ClientAccess.zioMinecraft.player.getMainHandItem().is(getGappleItem())) {
            int gappleSlot = InventoryUtils.findHotbarSlot(getGappleItem());
            this.previousSelectedHotbarSlot = InventoryUtils.getSelectedSlot();
            InventoryUtils.selectHotbarSlotForPlacement(gappleSlot);
            this.temporaryHotbarSelection = true;
            return;
         }
      }
   }

   private boolean canStartGappleBind() {
      ItemStack mainhandStack = ClientAccess.zioMinecraft.player.getMainHandItem();

      if (ItemUtils.isFood(ClientAccess.zioMinecraft.player.getOffhandItem())) {
         return false;
      }
      Item totem = Items.TOTEM_OF_UNDYING;
      if (!mainhandStack.is(totem)) {
         if (!mainhandStack.is(getGappleItem())) {
            return false;
         }
      }
      return true;
   }

   @EventHandler(priority = 100)
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (this.isWorldUnavailable()) {
         return;
      }
      this.tickReady = event.hasPendingTicks();

      this.lethalDamageTracker.update();
      if (ClientAccess.zioMinecraft.player.containerMenu != ClientAccess.zioMinecraft.player.inventoryMenu) {
         return;
      }

      if (autoCrystalModule.isToggled()) {
         if (autoCrystalModule.forceSuicideSetting.getValue()) {
            return;
         }
      }

      if (autoEatModule.isToggled()) {
         if (autoEatModule.isEating()) {
            return;
         }
      }

      if (autoExpModule.isToggled()) {
         if (autoExpModule.isActive()) {
            return;
         }
      }

      if (this.hotbarSetting.getValue() && this.tickReady) {
         if (shouldTemporarilySelectGapple()) {
            selectGappleTemporarily();
            return;
         }
         if (this.temporaryHotbarSelection) {
            InventoryUtils.selectHotbarSlotForPlacement(this.previousSelectedHotbarSlot);
            this.temporaryHotbarSelection = false;
            return;
         }
      }
      if (this.tickReady) {
         autoSelectTotem();
      }
      Item desiredItem = getDesiredItem();

      ItemStack configuredStack = ClientAccess.zioMinecraft.player.getInventory().getItem(getConfiguredHotbarSlot());
      if (desiredItem != configuredStack.getItem()) {
         replaceConfiguredSlot(desiredItem);
      }
   }

   @EventHandler
   private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
      BlockHitResult hitResult = event.getHitResult();
      if (!WorldUtils.isInteractive(hitResult)) {
         return;
      }

      if (ClientAccess.zioMinecraft.player.isShiftKeyDown()) {
         return;
      }

      if (!noInteractModule.isToggled() && !event.isCancelled()) {
         this.interactionCooldownTimer.reset();
      }
   }

   private void replaceConfiguredSlot(Item desiredItem) {
      int replacementSlot = InventoryUtils.findInventorySlot((Predicate<ItemStack>)stack -> {
         return stack.is(desiredItem);
      }, true);
      if (replacementSlot < 9) {
         replacementSlot = InventoryUtils.toMenuSlot(replacementSlot);
      }
      if (this.previousReplacementSlot != -1) {
         ItemStack previousStack = ClientAccess.zioMinecraft.player.getInventory().getItem(this.previousReplacementSlot);
         if (previousStack.is(desiredItem) && replacementSlot != -1) {
            replacementSlot = this.previousReplacementSlot;
         }
      }
      if (replacementSlot != -1) {
         NonNullList menuSlots = ClientAccess.zioMinecraft.player.containerMenu.slots;
         if (replacementSlot < menuSlots.size()) {
            AbstractContainerMenu menu = ClientAccess.zioMinecraft.player.containerMenu;
            Slot slot = menu.getSlot(replacementSlot);
            ItemStack replacementStack = slot.getItem();
            if (!replacementStack.has(DataComponents.FOOD) || this.tickReady) {
               ElapsedTimer swapTimer = this.replacementTimer;

               long delayMillis = (this.delaySetting.getValue()).intValue();
               if (swapTimer.hasElapsedAndReset(delayMillis)) {
                  if (this.interactionCooldownTimer.hasElapsed(200L)) {
                     int configuredSlot = getConfiguredHotbarSlot();
                     InventoryUtils.swapWithHotbarSlot(replacementSlot, configuredSlot);

                     ClientContext.inventoryStateManager.close();
                     this.previousReplacementSlot = replacementSlot;
                  }
               }
               KeyMapping useKey = ClientAccess.zioMinecraft.options.keyUse;
               if (KeyUtils.isPhysicallyDown(useKey) && replacementStack.has(DataComponents.FOOD)) {
                  if (ClientAccess.zioMinecraft.player.isUsingItem()) {
                     if (ClientAccess.zioMinecraft.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                        ClientAccess.zioMinecraft.player.releaseUsingItem();
                     }
                  }

                  MultiPlayerGameMode gameModeForUse = ClientAccess.zioMinecraft.gameMode;
                  LocalPlayer playerForUse = ClientAccess.zioMinecraft.player;
                  InteractionHand offhand = InteractionHand.OFF_HAND;
                  gameModeForUse.useItem(playerForUse, offhand);
               }
            }
         }
      }
   }

   @Override
   public boolean isLethalCheckEnabled() {
      if (!this.lethalSetting.getValue()) {
         return false;
      }

      return this.autoSelectSetting.getValue();
   }

   public static void bindModuleConnections(ModuleRegistry registry) {
      noInteractModule = (NoInteractModule)registry.getModule(
         NoInteractModule.class
      );
      autoCrystalModule = (AutoCrystalModule)registry.getModule(
         AutoCrystalModule.class
      );
      autoEatModule = (AutoEatModule)registry.getModule(
         AutoEatModule.class
      );
      autoExpModule = (AutoExpModule)registry.getModule(
         AutoExpModule.class
      );
      offhandModule = (OffhandModule)registry.getModule(
         OffhandModule.class
      );
   }

   public MainhandModule() {
      super("Mainhand", "Auto totem for main hand.", ModuleCategory.COMBAT, new String[0]);
      this.slotSetting = this.add(new NumberSetting<>("Slot", 1, 1, 9));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 25, 0, 500).withUnit("ms"));
      this.safeSetting = this.add(new NumberSetting<>("Safe", 8.0F, 0.0F, 36.0F).withUnit("hp"));
      Setting<Boolean> autoSelectOption = this.add(new BooleanSetting("AutoSelect", false).markVisibilityDependency());
      this.autoSelectSetting = autoSelectOption;
      this.autoSelectDelaySetting = this.add(new NumberSetting<>("Delay", 25, 0, 500).withUnit("ms").dependsOn(autoSelectOption));
      this.lethalSetting = this.add(new BooleanSetting("Lethal", true).dependsOn(autoSelectOption));
      this.healthSetting = this.add(new NumberSetting<>("Health", 8.0F, 0.0F, 36.0F).withUnit("hp").dependsOn(autoSelectOption));
      Setting<Boolean> gappleBindOption = this.add(new BooleanSetting("GappleBind", true).markVisibilityDependency());
      this.gappleBindSetting = gappleBindOption;
      this.fallbackSetting = this.add(new BooleanSetting("Fallback", true).dependsOn(gappleBindOption));
      this.crappleSetting = this.add(new BooleanSetting("Crapple", false).dependsOn(gappleBindOption));
      Setting<Boolean> hotbarOption = this.add(new BooleanSetting("Hotbar", false).markVisibilityDependency());
      this.hotbarSetting = hotbarOption;
      this.weaponSetting = this.add(new BooleanSetting("Weapon", false).dependsOn(hotbarOption));
      this.totemSetting = this.add(new BooleanSetting("Totem", false).dependsOn(hotbarOption));
      this.replacementTimer = new ElapsedTimer();
      this.autoSelectTimer = new ElapsedTimer();
      this.interactionCooldownTimer = new ElapsedTimer();
      this.lethalDamageTracker = new LethalDamageTracker(this);
      this.autoSelectDelaySetting.setConfigName("SwapDelay");
   }
}
