/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.utils.collections.IntPair;

public enum SnakeDirection {
   UP(0, -1),
   RIGHT(1, 0),
   DOWN(0, 1),
   LEFT(-1, 0);

   private final IntPair offset;

   SnakeDirection(int xOffset, int yOffset) {
      this.offset = new IntPair(xOffset, yOffset);
   }

   public IntPair getOffset() {
      return this.offset;
   }

   public SnakeDirection opposite() {
      return switch (this) {
         case UP -> DOWN;
         case RIGHT -> LEFT;
         case DOWN -> UP;
         case LEFT -> RIGHT;
         default -> throw new MatchException(null, null);
      };
   }
}
