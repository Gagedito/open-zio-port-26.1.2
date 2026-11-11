/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

public record CrystalPlacementResult(
   BlockPos base,
   LivingEntity target,
   double damage,
   CrystalPlacementKind kind
) {

   public CrystalPlacementResult selectBetterPlacement(CrystalPlacementResult other) {
      if (other == null) {
         return this;
      }
      int priority = this.kind.ordinal();
      if (priority < other.kind.ordinal()) {
         return this;
      }
      priority = this.kind.ordinal();
      if (priority > other.kind.ordinal()) {
         return other;
      }
      return other.damage > this.damage ? other : this;
   }

}
