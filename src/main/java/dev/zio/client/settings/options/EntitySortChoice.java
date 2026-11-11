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
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.settings.NamedEntry;
import java.util.Comparator;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public enum EntitySortChoice implements NamedEntry {
   DISTANCE("Distance", Comparator.comparingDouble((Entity entity) -> {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      float distance = entity.distanceTo(player);
      return distance;
   })),
   CROSSHAIR("Crosshair", Comparator.comparingDouble((Entity entity) -> {
      GameRenderer renderer = ClientAccess.zioMinecraft.gameRenderer;
      Camera camera = renderer.getMainCamera();
      float cameraYaw = camera.yaw();
      float targetYaw = RotationManager.getRotationsToEntity(entity)[0];
      return Mth.degreesDifferenceAbs(cameraYaw, targetYaw);
   })),
   HEALTH("Health", Comparator.comparingDouble(EntityUtils::getHealthWithAbsorption));

   private final String name;
   private final Comparator<Entity> comparator;

   EntitySortChoice(String name, Comparator<Entity> comparator) {
      this.name = name;
      this.comparator = comparator;
   }

   @Override
   public String getName() {
      return this.name;
   }

   public Comparator<Entity> getComparator() {
      return this.comparator;
   }

}
