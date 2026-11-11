/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.client.ColorsModule;
import dev.zio.client.modules.client.UIModule;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;

public final class GlobalCommand extends Command {

   public GlobalCommand() {
      super("global");
      String[] aliases = {"syncall"};
      setAliases(aliases);
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      LiteralArgumentBuilder colorCommand = Command.literal("color");
      ArgumentType colorType = BoolArgumentType.bool();
      RequiredArgumentBuilder colorValue = Command.argument("value", colorType);
      com.mojang.brigadier.Command<Object> colorAction = context -> {
         boolean synced = (Boolean)context.getArgument("value", Boolean.class);
         BiConsumer<Module, Setting<?>> syncColor = (module, setting) -> {
            if (!(module instanceof ColorsModule) && !(module instanceof UIModule)) {
               if (setting instanceof ColorSetting color) {
                  color.setSynced(synced);
               }
            }
         };
         forEachSetting(syncColor);
         ChatUtils.printChat(Component.literal("Color sync for all modules has been " + (synced ? "enabled." : "disabled.")), ChatUtils.DEFAULT_SIGNATURE);
         return 1;
      };
      ArgumentBuilder colorArguments = colorValue.executes(colorAction);
      ArgumentBuilder colorSubcommand = colorCommand.then(colorArguments);
      builder.then(colorSubcommand);

      LiteralArgumentBuilder lineWidthCommand = Command.literal("linewidth");
      ArgumentType lineWidthType = FloatArgumentType.floatArg(0.1F, 5.0F);
      RequiredArgumentBuilder lineWidthValue = Command.argument("value", lineWidthType);
      com.mojang.brigadier.Command<Object> lineWidthAction = context -> {
         Float argument = context.getArgument("value", Float.class);
         float lineWidth = argument;
         BiConsumer<Module, Setting<?>> setLineWidth = (module, setting) -> {
            if (setting.getValue() instanceof Float) {
               String name = setting.getName();
               if (name.equalsIgnoreCase("LineWidth")) {
                  NumberSetting<?> number = (NumberSetting<?>)setting;
                  Float value = lineWidth;
                  number.setNumber(value);
               }
            }
         };
         forEachSetting(setLineWidth);
         ChatUtils.printChat(Component.literal("LineWidth for all modules has been set to ").append(this.formatText(String.valueOf(lineWidth))).append("."), ChatUtils.DEFAULT_SIGNATURE);
         return 1;
      };
      ArgumentBuilder lineWidthArguments = lineWidthValue.executes(lineWidthAction);
      ArgumentBuilder lineWidthSubcommand = lineWidthCommand.then(lineWidthArguments);
      builder.then(lineWidthSubcommand);
   }

   private void forEachSetting(BiConsumer<Module, Setting<?>> action) {
      ModuleRegistry registry = ClientContext.moduleRegistry;
      List modules = (List)registry.getRegistry();
      Iterator moduleIterator = modules.iterator();
      while (moduleIterator.hasNext()) {
         Module module = (Module)moduleIterator.next();
         List settings = module.getSettings();
         Iterator settingIterator = settings.iterator();
         while (settingIterator.hasNext()) {
            Setting<?> setting = (Setting<?>)settingIterator.next();
            action.accept(module, setting);
         }
      }
   }
}
