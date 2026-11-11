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
import dev.zio.client.gui.screens.ClientScreen;
import dev.zio.client.gui.CloseState;
import dev.zio.client.gui.components.StringSettingComponent;
import dev.zio.client.settings.StringSetting;

import com.mojang.blaze3d.vertex.PoseStack;

public abstract class TradeSelectorPanel<T> extends Panel implements ClientAccess {
   protected final TradeItemsComponent tradeItems;
   protected final StringSettingComponent searchInput;
   private final boolean fromSide;

   public TradeSelectorPanel(TradeItemsComponent tradeItems, boolean fromSide) {
      super(fromSide ? "From" : "To");
      StringSetting searchSetting = new StringSetting("Search", "");
      this.fromSide = fromSide;
      this.tradeItems = tradeItems;
      this.searchInput = new StringSettingComponent(
         this, CloseState.ALWAYS_OPEN, searchSetting
      );
      this.children
         .add(this.searchInput);
      this.disableHeightLimit();
   }

   protected abstract void applySelection(T selection);

   protected abstract T getSelection();

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      super.render(poseStack, mouseX, mouseY);
      ClientScreen screen = (ClientScreen)ClientAccess.zioMinecraft.screen;
      if (!this.tradeItems
         .isSelectorOpen()) {
         ClientContext.taskScheduler
            .scheduleRotationTask(
               () -> screen.getPanels()
                  .remove(this),
               0
            );
      }
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      super.mouseClicked(mouseX, mouseY, button);
      T selection = this.getSelection();
      if (selection != null) {
         ClientScreen screen = (ClientScreen)ClientAccess.zioMinecraft.screen;
         ClientContext.taskScheduler
            .scheduleRotationTask(
               () -> {
                  screen.getPanels()
                     .remove(this);
                  this.applySelection(selection);
                  this.tradeItems
                     .closeSelector();
               },
               0
            );
      }
   }

   public boolean isFromSide() {
      return this.fromSide;
   }

}
