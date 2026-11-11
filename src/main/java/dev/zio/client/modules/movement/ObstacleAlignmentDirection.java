/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;

import dev.zio.client.ClientAccess;
import dev.zio.client.managers.RotationManager;
import net.minecraft.core.BlockPos;

public enum ObstacleAlignmentDirection implements ClientAccess {
   XP,
   XN,
   ZP,
   ZN,
   XP_ZP,
   XN_ZP,
   XP_ZN,
   XN_ZN;

   public BlockPos positionAtOffset(Integer offset) {
      return switch (this) {
         case XP, XN -> {
            double x = ClientAccess.zioMinecraft.player.getX();
            yield BlockPos.containing(this == XP ? x + offset : x - offset, 0.0, 0.0);
         }
         case ZP, ZN -> {
            double z = ClientAccess.zioMinecraft.player.getZ();
            yield BlockPos.containing(0.0, 0.0, this == ZP ? z + offset : z - offset);
         }
         case XP_ZP -> BlockPos.containing(projectedX() + offset, 0.0, projectedZ() + offset);
         case XN_ZP -> BlockPos.containing(projectedX() - offset, 0.0, projectedZ() + offset);
         case XP_ZN -> BlockPos.containing(projectedX() + offset, 0.0, projectedZ() - offset);
         case XN_ZN -> BlockPos.containing(projectedX() - offset, 0.0, projectedZ() - offset);
      };
   }

   private static int projectedX() {
      double coordinateMagnitude = maximumHorizontalCoordinate();
      double playerX = ClientAccess.zioMinecraft.player.getX();
      return (int)(coordinateMagnitude * Math.signum(playerX));
   }

   private static int projectedZ() {
      double coordinateMagnitude = maximumHorizontalCoordinate();
      double playerZ = ClientAccess.zioMinecraft.player.getZ();
      return (int)(coordinateMagnitude * Math.signum(playerZ));
   }

   private static int maximumHorizontalCoordinate() {
      return (int)Math.max(Math.abs(ClientAccess.zioMinecraft.player.getX()), Math.abs(ClientAccess.zioMinecraft.player.getZ()));
   }

   public static ObstacleAlignmentDirection fromYaw(float yaw) {
      return switch (RotationManager.getYawSector(yaw)) {
         case 0 -> ZP;
         case 1 -> XN_ZP;
         case 2 -> XN;
         case 3 -> XN_ZN;
         case 4 -> ZN;
         case 5 -> XP_ZN;
         case 6 -> XP;
         case 7 -> XP_ZP;
         default -> null;
      };
   }
}
