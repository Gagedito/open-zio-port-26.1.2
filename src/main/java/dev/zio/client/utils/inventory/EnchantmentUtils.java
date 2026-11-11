/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import dev.zio.client.ClientAccess;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class EnchantmentUtils implements ClientAccess {
   private EnchantmentUtils() {
      throw new IllegalArgumentException("Illegal constructor call");
   }

   public static int getLevel(ResourceKey enchantment, ItemStack stack) {
      if (stack.isEmpty()) {
         return 0;
      }
      Iterator iterator = getEntries(stack).iterator();

      while (iterator.hasNext()) {
         Entry entry = (Entry)iterator.next();
         if (((Holder)entry.getKey()).is(enchantment)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }

   public static int getLevel(
      ResourceKey enchantment, EquipmentSlot slot, LivingEntity entity
   ) {
      ItemStack stack = entity.getItemBySlot(slot);
      return getLevel(enchantment, stack);
   }

   public static int getLevel(ResourceKey enchantment, EquipmentSlot slot) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      return getLevel(enchantment, slot, player);
   }

   public static boolean hasEnchantment(ResourceKey enchantment, ItemStack stack) {
      return getLevel(enchantment, stack) > 0;
   }

   public static boolean hasEnchantment(
      ResourceKey enchantment, EquipmentSlot slot, LivingEntity entity
   ) {
      return getLevel(enchantment, slot, entity) > 0;
   }

   public static boolean hasEnchantment(ResourceKey enchantment, EquipmentSlot slot) {
      return getLevel(enchantment, slot) > 0;
   }

   public static Map getLevels(ItemStack stack) {
      if (stack.isEmpty()) {
         return Collections.emptyMap();
      }

      HashMap levels = new HashMap();
      Iterator iterator = getEntries(stack).iterator();

      while (iterator.hasNext()) {
         Entry entry = (Entry)iterator.next();
         Optional key = ((Holder)entry.getKey()).unwrapKey();
         if (!key.isEmpty()) {
            ResourceKey enchantmentKey = (ResourceKey)key.get();
            int level = entry.getIntValue();
            levels.put(enchantmentKey, level);
         }
      }

      return levels;
   }

   private static Set getEntries(ItemStack stack) {
      Item enchantedBook = Items.ENCHANTED_BOOK;
      if (stack.is(enchantedBook) && stack.has(DataComponents.STORED_ENCHANTMENTS)) {
         return ((ItemEnchantments)stack.get(DataComponents.STORED_ENCHANTMENTS)).entrySet();
      } else {
         return stack.getEnchantments().entrySet();
      }
   }

   public static Holder getHolder(ResourceKey enchantment) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      return (Holder)level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(enchantment.identifier()).orElse(null);
   }

   public static Holder getHolder(String name) {
      ResourceKey key = getKey(name);
      return getHolder(key);
   }

   public static ResourceKey getKey(String name) {
      ResourceKey registryKey = Registries.ENCHANTMENT;
      Identifier identifier = Identifier.withDefaultNamespace(name);
      return ResourceKey.create(registryKey, identifier);
   }
}
