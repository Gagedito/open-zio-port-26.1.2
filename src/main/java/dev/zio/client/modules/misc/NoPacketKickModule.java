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
import org.slf4j.Logger;

public class NoPacketKickModule extends Module {
   public final Setting<Boolean> messageSetting;

   public NoPacketKickModule() {
      super("NoPacketKick", "Cancels broken packets that may cause you getting kicked.", ModuleCategory.MISC, new String[0]);
      this.messageSetting = this.add(new BooleanSetting("Message", false));
      this.setDrawn(false);
   }

   public void logInterceptedException(Throwable exception) {
      if (this.messageSetting.getValue()) {
         try {
            Logger logger = dev.zio.client.ClientAccess.logger;
            String message = exception.getLocalizedMessage();
            logger.warn("Intercepted exception: {}", message);
         } catch (Throwable ignored) {
         }
      }
   }
}
