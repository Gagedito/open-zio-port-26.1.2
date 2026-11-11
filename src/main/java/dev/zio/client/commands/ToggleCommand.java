/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.zio.client.modules.Module;

public final class ToggleCommand extends Command {

   public ToggleCommand() {
      super("toggle");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      ModuleArgumentType moduleType = new ModuleArgumentType();
      RequiredArgumentBuilder moduleArgument = Command.argument("module", moduleType);
      BoolArgumentType stateType = BoolArgumentType.bool();
      RequiredArgumentBuilder stateArgument = Command.argument("state", stateType);
      com.mojang.brigadier.Command<Object> setState = context -> {
         Class<Module> moduleClass = Module.class;
         Module module = context.getArgument("module", moduleClass);
         Boolean state = context.getArgument("state", Boolean.class);
         boolean toggled = state.booleanValue();
         module.setToggled(toggled);
         return 1;
      };
      ArgumentBuilder stateCommand = stateArgument.executes(setState);
      RequiredArgumentBuilder moduleWithState = (RequiredArgumentBuilder)moduleArgument.then(stateCommand);
      com.mojang.brigadier.Command<Object> toggle = context -> {
         Class<Module> moduleClass = Module.class;
         Module module = context.getArgument("module", moduleClass);
         module.toggle();
         return 1;
      };
      ArgumentBuilder moduleCommand = moduleWithState.executes(toggle);
      builder.then(moduleCommand);
   }
}
