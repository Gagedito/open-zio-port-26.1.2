/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import dev.zio.client.managers.KitRegistry;

import dev.zio.client.settings.NamedEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class Kit implements NamedEntry {
   private final Map itemsBySlot = new HashMap();
   private final String name;

   public Map getItemsBySlot() {
      return this.itemsBySlot;
   }

   @Override
   public String getName() {
      return this.name;
   }

   public Kit(String name) {
      this.name = name;
   }

   public record ItemDescriptor(
      int itemId,
      String potionSignature
   ) {

      public boolean matches(ItemStack stack) {
         Item item = stack.getItem();
         if (Item.getId(item) == this.itemId) {

            if (Objects.equals(
               KitRegistry.getPotionSignature(stack),
               this.potionSignature
            )) {
               return true;
            }
         }

         return false;
      }

   }
}
