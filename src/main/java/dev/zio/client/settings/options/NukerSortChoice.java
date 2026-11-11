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
import dev.zio.client.settings.NamedEntry;

import java.util.function.ToDoubleFunction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public enum NukerSortChoice implements NamedEntry {
   CLOSEST("Closest", blockPos -> {
      LocalPlayer playerForEye = ClientAccess.zioMinecraft.player;
      Vec3 eyeSnapshot = playerForEye.getEyePosition();
      Vec3 blockCenter = blockPos.getCenter();
      return eyeSnapshot.distanceToSqr(blockCenter);
   }),
   FURTHEST("Furthest", blockPos -> {
      LocalPlayer playerForEye = ClientAccess.zioMinecraft.player;
      Vec3 eyeSnapshot = playerForEye.getEyePosition();
      Vec3 blockCenter = blockPos.getCenter();
      return -eyeSnapshot.distanceToSqr(blockCenter);
   }),
   TOP("Top", blockPos -> -blockPos.getY()),
   BOTTOM("Bottom", blockPos -> blockPos.getY());

   private final String displayName;
   private final ToDoubleFunction<BlockPos> sortKeyFunction;

   public double getSortKey(BlockPos blockPos) {
      return this.sortKeyFunction.applyAsDouble(blockPos);
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   NukerSortChoice(String displayName, ToDoubleFunction<BlockPos> sortKeyFunction) {
      this.displayName = displayName;
      this.sortKeyFunction = sortKeyFunction;
   }
}
