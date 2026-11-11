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
import net.minecraft.world.phys.BlockHitResult;

public record BedPlacementCandidate(
   BlockPos foot,
   BlockPos head,
   float targetDamage,
   float selfDamage,
   BlockHitResult hitResult
) {

}
