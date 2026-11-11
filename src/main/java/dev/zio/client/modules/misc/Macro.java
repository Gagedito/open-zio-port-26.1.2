/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.NamedFeature;
import dev.zio.client.managers.CommandRegistry;
import dev.zio.client.modules.Keybind;
import dev.zio.client.settings.options.MacroType;
import java.util.ArrayList;
import java.util.List;

public abstract class Macro extends NamedFeature {
   protected final List commands = new ArrayList();
   private final MacroType type;
   private final Keybind keybind;
   private boolean toggled;

   public Macro(String name, MacroType type, Keybind keybind) {
      super(name);
      this.type = type;
      this.keybind = keybind;
      this.toggled = true;
   }

   public abstract void run();

   public boolean isToggled() {
      return this.toggled;
   }

   public Keybind getKeybind() {
      return this.keybind;
   }

   public static void executeCommands(String commandGroup) {
      for (String command : commandGroup.split(";")) {
         CommandRegistry.executeCommand(command.trim());
      }
   }

   public List getCommands() {
      return this.commands;
   }

   public void setToggled(boolean toggled) {
      this.toggled = toggled;
   }

   public MacroType getType() {
      return this.type;
   }
}
