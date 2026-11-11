/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
   @Accessor("useItemRemaining")
   void setItemUseTimeLeft(int useItemRemaining);

   @Accessor("swimAmount")
   void zio$setLeaningPitch(float swimAmount);

   @Accessor("swimAmount")
   float zio$getLeaningPitch();

   @Accessor("swimAmountO")
   float zio$getLastLeaningPitch();

   @Accessor("swimAmountO")
   void zio$setLastLeaningPitch(float swimAmountO);

   @Accessor("fallFlyTicks")
   void zio$setGlidingTicks(int fallFlyTicks);

   @Invoker("getCurrentSwingDuration")
   int getSwingDuration();

   @Accessor("DATA_EFFECT_PARTICLES")
   static EntityDataAccessor zio$potion_swirls() {
      return null;
   }
}
