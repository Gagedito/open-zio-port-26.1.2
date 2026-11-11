/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.entity;

import dev.zio.client.settings.options.SocialRole;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface PlayerState {
   void zio$setRole(SocialRole role);

   boolean zio$isNextToWall();

   SocialRole zio$getRole();

   boolean zio$wasCrawling();

   default float getItemUseProgress(ItemStack stack, LivingEntity entity) {
      float elapsedMillis = (float)(System.currentTimeMillis() - zio$getLastEatingTime());
      return elapsedMillis / (stack.getUseDuration(entity) * 50.0F + 50.0F);
   }

   float zio$getHealthFactor();

   long zio$getLastEatingTime();

   int zio$getLastAttackedTicksServer();

   void zio$resetLastAttackedServer();
}
