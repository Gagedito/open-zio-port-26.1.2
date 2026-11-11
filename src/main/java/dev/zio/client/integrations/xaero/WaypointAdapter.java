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

public interface WaypointAdapter {
   void removeWaypoints(String name);

   boolean isAvailable();

   void addWaypoint(String name, BlockPos position);
}
