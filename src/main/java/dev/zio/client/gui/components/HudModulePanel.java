/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.gui.panels.ModuleCategoryPanel;
import dev.zio.client.gui.GuiElement;
import dev.zio.client.modules.ModuleCategory;

public class HudModulePanel extends ModuleCategoryPanel {

   @Override
   protected int getHeightLimit() {
      int height = 0;

      for (Object childElement : this.children) {
         GuiElement child = (GuiElement)childElement;
         height += child.getBaseHeight() + 1;
      }

      return height;
   }

   public HudModulePanel() {
      super(ModuleCategory.HUD);
   }

}
