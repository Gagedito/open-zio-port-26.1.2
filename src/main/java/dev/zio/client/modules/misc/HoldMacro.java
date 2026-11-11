/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.modules.Keybind;
import dev.zio.client.settings.options.MacroType;

public class HoldMacro extends Macro {
   private boolean pressed;

   public HoldMacro(String name, Keybind keybind) {
      super(name, MacroType.HOLD, keybind);
   }

   @Override
   public void run() {
   }

   public void updatePressed(boolean pressed) {
      if (this.pressed == pressed) {
         return;
      }
      this.pressed = pressed;
      if (this.commands.isEmpty()) {
         return;
      }
      if (this.pressed) {
         String command = (String)this.commands.getFirst();
         Macro.executeCommands(command);
      } else {
         String command = (String)this.commands.getLast();
         Macro.executeCommands(command);
      }
   }

}
