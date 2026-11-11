/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.ClientAccess;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.settings.NamedEntry;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public enum ChorusControlYawChoice implements ClientAccess, NamedEntry {
   KEEP("Keep"),
   INVERT("Invert"),
   POS("Pos");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ChorusControlYawChoice(String displayName) {
      this.displayName = displayName;
   }

   public float resolveYaw(Vec3 destination, Vec3 eyePosition) {
      if (this == KEEP) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         return player.getYRot();
      }

      float yaw = RotationManager.calculateRotations(destination, eyePosition)[0];
      if (this == INVERT) {
         yaw += 180.0F;
      }

      return yaw;
   }

}
