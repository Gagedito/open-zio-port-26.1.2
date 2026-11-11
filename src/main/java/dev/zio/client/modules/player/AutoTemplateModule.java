/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import net.minecraft.world.item.Item;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.utils.ElapsedTimer;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.tags.ItemTags;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;


public final class AutoTemplateModule extends Module {
   // MC 26.1.2: ItemStack en un inicializador estatico crashea al cargar el entrypoint
   // ("Components not bound yet", los data components se bindean despues del freeze de
   // registros). Inicializacion perezosa en el primer uso (runtime, post-builder).
   private static ItemStack diamondRepairProbe;

   private static ItemStack diamondRepairProbe() {
      if (diamondRepairProbe == null) diamondRepairProbe = new ItemStack(Items.DIAMOND);
      return diamondRepairProbe;
   }
   private static final Comparator<ItemStack> MATERIAL_ORDER = Comparator.comparing(stack -> stack.is(Items.NETHERITE_INGOT));
   private final Setting<Set<Item>> templatesSetting;
   private final Setting<Set<Item>> materialsSetting;
   private final Setting<ItemsChoice> itemsSetting;
   private final Setting<Integer> delaySetting;
   private final Setting<Boolean> safeSetting;
   private final ElapsedTimer actionTimer = new ElapsedTimer();

   public AutoTemplateModule() {
      super("AutoTemplate", "Automatically applies selected smithing templates.", ModuleCategory.PLAYER, new String[]{"AutoUpgrade"});
      this.templatesSetting = this.add(new RegistrySetSetting<>("Templates", BuiltInRegistries.ITEM, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
      this.materialsSetting = this.add(new RegistrySetSetting<>("Materials", BuiltInRegistries.ITEM));
      this.itemsSetting = this.add(new EnumSetting<>("Items", ItemsChoice.ANY));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 100, 0, 1000).withUnit("ms"));
      this.safeSetting = this.add(new BooleanSetting("Safe", true));
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerPreTickEvent event) {
      if (this.isWorldUnavailable() || !(ClientAccess.zioMinecraft.screen instanceof SmithingScreen)) return;
      AbstractContainerMenu menu = ClientAccess.zioMinecraft.player.containerMenu;
      if (!this.actionTimer.hasElapsed((this.delaySetting.getValue()).longValue())) return;
      if (!menu.getSlot(3).getItem().isEmpty()) {
         this.quickMove(menu, 3);
         return;
      }
      Item template = this.findTemplate(menu);
      if (template == null) return;
      Predicate<ItemStack> templatePredicate = stack -> stack.is(template);
      Predicate<ItemStack> basePredicate = this.getBasePredicate(template);
      Predicate<ItemStack> materialPredicate = this.getMaterialPredicate(template);
      int materialSlot = this.findBestMaterialSlot(menu, materialPredicate);
      Predicate<ItemStack> preferredMaterial = materialPredicate.and(stack -> materialSlot == -1
         || MATERIAL_ORDER.compare(menu.getSlot(materialSlot).getItem(), stack) >= 0);
      if (this.clearInvalidInput(menu, 0, templatePredicate)
         || this.clearInvalidInput(menu, 1, basePredicate)
         || this.clearInvalidInput(menu, 2, preferredMaterial)) return;
      if (this.fillInput(menu, 0, this.findSourceSlot(menu, templatePredicate))) return;
      if (this.fillInput(menu, 1, this.findSourceSlot(menu, basePredicate))) return;
      this.fillInput(menu, 2, materialSlot);
   }

   private Item findTemplate(AbstractContainerMenu menu) {
      int reserve = this.safeSetting.getValue() ? 1 : 0;
      for (Item template : this.templatesSetting.getValue()) {
         int count = 0;
         for (var slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.is(template)) count += stack.getCount();
         }
         if (count > reserve && this.hasInput(menu, 1, this.getBasePredicate(template))
            && this.hasInput(menu, 2, this.getMaterialPredicate(template))) return template;
      }
      return null;
   }

   private Predicate<ItemStack> getBasePredicate(Item template) {
      return template == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE ? this::isEligibleItem
         : stack -> stack.is(ItemTags.TRIMMABLE_ARMOR) && !stack.has(DataComponents.TRIM);
   }

   private Predicate<ItemStack> getMaterialPredicate(Item template) {
      return template == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE ? stack -> stack.is(Items.NETHERITE_INGOT)
         : stack -> this.materialsSetting.getValue().contains(stack.getItem());
   }

   private boolean hasInput(AbstractContainerMenu menu, int slot, Predicate<ItemStack> predicate) {
      return predicate.test(menu.getSlot(slot).getItem()) || this.findSourceSlot(menu, predicate) != -1;
   }

   private boolean clearInvalidInput(AbstractContainerMenu menu, int slot, Predicate<ItemStack> predicate) {
      ItemStack stack = menu.getSlot(slot).getItem();
      if (stack.isEmpty() || predicate.test(stack)) return false;
      this.quickMove(menu, slot);
      return true;
   }

   private boolean fillInput(AbstractContainerMenu menu, int target, int source) {
      if (source == -1 || !menu.getSlot(target).getItem().isEmpty()) return false;
      InventoryUtils.swapSlotsWithPickup(source, target);
      this.actionTimer.reset();
      return true;
   }

   private int findSourceSlot(AbstractContainerMenu menu, Predicate<ItemStack> predicate) {
      for (int slot = 4; slot < 40; slot++) {
         ItemStack stack = menu.getSlot(slot).getItem();
         if (!stack.isEmpty() && predicate.test(stack)) return slot;
      }
      return -1;
   }

   private int findBestMaterialSlot(AbstractContainerMenu menu, Predicate<ItemStack> predicate) {
      int best = -1;
      for (int slot = 4; slot < 40; slot++) {
         ItemStack stack = menu.getSlot(slot).getItem();
         if (!stack.isEmpty() && predicate.test(stack)
            && (best == -1 || MATERIAL_ORDER.compare(stack, menu.getSlot(best).getItem()) < 0)) best = slot;
      }
      return best;
   }

   private void quickMove(AbstractContainerMenu menu, int slot) {
      InventoryUtils.clickSlot(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE);
      this.actionTimer.reset();
   }

   private boolean isEligibleItem(ItemStack stack) {
      if (!(stack.get(DataComponents.REPAIRABLE) instanceof Repairable repairable)
         || !repairable.isValidRepairItem(diamondRepairProbe())) return false;
      ItemsChoice choice = this.itemsSetting.getValue();
      return choice != ItemsChoice.TOOLS && stack.has(DataComponents.EQUIPPABLE)
         || choice != ItemsChoice.ARMOR && stack.has(DataComponents.TOOL);
   }

   public enum ItemsChoice implements NamedEntry {
      ANY("Any"), TOOLS("Tools"), ARMOR("Armor");
      private final String displayName;
      ItemsChoice(String displayName) { this.displayName = displayName; }
      @Override public String getName() { return this.displayName; }
   }
}
