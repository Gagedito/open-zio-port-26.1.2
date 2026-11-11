/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.modules.Keybind;
import dev.zio.client.modules.misc.Macro;
import dev.zio.client.utils.ElapsedTimer;
import java.util.Iterator;

public class DoubleTapMacro extends Macro {
   private final ElapsedTimer tapTimer = new ElapsedTimer();

   public DoubleTapMacro(String name, Keybind keybind) {
      super(name, MacroType.DOUBLE_TAP, keybind);
   }

   @Override
   public void run() {
      if (this.tapTimer.hasElapsed(300L)) {
         this.tapTimer.reset();
      } else {
         Iterator commands = this.commands.iterator();
         while (commands.hasNext()) {
            String command = (String)commands.next();
            Macro.executeCommands(command);
         }
         this.tapTimer.clear();
      }
   }

}
