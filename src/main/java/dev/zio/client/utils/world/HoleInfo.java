/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public record HoleInfo(
   HoleInfo.Safety safety,
   BlockPos pos,
   AABB bounds,
   boolean trapped
) {

   public enum Safety {
      SAFE,
      UNSAFE
   }
}
