/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;

public class AntiQuitModule extends Module {
   public final Setting<Boolean> disconnectSetting;
   public final Setting<Boolean> gameCloseSetting;

   public AntiQuitModule() {
      super("AntiQuit", "Prevents you from quitting the game/server accidentally.", ModuleCategory.MISC, new String[]{"antidisconnect"});
      this.disconnectSetting = this.add(new BooleanSetting("Disconnect", false));
      this.gameCloseSetting = this.add(new BooleanSetting("GameClose", false));
   }
}
