/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

public record MotionSample(
   double motionX,
   double motionY,
   double motionZ,
   double accelerationX,
   double accelerationZ,
   boolean collisionBelow,
   boolean sneaking,
   boolean fallFlying
) {}
