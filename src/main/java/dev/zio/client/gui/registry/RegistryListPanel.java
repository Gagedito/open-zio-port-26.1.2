/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.registry;

import dev.zio.client.ClientAccess;
import dev.zio.client.gui.panels.Panel;

class RegistryListPanel extends Panel {

   public RegistryListPanel(String title) {
      super(title);
   }

   @Override
   protected int getHeightLimit() {
      return ClientAccess.zioMinecraft.getWindow().getGuiScaledHeight() / 2 + 10;
   }

}
