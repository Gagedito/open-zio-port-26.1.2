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

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class VehicleMoveEvent extends LocalPlayerMoveEvent {

   public VehicleMoveEvent(Vec3 velocity) {
      super(velocity, null);
   }

   public static boolean isLocalPlayerVehicle(Object candidate) {
      return ClientAccess.zioMinecraft.player == null
         ? false
         : candidate instanceof Entity
            && ClientAccess.zioMinecraft.player.getVehicle()
               == candidate;
   }
}
