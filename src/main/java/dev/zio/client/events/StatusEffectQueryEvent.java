/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import net.minecraft.core.Holder;

public final class StatusEffectQueryEvent extends CancellableEvent {
   private final Holder effect;

   public StatusEffectQueryEvent(Holder effect) {
      this.effect = effect;
   }

   public Holder getEffect() {
      return this.effect;
   }
}
