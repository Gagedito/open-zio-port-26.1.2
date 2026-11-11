/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class PistonCrystalStageTracker {
   enum Kind { PISTON, CRYSTAL, POWER }

   private final EnumMap<Kind, Map<BlockPos, Long>> pending = new EnumMap<>(Map.of(
      Kind.PISTON, new HashMap<>(), Kind.CRYSTAL, new HashMap<>(), Kind.POWER, new HashMap<>()
   ));

   void update() {
      this.pending.values().forEach(positions -> positions.values().removeIf(expiry -> System.currentTimeMillis() > expiry));
   }

   void reset() {
      this.pending.values().forEach(Map::clear);
   }

   void markPiston(BlockPos position, Direction facing) {
      this.mark(Kind.PISTON, position.immutable());
      this.mark(Kind.PISTON, position.immutable().relative(facing));
   }

   void markCrystal(BlockPos position) {
      this.mark(Kind.CRYSTAL, position.immutable());
   }

   void markPower(BlockPos position) {
      this.mark(Kind.POWER, position.immutable());
   }

   boolean isPending(Kind kind, BlockPos position) {
      return this.pending.get(kind).getOrDefault(position, 0L) > System.currentTimeMillis();
   }

   private void mark(Kind kind, BlockPos position) {
      this.pending.get(kind).put(position, System.currentTimeMillis() + 300L);
   }
}
