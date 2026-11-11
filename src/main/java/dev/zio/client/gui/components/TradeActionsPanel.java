/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.gui.panels.Panel;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.gui.screens.TradeManagerScreen;
import dev.zio.client.gui.screens.TradeEditorScreen;
import dev.zio.client.gui.components.ActionButtonComponent;
import dev.zio.client.utils.inventory.TradeDefinition;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.gui.components.ButtonComponent;
import dev.zio.client.render.TradePanelRequest;

public class TradeActionsPanel extends Panel implements ClientAccess {
   private final ElapsedTimer deleteConfirmationTimer = new ElapsedTimer();
   private final TradeManagerScreen managerScreen;
   private final TradeDefinition trade;

   public TradeActionsPanel(TradeManagerScreen screen, TradeDefinition selectedTrade) {
      super("Trade");
      this.setX(
         this.getX()
            + this.getWidth()
            + 4
      );
      this.managerScreen = screen;
      this.trade = selectedTrade;
      this.registerChild(
         new ButtonComponent(
            this,
            "Trade: " + selectedTrade.getName()
         )
      );
      this.registerChild(
         new ActionButtonComponent(this, "Edit", this::openEditor)
      );
      this.registerChild(
         new ActionButtonComponent(
            this,
            this::getDeleteButtonLabel,
            this::deleteIfConfirmed
         )
      );
      this.registerChild(
         new ActionButtonComponent(this, "Cancel", this::showCreatePanel)
      );
   }

   private void openEditor() {
      TradeDefinition registeredTrade = (TradeDefinition)ClientContext.tradeRegistry
         .findFirst(
            (java.util.function.Predicate<dev.zio.client.utils.inventory.TradeDefinition>)(candidate -> candidate.getName()
               .equals(this.trade.getName())
         ))
         .orElse(null);
      TradeEditorScreen editorScreen = new TradeEditorScreen(registeredTrade);
      editorScreen.setParentScreen(
         this.managerScreen
      );
      ClientAccess.zioMinecraft.setScreen(editorScreen);
   }

   private void deleteIfConfirmed() {
      if (!this.deleteConfirmationTimer
         .hasElapsedAndReset(1000L)) {
         ClientContext.tradeRegistry
            .reregisterByName(
               this.trade
                  .name()
            );
         this.showCreatePanel();
      }
   }

   private void showCreatePanel() {
      this.managerScreen
         .requestPanelChange(
            TradePanelRequest.forCreate()
         );
   }

   private String getDeleteButtonLabel() {
      return this.deleteConfirmationTimer
            .hasElapsed(1000L)
         ? "Delete"
         : "Are you sure?";
   }

}
