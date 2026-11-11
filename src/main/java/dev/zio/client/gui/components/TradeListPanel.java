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
import dev.zio.client.ClientContext;
import dev.zio.client.gui.screens.TradeManagerScreen;
import dev.zio.client.gui.components.ActionButtonComponent;
import dev.zio.client.utils.inventory.TradeDefinition;
import dev.zio.client.render.TradePanelRequest;

import java.util.List;

public class TradeListPanel extends Panel {
   private final TradeManagerScreen managerScreen;

   public TradeListPanel(TradeManagerScreen screen) {
      super("List");
      this.managerScreen = screen;
      this.refreshTrades();
   }

   @Override
   public void init() {
      super.init();
      this.refreshTrades();
   }

   public void refreshTrades() {
      this.getChildren()
         .clear();

      for (Object registeredEntry : (List)ClientContext.tradeRegistry
         .getRegistry()) {
         TradeDefinition registeredTrade = (TradeDefinition)registeredEntry;
         this.registerChild(
            new ActionButtonComponent(this, registeredTrade.name(), () -> this.selectTrade(registeredTrade))
         );
      }

      this.updateLayout();
   }

   private void selectTrade(TradeDefinition selectedTrade) {
      this.managerScreen
         .requestPanelChange(
            TradePanelRequest.forEdit(
               selectedTrade
            )
         );
   }

}
