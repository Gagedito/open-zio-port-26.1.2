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
import dev.zio.client.ClientContext;
import dev.zio.client.utils.world.NukerBlockQueue;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.player.NukerModule;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public enum NukerShapeChoice implements NamedEntry {
   SPHERE("Sphere") {
      @Override
      public List collectPositions(NukerModule nukerModule) {
         LocalPlayer playerForPosition = ClientAccess.zioMinecraft.player;
         Vec3 positionSnapshot = playerForPosition.position();
         Setting<Float> rangeSettingRef = nukerModule.rangeSetting;
         Float configuredRange = rangeSettingRef.getValue();
         float rangeSnapshot = configuredRange;
         return new ArrayList<>(WorldUtils.collectSphereBlocks(positionSnapshot, rangeSnapshot, true));
      }
   },
   TUNNEL("Tunnel") {
      @Override
      public List collectPositions(NukerModule nukerModule) {
         ArrayList positions = new ArrayList();
         Setting<Integer> heightSettingRef = nukerModule.heightSetting;
         double height;
         if (heightSettingRef.isAtMinimum()) {
            LocalPlayer playerForHeight = ClientAccess.zioMinecraft.player;
            double playerHeight = playerForHeight.getBbHeight();
            height = Math.ceil(playerHeight);
         } else {
            Setting<Integer> configuredHeightSetting = nukerModule.heightSetting;
            Integer configuredHeight = configuredHeightSetting.getValue();
            height = configuredHeight.intValue();
         }

         int distance = 0;

         while (true) {
            Setting<Float> rangeSettingRef = nukerModule.rangeSetting;
            Float configuredRange = rangeSettingRef.getValue();
            double range = configuredRange.floatValue();
            if (!((double)distance <= Math.ceil(range))) {
               return positions;
            }

            for (int heightOffset = 0; heightOffset < height; heightOffset++) {
               LocalPlayer playerForPosition = ClientAccess.zioMinecraft.player;
               BlockPos position = playerForPosition.blockPosition();
               position = position.above(heightOffset);
               LocalPlayer playerForDirection = ClientAccess.zioMinecraft.player;
               Direction directionSnapshot = playerForDirection.getDirection();
               position = position.relative(directionSnapshot, distance);
               positions.add(position);
            }

            distance++;
         }
      }
   },
   QUEUE("Queue") {

      @Override
      public List collectPositions(NukerModule nukerModule) {

         NukerBlockQueue queueSnapshot = nukerModule.getBlockQueue();
         Setting<Boolean> extraBreakSettingRef = NukerShapeChoice.speedMineModule.extraBreakSetting;
         Boolean extraBreakEnabled = extraBreakSettingRef.getValue();
         boolean extraBreakFlag = extraBreakEnabled;

         return queueSnapshot.getNextPositions(extraBreakFlag);
      }

   },
   CROSSHAIR("Crosshair") {
      @Override
      public List collectPositions(NukerModule nukerModule) {
         if (ClientAccess.zioMinecraft.hitResult instanceof BlockHitResult hitResult) {
            return new ArrayList<>(Collections.singletonList(hitResult.getBlockPos()));
         } else {
            return Collections.emptyList();
         }
      }
   };

   private static SpeedMineModule speedMineModule = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   NukerShapeChoice(String displayName) {
      this.displayName = displayName;
   }

   public abstract List collectPositions(NukerModule nukerModule);

   public static void bindModules(ModuleRegistry modules) {
      speedMineModule = (SpeedMineModule)modules.getModule(SpeedMineModule.class);
   }
}
