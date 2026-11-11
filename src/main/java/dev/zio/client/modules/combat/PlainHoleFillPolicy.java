/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.utils.world.HoleInfo;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.SpeedMineRebreakChoice;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PlainHoleFillPolicy implements ClientAccess {
   private static SpeedMineModule speedMineModule;
   protected final HoleFillModule holeFillModule;

   public PlainHoleFillPolicy(HoleFillModule holeFillModule) {
      this.holeFillModule = holeFillModule;
   }

   static {
      speedMineModule = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
   }

   public boolean shouldFill(HoleInfo hole) {
      if (speedMineModule.isToggled()) {
         Setting<SpeedMineRebreakChoice> rebreakSettingRef = speedMineModule.rebreakSetting;

         if (rebreakSettingRef.getValue() == SpeedMineRebreakChoice.INSTANT) {
            if (speedMineModule.getMiningPos() != null) {
               BlockPos miningPos = speedMineModule.getMiningPos();
               Vec3 miningCenter = miningPos.getCenter();
               AABB miningBoundsSnapshot = hole.bounds();
               if (miningBoundsSnapshot.contains(miningCenter)) {
                  return false;
               }
            }
         }
      }

      AABB widthBounds = hole.bounds();
      if (widthBounds.getXsize() != 1.0D || hole.bounds().getZsize() != 1.0D) {
         if (!this.holeFillModule.doublesSetting.getValue()) {
            return false;
         }
      }

      Vec3 eyeSnapshot = ClientAccess.zioMinecraft.player.getEyePosition();
      BlockPos rangePosition = hole.pos();
      Vec3 holeCenter = rangePosition.getCenter();
      double distance = eyeSnapshot.distanceTo(holeCenter);

      Float configuredRange = this.holeFillModule.rangeSetting.getValue();
      if (distance <= configuredRange.floatValue()) {
         double eyeY = ClientAccess.zioMinecraft.player.getEyePosition().y;
         BlockPos verticalPosition = hole.pos();
         double verticalDifference = eyeY - verticalPosition.getCenter().y;
         double absoluteVerticalDifference = Math.abs(verticalDifference);

         Float configuredVerticalRange = this.holeFillModule.verticalRangeSetting.getValue();
         return absoluteVerticalDifference <= configuredVerticalRange.floatValue();
      }
      return false;
   }

   public static void connectSpeedMineModule(ModuleRegistry registry) {
      speedMineModule = (SpeedMineModule)registry.getModule(
         SpeedMineModule.class
      );
   }
}
