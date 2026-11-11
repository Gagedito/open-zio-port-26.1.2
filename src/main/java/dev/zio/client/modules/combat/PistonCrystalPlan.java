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
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;

record PistonCrystalPlan(
   BlockPos crystalPosition,
   BlockHitResult crystalHit,
   BlockPos pistonPosition,
   Direction pistonFacing,
   BlockHitResult pistonHit,
   BlockPos powerPosition,
   BlockHitResult powerHit
) {
}
