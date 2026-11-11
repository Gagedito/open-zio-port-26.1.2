/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.screens;

import dev.zio.client.gui.components.TradeActionsPanel;
import dev.zio.client.gui.components.TradeListPanel;
import dev.zio.client.gui.components.CreateTradePanel;
import dev.zio.client.gui.panels.Panel;
import dev.zio.client.render.TradePanelMode;
import dev.zio.client.render.TradePanelRequest;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class TradeManagerScreen extends ClientScreen {
   private final TradeListPanel tradeListPanel = new TradeListPanel(this);
   private Panel actionPanel = new CreateTradePanel(this);
   private TradePanelRequest pendingPanelRequest;

   public TradeManagerScreen() {
      this.tradeListPanel
         .updateLayout();
      this.getPanels()
         .add(this.tradeListPanel);
      this.getPanels()
         .add(this.actionPanel);
   }

   @Override
   public void renderContents(
      GuiGraphicsExtractor graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick
   ) {
      if (this.pendingPanelRequest != null) {
         this.tradeListPanel
            .refreshTrades();
         this.getPanels()
            .remove(this.actionPanel);
         if (this.pendingPanelRequest
               .mode()
            == TradePanelMode.CREATE) {
            this.actionPanel = new CreateTradePanel(this);
         } else {
            this.actionPanel = new TradeActionsPanel(
               this,
               this.pendingPanelRequest
                  .trade()
            );
         }

         this.getPanels()
            .add(this.actionPanel);
         this.pendingPanelRequest = null;
      }
   }

   public void requestPanelChange(TradePanelRequest request) {
      this.pendingPanelRequest = request;
   }

}
