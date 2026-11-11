/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.access;

public interface LocalPlayerTickAccess {
   void sendMovementPacketsWrapper();

   void superTick();

   void resetEvent();

   void zio$updateServer(float yaw, float pitch);

   void resetRotations();
}
