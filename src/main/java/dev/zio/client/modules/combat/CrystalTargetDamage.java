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
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.math.MathConstants;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.entity.LivingEntity;

public record CrystalTargetDamage(
   LivingEntity target,
   double damage,
   CrystalPlacementKind kind,
   CrystalLethality lethality
) {
   static CrystalTargetDamage calculateForBase(AutoCrystalModule module, BlockPos base, boolean hasCrystalBase, List<LivingEntity> targets) {
      CrystalTargetDamage bestTargetDamage = null;
      for (LivingEntity candidate : targets) {
         if (module.isInvalidTarget(candidate)) {
            continue;
         }

         double baseY = base.getY();
         double targetY = candidate.getY();
         if (baseY > targetY + 1.5D && !hasCrystalBase) {
            continue;
         }

         AABB targetBounds = module.getTargetBounds(candidate);

         double minimumDamage = CrystalDamagePolicy.PLACE.getMinimumDamage(candidate);

         double damage = module.calculateExplosionDamage(base, candidate, targetBounds, true);
         CrystalLethality lethality = CrystalLethality.classifyLethality(candidate, damage);
         Item crystalItem = Items.END_CRYSTAL;
         boolean holdingCrystal = candidate.isHolding(crystalItem);

         if (module.ignoreNakedsSetting.getValue() && !PlayerStateUtils.hasArmor(candidate) && !holdingCrystal) {
            continue;
         }

         Vec3 boundsCenterSnapshot = BoundingBoxUtils.getBottomCenter(targetBounds);
         Vec3 baseCenter = Vec3.atBottomCenterOf(base);
         double distance = boundsCenterSnapshot.distanceTo(baseCenter);

         Float crystalRange = module.crystalRangeSetting.getValue();
         if (distance > crystalRange.floatValue() && !lethality.lethalDamage
            && !module.obsidianDropNearTarget && !module.quiverThreatActive) {
            continue;
         }

         if (ClientContext.socialRegistry.isFriendEntity(candidate) && candidate != ClientAccess.zioMinecraft.player) {
            if (module.antiFriendKillSetting.getValue() && damage > EntityUtils.getHealthWithAbsorption(candidate)) {
               return null;
            }
            continue;
         }

         if ((minimumDamage > damage || damage < MathConstants.ONE_HALF) && !lethality.lethalDamage) {
            continue;
         }
         if (bestTargetDamage != null && !(damage > bestTargetDamage.damage())) {
            continue;
         }

         bestTargetDamage = new CrystalTargetDamage(candidate, damage, CrystalPlacementKind.NONE, lethality);
      }

      return bestTargetDamage;
   }
}
