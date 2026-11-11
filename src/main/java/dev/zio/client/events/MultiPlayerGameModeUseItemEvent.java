/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import net.minecraft.world.InteractionHand;

public final class MultiPlayerGameModeUseItemEvent extends CancellableEvent {
   private final InteractionHand hand;

   public MultiPlayerGameModeUseItemEvent(InteractionHand hand) {
      this.hand = hand;
   }

   public InteractionHand getHand() {
      return this.hand;
   }
}
