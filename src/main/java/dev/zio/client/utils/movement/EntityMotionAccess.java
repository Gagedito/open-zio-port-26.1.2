/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

import net.minecraft.world.phys.Vec3;

public interface EntityMotionAccess {
   float getCameraYaw();

   void setCameraPitch(float pitch);

   float getCameraPitch();

   void setCameraYaw(float yaw);

   Vec3 zio$getPrevVelocity();
}
