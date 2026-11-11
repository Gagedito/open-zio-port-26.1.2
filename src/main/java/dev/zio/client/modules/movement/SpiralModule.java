/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;

import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.math.Vec3Utils;


import dev.zio.client.ClientAccess;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class SpiralModule extends Module {
   private final Setting<Integer> sizeSetting;
   private final List<Vec3> waypoints;
   private int waypointIndex;

   public SpiralModule() {
      super("Spiral", ModuleCategory.MOVEMENT);
      this.sizeSetting = this.add(new NumberSetting<>(new String("Size"), 5, 1, 20));
      this.waypoints = new ArrayList<>(5000);
   }

   @Override
   public void onEnable() {
      this.waypointIndex = 0;
      this.waypoints.clear();
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      Vec3 position = player.position();
      float yaw = 0.0F;
      int index = 0;

      while (true) {
         if (index >= 5000 * 4) {
            return;
         }

         this.waypoints.add(position);
         Vec3 direction = Vec3.directionFromRotation(0.0F, yaw);
         Setting<Integer> setting = this.sizeSetting;
         Integer size = setting.getValue();
         int radius = index * size;
         double distance = radius * 16;
         Vec3 offset = direction.scale(distance);
         position = position.add(offset);
         float nextYaw = yaw + 90.0F;
         yaw = Mth.wrapDegrees(nextYaw);
         index++;
      }
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      int index = Math.max(this.waypointIndex, 1);
      while (true) {
         int startIndex = Math.max(this.waypointIndex, 1);
         if (index >= startIndex + 2) {
            return;
         }

         Vec3 previousPoint = this.waypoints.get(index - 1);
         Vec3 point = this.waypoints.get(index);
         index++;
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      int index = this.waypointIndex;
      Vec3 waypoint = this.waypoints.get(index);
      float yaw = RotationManager.getRotationsToPosition(waypoint)[0];
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      player.setYRot(yaw);
      Vec3 position = ClientAccess.zioMinecraft.player.position();
      float distance = Vec3Utils.horizontalDistance(waypoint, position);
      if (distance < 3.0F) {
         this.waypointIndex++;
      }
   }
}
