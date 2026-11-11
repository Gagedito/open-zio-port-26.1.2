/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.integrations.xaero;

import net.minecraft.core.BlockPos;

public final class DisabledWaypointAdapter implements WaypointAdapter {
   @Override
   public void removeWaypoints(String name) {
   }

   @Override
   public boolean isAvailable() {
      return false;
   }

   @Override
   public void addWaypoint(String name, BlockPos position) {
   }
}
