/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import net.minecraft.world.effect.MobEffectInstance;

public final class LivingEntityAddEffectEvent extends CancellableEvent {
   private final MobEffectInstance effect;

   public LivingEntityAddEffectEvent(MobEffectInstance effect) {
      this.effect = effect;
   }

   public MobEffectInstance getEffect() {
      return this.effect;
   }
}
