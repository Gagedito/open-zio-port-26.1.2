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

public class QueuedMacro extends Macro {
   private int nextCommandIndex = 0;

   public QueuedMacro(String name, Keybind keybind) {
      super(name, MacroType.QUEUE, keybind);
   }

   @Override
   public void run() {
      if (this.commands.isEmpty()) {
         return;
      }
      this.nextCommandIndex = this.nextCommandIndex % this.commands.size();
      String commandGroup = (String)this.commands.get(this.nextCommandIndex);
      String[] commands = commandGroup.split(";");
      for (String command : commands) {
         Macro.executeCommands(command);
      }
      this.nextCommandIndex++;

   }

}
