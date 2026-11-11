/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.panels;

import dev.zio.client.gui.components.GridGameComponent;
import dev.zio.client.gui.components.SnakeGameComponent;
import dev.zio.client.gui.components.TetrisGameComponent;
import dev.zio.client.gui.components.MinesweeperGameComponent;
import dev.zio.client.gui.components.ButtonComponent;
import dev.zio.client.settings.options.GameType;
import dev.zio.client.settings.options.MinesweeperDifficulty;

import com.mojang.blaze3d.vertex.PoseStack;

public class GamePanel extends Panel {
   private GridGameComponent game = new TetrisGameComponent(this, 0);
   private GameType gameType = GameType.TETRIS;
   private boolean gameReplacementPending;

   public GamePanel() {
      super("Game");
      this.expansionAnimation
         .setValue(false);
      this.setExpanded(
         false
      );
      this.disableHeightLimit();
      this.registerChild(
         this.game
      );
      this.registerChild(
         new ButtonComponent(this, () -> this.gameType.getName()) {
            @Override
            public void mouseClicked(double mouseX, double mouseY, int button) {
               super.mouseClicked(mouseX, mouseY, button);
               if (this.isMouseOver(mouseX, mouseY) && button == 0) {
                  GamePanel.this.switchGame(
                     GameType.values()[(
                           GamePanel.this.gameType.ordinal()
                              + 1
                        )
                        % GameType.values().length]
                  );
               }
            }

         }
      );
   }

   public void switchGame(GameType gameType) {
      GridGameComponent game = switch (gameType) {
         case TETRIS -> new TetrisGameComponent(this, 0);
         case SNAKE -> new SnakeGameComponent(this, 0);
         case MINESWEEPER -> new MinesweeperGameComponent(this, MinesweeperDifficulty.EASY);
         default -> throw new MatchException(null, null);
      };
      this.switchGame(gameType, game);
   }

   public void switchGame(GameType gameType, GridGameComponent game) {
      this.gameType = gameType;
      this.game = game;
      this.gameReplacementPending = true;
   }

   @Override
   public void render(PoseStack matrices, double mouseX, double mouseY) {
      super.render(matrices, mouseX, mouseY);
      if (this.gameReplacementPending) {
         synchronized (this.children) {
            this.children
               .set(0, this.game);
         }

         this.gameReplacementPending = false;
      }
   }

   @Override
   public int getWidth() {
      return this.game instanceof MinesweeperGameComponent minesweeper
         ? minesweeper.getPreferredWidth()
         : 110;
   }

}
