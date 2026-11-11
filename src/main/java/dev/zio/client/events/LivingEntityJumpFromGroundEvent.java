/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public final class LivingEntityJumpFromGroundEvent extends CancellableEvent {
   private float jumpPower;

   public LivingEntityJumpFromGroundEvent(float jumpPower) {
      this.jumpPower = jumpPower;
   }

   public float getJumpPower() {
      return this.jumpPower;
   }

   public void setJumpPower(float jumpPower) {
      this.jumpPower = jumpPower;
   }
}
