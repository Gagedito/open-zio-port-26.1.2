/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.math;

import dev.zio.mixin.Vec3Accessor;
import net.minecraft.world.phys.Vec3;

public final class Vec3Utils {
   private Vec3Utils() {
      throw new AssertionError();
   }

   public static void setComponents(
      Vec3 vector, double x, double y, double z
   ) {
      Vec3Accessor mutableVector = (Vec3Accessor)vector;
      mutableVector.setX(x);
      mutableVector.setY(y);
      mutableVector.setZ(z);
   }

   public static float horizontalDistance(
      Vec3 start, Vec3 end
   ) {
      double startX = start.x;
      double startZ = start.z;
      double endX = end.x;
      double endZ = end.z;
      double deltaX = startX - endX;
      double deltaZ = startZ - endZ;
      return (float)Math.hypot(deltaX, deltaZ);
   }

   public static Vec3 lerp(
      float fraction, Vec3 start, Vec3 end
   ) {
      Vec3 difference = end.subtract(start);
      Vec3 scaledDifference = difference.scale((double)fraction);
      return start.add(scaledDifference);
   }
}
