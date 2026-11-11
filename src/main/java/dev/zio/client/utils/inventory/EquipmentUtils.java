/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.Equippable;

public final class EquipmentUtils {
   private static final Map<String, Item> REPAIR_MATERIALS_BY_TAG;
   private static final List<EnchantmentRequirement> HELMET_ENCHANTMENTS;
   private static final List<EnchantmentRequirement> CHESTPLATE_ENCHANTMENTS;
   private static final List<EnchantmentRequirement> LEGGINGS_ENCHANTMENTS;
   private static final List<EnchantmentRequirement> BOOTS_ENCHANTMENTS;
   private static final List<EnchantmentRequirement> ELYTRA_ENCHANTMENTS;
   private static final List<EnchantmentRequirement> WEAPON_ENCHANTMENTS;

   private EquipmentUtils() {
   }

   public static boolean hasRequiredEnchantments(ItemStack stack) {
      DataComponentType<?> equippableType = DataComponents.EQUIPPABLE;
      if (stack.get(equippableType) instanceof Equippable equippable) {
         EquipmentSlot slot = equippable.slot();
         return switch (slot) {
            case MAINHAND, OFFHAND, SADDLE, BODY -> false;
            case FEET -> matchesEnchantments(stack, BOOTS_ENCHANTMENTS);
            case LEGS -> matchesEnchantments(stack, LEGGINGS_ENCHANTMENTS);
            case CHEST -> matchesEnchantments(stack, CHESTPLATE_ENCHANTMENTS);
            case HEAD -> matchesEnchantments(stack, HELMET_ENCHANTMENTS);
            default -> throw new MatchException(null, null);
         };
      } else {
         if (stack.has(DataComponents.WEAPON)) {
            return matchesEnchantments(stack, WEAPON_ENCHANTMENTS);
         }

         Item elytra = Items.ELYTRA;
         return stack.is(elytra) && matchesEnchantments(stack, ELYTRA_ENCHANTMENTS);
      }
   }

   public static List<ItemStack> getArmorStacks(Player player) {
      ArrayList<ItemStack> armorStacks = new ArrayList<>();

      for (EquipmentSlot slot : EquipmentSlot.values()) {
         if (slot.getType() == Type.HUMANOID_ARMOR) {
            Inventory inventory = player.getInventory();

            int inventoryIndex = slot.getIndex(36);
            armorStacks.add(inventory.getItem(inventoryIndex));
         }
      }

      return armorStacks;
   }

   private static boolean matchesEnchantments(ItemStack stack, Iterable<EnchantmentRequirement> requirements) {
      for (EnchantmentRequirement requirement : requirements) {
         ResourceKey enchantment = requirement.enchantment();
         int actualLevel = EnchantmentUtils.getLevel(enchantment, stack);
         if (actualLevel != requirement.level()) {
            return false;
         }
      }

      return true;
   }

   public static boolean isRepairMaterial(ItemStack stack, Item material) {
      DataComponentType<Repairable> repairableType = DataComponents.REPAIRABLE;
      Repairable repairable = stack.get(repairableType);
      if (repairable == null) {
         return false;
      }

      HolderSet<Item> repairItems = repairable.items();
      Optional<TagKey<Item>> tagKey = repairItems.unwrapKey();
      TagKey<Item> repairTag = tagKey.orElse(null);
      if (repairTag == null) {
         return false;
      }

      Map<String, Item> materialsByTag = REPAIR_MATERIALS_BY_TAG;
      Identifier tagLocation = repairTag.location();
      Item repairMaterial = materialsByTag.get(tagLocation.toString());
      return repairMaterial != null && repairMaterial == material;
   }

   static {

      REPAIR_MATERIALS_BY_TAG = Map.of(
         "minecraft:repairs_chain_armor", Items.IRON_INGOT,
         "minecraft:repairs_diamond_armor", Items.DIAMOND,
         "minecraft:repairs_gold_armor", Items.GOLD_INGOT,
         "minecraft:repairs_iron_armor", Items.IRON_INGOT,
         "minecraft:repairs_leather_armor", Items.LEATHER,
         "minecraft:repairs_netherite_armor", Items.NETHERITE_INGOT,
         "minecraft:repairs_turtle_armor", Items.TURTLE_SCUTE,
         "minecraft:repairs_wolf_armor", Items.ARMADILLO_SCUTE
      );
      HELMET_ENCHANTMENTS = List.of(
         new EnchantmentRequirement(Enchantments.PROTECTION, 4),
         new EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         new EnchantmentRequirement(Enchantments.MENDING, 1)
      );
      CHESTPLATE_ENCHANTMENTS = List.of(
         new EnchantmentRequirement(Enchantments.PROTECTION, 4),
         new EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         new EnchantmentRequirement(Enchantments.MENDING, 1)
      );
      LEGGINGS_ENCHANTMENTS = List.of(
         new EnchantmentRequirement(Enchantments.BLAST_PROTECTION, 4),
         new EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         new EnchantmentRequirement(Enchantments.MENDING, 1)
      );
      BOOTS_ENCHANTMENTS = List.of(
         new EnchantmentRequirement(Enchantments.PROTECTION, 4),
         new EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         new EnchantmentRequirement(Enchantments.MENDING, 1),
         new EnchantmentRequirement(Enchantments.FEATHER_FALLING, 4)
      );
      ELYTRA_ENCHANTMENTS = List.of(
         new EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         new EnchantmentRequirement(Enchantments.MENDING, 1)
      );
      WEAPON_ENCHANTMENTS = List.of(
         new EnchantmentRequirement(Enchantments.SHARPNESS, 5),
         new EnchantmentRequirement(Enchantments.FIRE_ASPECT, 2),
         new EnchantmentRequirement(Enchantments.KNOCKBACK, 2),
         new EnchantmentRequirement(Enchantments.SWEEPING_EDGE, 3),
         new EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         new EnchantmentRequirement(Enchantments.MENDING, 1)
      );
   }

   private record EnchantmentRequirement(
      ResourceKey enchantment,
      int level
   ) {
   }
}
