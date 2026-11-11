/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.world;

import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.inventory.ExtraBreakTracker;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.player.NukerModule;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class NukerBlockQueue implements ClientAccess {
   private static SpeedMineModule speedMineModule = ClientContext.getModule(SpeedMineModule.class);
   private final List<BlockPos> positions = new ArrayList<>();
   private final NukerModule nukerModule;

   public void pruneInvalidPositions() {
      this.positions.removeIf(this::shouldRemovePosition);
   }

   public List<BlockPos> getPositions() {
      return this.positions;
   }

   public NukerBlockQueue(NukerModule nukerModule) {
      this.nukerModule = nukerModule;
   }

   private boolean shouldRemovePosition(BlockPos blockPos) {
      SpeedMineModule speedMineForToggle = speedMineModule;
      if (speedMineForToggle.isToggled()) {
         SpeedMineModule speedMineSnapshot = speedMineModule;

         ExtraBreakTracker pendingTaskSnapshot = speedMineSnapshot.getExtraBreakTracker();

         if (pendingTaskSnapshot.isTracked(blockPos)) {
            return true;
         }
      }

      if (!WorldUtils.canMineBlock(blockPos)) {
         return true;
      }

      Vec3 eyePosition = ClientAccess.zioMinecraft.player.getEyePosition();
      AABB blockBounds = new AABB(blockPos);
      Vec3 closestPoint = BoundingBoxUtils.clampToBounds(eyePosition, blockBounds);
      double distance = eyePosition.distanceTo(closestPoint);
      Setting<Float> rangeSettingRef = this.nukerModule.rangeSetting;
      Float configuredRange = rangeSettingRef.getValue();
      return distance > configuredRange.floatValue();
   }

   public void enqueue(BlockPos blockPos) {
      if (!this.positions.contains(blockPos)) {
         this.positions.add(blockPos);
      }
   }

   public List<BlockPos> getNextPositions(boolean includeSecond) {
      if (this.positions.isEmpty()) {
         return Collections.emptyList();
      }

      if (this.positions.size() == 1) {
         return new ArrayList<>(List.of((BlockPos)this.positions.getFirst()));
      }

      return new ArrayList<>(includeSecond
         ? List.of((BlockPos)this.positions.getFirst(), (BlockPos)this.positions.get(1))
         : Collections.singletonList((BlockPos)this.positions.getFirst()));
   }

   public static void bindModules(ModuleRegistry modules) {
      speedMineModule = (SpeedMineModule)modules.getModule(
         SpeedMineModule.class
      );
   }

}
