/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import dev.zio.client.ClientAccess;

public final class RotationRequestEvent extends CancellableEvent implements ClientAccess {

   public float getPlayerPitch() {
      return ClientAccess.zioMinecraft.player.getXRot();
   }

   public float getPlayerYaw() {
      return ClientAccess.zioMinecraft.player.getYRot();
   }
}
