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
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class AutoMineTargetSelector implements ClientAccess {
   private final AutoMineModule module;


   public AABB getTargetFootprintBounds(Player target) {
      AABB boundsSnapshot = target.getBoundingBox();
      double shrink = -BoundingBoxUtils.blockCornerInset;
      boundsSnapshot = boundsSnapshot.inflate(shrink, 0.0, shrink);
      double maximumY = target.getY();
      return boundsSnapshot.setMaxY(maximumY);
   }

   AutoMineTargetSelector(AutoMineModule module) {
      this.module = module;
   }


   public Player selectTarget() {
      Stream<AbstractClientPlayer> filteredCandidates = ClientAccess.zioMinecraft.level.players().stream()
         .filter(candidate -> candidate.isAlive()
            && candidate != ClientAccess.zioMinecraft.player
            && ClientAccess.zioMinecraft.player.distanceTo(candidate) <= this.module.getRange() + 2.0F
            && !ClientContext.socialRegistry.isFriendPlayer(candidate))
         .filter(candidate -> !this.module.ignoreNakedSetting.getValue() || PlayerStateUtils.hasArmor(candidate));
      Comparator<AbstractClientPlayer> yawOrder = Comparator.comparing((Function<AbstractClientPlayer, Float>)candidate -> {
            float yawSnapshot = ClientAccess.zioMinecraft.player.getYRot();

            float candidateYaw = RotationManager.getRotationsToEntity(candidate)[0];

            float yawDifference = Mth.degreesDifferenceAbs(yawSnapshot, candidateYaw);
            return yawDifference;
         });
      return filteredCandidates.min(yawOrder).orElse(null);
   }
}
