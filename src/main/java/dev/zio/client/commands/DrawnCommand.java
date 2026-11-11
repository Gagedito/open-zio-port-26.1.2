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
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Iterator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;

public final class DrawnCommand extends Command {

   public DrawnCommand() {
      super("drawn");
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {

      String moduleNodeName = "module";
      ArgumentType moduleNodeType = new ModuleArgumentType();
      ArgumentBuilder moduleToggleBuilder = Command.argument(moduleNodeName, moduleNodeType);
      com.mojang.brigadier.Command moduleToggleCallback = (com.mojang.brigadier.Command<Object>)context -> {

            Module module = (Module)context.getArgument("module", Module.class);
            boolean nextDrawn = !module.isDrawn();

            module.setDrawn(nextDrawn);
            String moduleName = module.getName();

            String confirmationText = "Drawing for " + String.valueOf(moduleName) + " has been set to " + String.valueOf(module.isDrawn()) + ".";
            MutableComponent confirmation = Component.literal(confirmationText);

            Component moduleConfirmationMessage = confirmation;
            MessageSignature moduleConfirmationSignature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(moduleConfirmationMessage, moduleConfirmationSignature);
            return 1;
         };
      ArgumentBuilder moduleToggleBranch = moduleToggleBuilder.executes(moduleToggleCallback);
      root.then(moduleToggleBranch);

      LiteralArgumentBuilder allLiteral = Command.literal("all");

      String allStateNodeName = "state";
      ArgumentType allStateNodeType = BoolArgumentType.bool();
      ArgumentBuilder allStateActionBuilder = Command.argument(allStateNodeName, allStateNodeType);
      com.mojang.brigadier.Command allStateActionCallback = (com.mojang.brigadier.Command<Object>)context -> {

            Boolean boxedState = (Boolean)context.getArgument("state", Boolean.class);
            boolean drawn = boxedState;
            ModuleRegistry registry = ClientContext.moduleRegistry;
            Iterator modules = ((List)registry.getRegistry()).iterator();

            while (modules.hasNext()) {
               Module module = (Module)modules.next();

               module.setDrawn(drawn);
            }

            String confirmationText = "Drawing for all modules has been set to " + drawn + ".";
            MutableComponent confirmation = Component.literal(confirmationText);

            Component allConfirmationMessage = confirmation;
            MessageSignature allConfirmationSignature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(allConfirmationMessage, allConfirmationSignature);
            return 1;
         };
      ArgumentBuilder allStateBranch = allStateActionBuilder.executes(allStateActionCallback);
      ArgumentBuilder allBranch = allLiteral.then(allStateBranch);
      root.then(allBranch);

      String categoryNodeName = "category";
      ArgumentType categoryNodeType = new EnumArgumentType(ModuleCategory.class, "Category");
      RequiredArgumentBuilder categoryArgument = Command.argument(categoryNodeName, categoryNodeType);

      String categoryStateNodeName = "state";
      ArgumentType categoryStateNodeType = BoolArgumentType.bool();
      ArgumentBuilder categoryStateActionBuilder = Command.argument(categoryStateNodeName, categoryStateNodeType);
      com.mojang.brigadier.Command categoryStateActionCallback = (com.mojang.brigadier.Command<Object>)context -> {

            ModuleCategory category = (ModuleCategory)context.getArgument("category", ModuleCategory.class);

            Boolean boxedState = (Boolean)context.getArgument("state", Boolean.class);
            boolean drawn = boxedState;
            ModuleRegistry registry = ClientContext.moduleRegistry;
            Iterator modules = ((List)registry.getRegistry()).iterator();

            while (modules.hasNext()) {
               Module module = (Module)modules.next();

               if (category == module.getCategory()) {

                  module.setDrawn(drawn);
               }
            }

            Object[] formatArguments = new Object[2];
            formatArguments[0] = category.getName();
            formatArguments[1] = drawn;
            String formattedText = ("Drawing for all %s modules has been set to %b.").formatted(formatArguments);
            MutableComponent confirmation = Component.literal(formattedText);

            Component categoryConfirmationMessage = confirmation;
            MessageSignature categoryConfirmationSignature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(categoryConfirmationMessage, categoryConfirmationSignature);
            return 1;
         };
      ArgumentBuilder categoryStateBranch = categoryStateActionBuilder.executes(categoryStateActionCallback);
      ArgumentBuilder categoryBranch = categoryArgument.then(categoryStateBranch);
      root.then(categoryBranch);
   }

}
