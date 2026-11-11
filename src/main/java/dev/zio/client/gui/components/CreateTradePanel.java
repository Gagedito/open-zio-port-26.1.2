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
import dev.zio.client.gui.screens.TradeManagerScreen;
import dev.zio.client.gui.screens.TradeEditorScreen;
import dev.zio.client.gui.components.ActionButtonComponent;
import dev.zio.client.gui.CloseState;
import dev.zio.client.gui.components.StringSettingComponent;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.StringSetting;

public class CreateTradePanel extends Panel implements ClientAccess {
   private final StringSetting nameSetting = new StringSetting("Name", "");
   private final TradeManagerScreen managerScreen;

   public CreateTradePanel(TradeManagerScreen screen) {
      super("Trade");
      this.setX(
         this.getX()
            + this.getWidth()
            + 4
      );
      this.managerScreen = screen;
      this.registerChild(
         new StringSettingComponent(
            this,
            CloseState.ALWAYS_OPEN,
            this.nameSetting
         )
      );
      this.registerChild(
         new ActionButtonComponent(this, "Create", this::createTrade)
      );
   }

   private void createTrade() {
      if (!(this.nameSetting.getValue()).isBlank()) {
         TradeEditorScreen editorScreen = new TradeEditorScreen(
            this.nameSetting.getValue()
         );
         editorScreen.setParentScreen(
            this.managerScreen
         );
         ClientAccess.zioMinecraft.setScreen(editorScreen);
      }
   }

}
