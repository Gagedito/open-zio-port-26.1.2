/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.ClientContext;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.HudWidgetModule;
import dev.zio.client.modules.Module;
import dev.zio.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class AlloffCommand extends Command {
   private final Set previouslyEnabledModules = new HashSet();

   public AlloffCommand() {
      super("alloff");
   }

   @Override
   public void exec(LiteralArgumentBuilder commandBuilder) {
      com.mojang.brigadier.Command<Object> disableCommand = context -> {
         this.previouslyEnabledModules.clear();
         ModuleRegistry registry = ClientContext.moduleRegistry;
         Iterator modules = ((List)registry.getRegistry()).iterator();

         while (modules.hasNext()) {
            Module module = (Module)modules.next();
            if (!(module instanceof HudWidgetModule) && module.isToggled()) {
               module.toggle();
               this.previouslyEnabledModules.add(module);
            }
         }

         this.scheduleRotationTask(() -> ChatUtils.printChat(
            Component.literal("Disabled ").append(this.formatText(String.valueOf(this.previouslyEnabledModules.size()))).append(" modules."),
            ChatUtils.DEFAULT_SIGNATURE
         ));
         return 1;
      };
      commandBuilder.executes(disableCommand);

      String[] aliasArray = {"restore", "backup"};
      ArgumentBuilder restoreBuilder = Command.aliasedLiteral(aliasArray);
      com.mojang.brigadier.Command<Object> restoreCommand = context -> {
         ModuleRegistry registry = ClientContext.moduleRegistry;
         Iterator modules = ((List)registry.getRegistry()).iterator();

         while (modules.hasNext()) {
            Module module = (Module)modules.next();
            if (!(module instanceof HudWidgetModule)) {
               boolean wasEnabled = this.previouslyEnabledModules.contains(module);
               module.setToggled(wasEnabled);
            }
         }

         this.scheduleRotationTask(() -> ChatUtils.printChat(
            Component.literal("Restored ").append(this.formatText(String.valueOf(this.previouslyEnabledModules.size()))).append(" modules."),
            ChatUtils.DEFAULT_SIGNATURE
         ));
         return 1;
      };
      ArgumentBuilder restoreBranch = restoreBuilder.executes(restoreCommand);
      commandBuilder.then(restoreBranch);
   }

}
