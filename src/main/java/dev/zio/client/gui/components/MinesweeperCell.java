/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

class MinesweeperCell {
   MinesweeperCellContent content;
   MinesweeperCellState state = MinesweeperCellState.COVERED;

   public MinesweeperCell(MinesweeperCellContent content) {
      this.content = content;
   }

   public MinesweeperSprite getSprite() {
      return switch (this.state) {
         case COVERED -> MinesweeperSprite.UNCOVERED;
         case FLAGGED -> MinesweeperSprite.FLAG;
         case BLOWN -> MinesweeperSprite.BOMB_BLEW;
         case OPEN -> switch (this.content) {
            case BOMB -> MinesweeperSprite.BOMB;
            case NUMBER_0 -> MinesweeperSprite.EMPTY;
            case NUMBER_1 -> MinesweeperSprite.ONE;
            case NUMBER_2 -> MinesweeperSprite.TWO;
            case NUMBER_3 -> MinesweeperSprite.THREE;
            case NUMBER_4 -> MinesweeperSprite.FOUR;
            case NUMBER_5 -> MinesweeperSprite.FIVE;
            case NUMBER_6 -> MinesweeperSprite.SIX;
            case NUMBER_7 -> MinesweeperSprite.SEVEN;
            case NUMBER_8 -> MinesweeperSprite.EIGHT;
            default -> throw new MatchException(null, null);
         };
         default -> throw new MatchException(null, null);
      };
   }
}
