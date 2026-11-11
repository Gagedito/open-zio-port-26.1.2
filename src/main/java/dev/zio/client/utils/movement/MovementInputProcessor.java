/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

import dev.zio.client.integrations.ProtocolCompatibility;
import dev.zio.client.integrations.ProtocolVersionAccess;

import dev.zio.client.ClientAccess;

import dev.zio.mixin.LocalPlayerAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

public final class MovementInputProcessor implements ClientAccess {
   public void applyInputPreservingBobbing() {
      float previousYawBob = ClientAccess.zioMinecraft.player.yBobO;
      float previousPitchBob = ClientAccess.zioMinecraft.player.xBobO;
      float pitchBob = ClientAccess.zioMinecraft.player.xBob;
      float yawBob = ClientAccess.zioMinecraft.player.yBob;
      LocalPlayer player = ClientAccess.zioMinecraft.player;

      player.applyInput();
      ClientAccess.zioMinecraft.player.yBobO = previousYawBob;
      ClientAccess.zioMinecraft.player.xBobO = previousPitchBob;
      ClientAccess.zioMinecraft.player.xBob = pitchBob;
      ClientAccess.zioMinecraft.player.yBob = yawBob;
   }

   public Vec2 normalizeInput(Vec2 input) {
      ProtocolVersionAccess targetVersion = ProtocolCompatibility.getTargetVersion();
      if (targetVersion.olderEq_1_21_4()) {
         return ((LocalPlayerAccessor)ClientAccess.zioMinecraft.player)
            .zio$applyMovementSpeedFactors(input);
      }

      return input.normalized();
   }

   public MovementDirection getRelativeDirection(float movementYaw, float rotationYaw) {
      double difference = (Mth.degreesDifference(movementYaw, rotationYaw) + 360.0D) % 360.0D;
      MovementDirection[] directions = MovementDirection.values();
      return directions[(int)Math.round(difference / 45.0D) % directions.length];
   }
}
