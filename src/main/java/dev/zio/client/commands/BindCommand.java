/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.InputPressEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Keybind;
import dev.zio.client.modules.Module;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;

public final class BindCommand extends Command {
   private Module pendingModule;

   public BindCommand() {
      super("bind");
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {

      LiteralArgumentBuilder branchBuilder = Command.literal("set");

      ArgumentType moduleTypeForSet = new ModuleArgumentType();
      RequiredArgumentBuilder moduleArgument = Command.argument("module", moduleTypeForSet);

      ArgumentType keyTypeForSet = new KeybindArgumentType();
      ArgumentBuilder keyBuilder = Command.argument("key", keyTypeForSet);
      com.mojang.brigadier.Command<Object> setAction = context -> {

         Module module = (Module)(context.getArgument("module", Module.class));

         Keybind keybind = (Keybind)(context.getArgument("key", Keybind.class));
         this.applyKeybind(module, keybind);
         return 1;
      };
      ArgumentBuilder setKeyAction = keyBuilder.executes(setAction);
      ArgumentBuilder captureKeyActionBuilder = (RequiredArgumentBuilder)moduleArgument.then(setKeyAction);
      com.mojang.brigadier.Command<Object> captureAction = context -> {

         this.pendingModule = (Module)(context.getArgument("module", Module.class));
         EventBus eventBus = ClientAccess.eventBus;
         eventBus.subscribe(this);

         MutableComponent prompt = Component.literal("Press a key");

         MessageSignature captureSignature = ChatUtils.createSignature(-1);
         ChatUtils.printChat(prompt, captureSignature);
         return 1;
      };
      ArgumentBuilder captureKeyAction = captureKeyActionBuilder.executes(captureAction);
      ArgumentBuilder setCommand = branchBuilder.then(captureKeyAction);
      LiteralArgumentBuilder configuredRoot = (LiteralArgumentBuilder)root.then(setCommand);

      branchBuilder = Command.literal("clear");

      ArgumentType moduleTypeForClear = new ModuleArgumentType();
      ArgumentBuilder clearModuleActionBuilder = Command.argument("module", moduleTypeForClear);
      com.mojang.brigadier.Command<Object> clearAction = context -> {

         Module module = (Module)(context.getArgument("module", Module.class));
         Function clearBinding = (Function<Keybind, Keybind>)(binding -> {
            return binding.withCode(-1);
         });

         module.modifyKeybind(clearBinding);

         MutableComponent prefix = Component.literal("Bind for ");
         String moduleName = module.getName();
         Component formattedClearedModuleName = this.formatText(moduleName);
         MutableComponent namedMessage = prefix.append(formattedClearedModuleName);

         MutableComponent message = namedMessage.append(" has been reset");

         MessageSignature clearedSignature = ChatUtils.createSignature(-1);
         ChatUtils.printChat(message, clearedSignature);
         return 1;
      };
      ArgumentBuilder clearModuleAction = clearModuleActionBuilder.executes(clearAction);
      branchBuilder = (LiteralArgumentBuilder)branchBuilder.then(clearModuleAction);

      ArgumentBuilder clearAllActionBuilder = Command.literal("all");
      com.mojang.brigadier.Command<Object> clearAllCallback = context -> {
         ModuleRegistry registry = ClientContext.moduleRegistry;
         Iterator modules = ((List)registry.getRegistry()).iterator();

         while (modules.hasNext()) {
            Module module = (Module)modules.next();
            Function clearBinding = (Function<Keybind, Keybind>)(binding -> {
               return binding.withCode(-1);
            });

            module.modifyKeybind(clearBinding);
         }

         MutableComponent message = Component.literal("Bind for all modules has been reset");

         MessageSignature allClearedSignature = ChatUtils.createSignature(-1);
         ChatUtils.printChat(message, allClearedSignature);
         return 1;
      };
      ArgumentBuilder clearAllAction = clearAllActionBuilder.executes(clearAllCallback);
      ArgumentBuilder clearCommand = branchBuilder.then(clearAllAction);
      configuredRoot = (LiteralArgumentBuilder)configuredRoot.then(clearCommand);

      ArgumentBuilder listActionBuilder = Command.literal("list");
      com.mojang.brigadier.Command<Object> listCallback = context -> {
         PresetCommand.reportPresetBinds();
         return 1;
      };
      ArgumentBuilder listAction = listActionBuilder.executes(listCallback);
      configuredRoot.then(listAction);
   }

   private void applyKeybind(Module module, Keybind keybind) {
      Function updateBinding = (Function<Keybind, Keybind>)(previousBinding -> {

         int keyCode = keybind.code();
         Keybind bindingWithCode = previousBinding.withCode(keyCode);

         boolean capturedMouse = keybind.mouse();

         return bindingWithCode.withMouse(capturedMouse);
      });

      module.modifyKeybind(updateBinding);

      MutableComponent prefix = Component.literal("Bind for ");
      String moduleName = module.getName();
      Component formattedModuleName = this.formatText(moduleName);
      MutableComponent namedMessage = prefix.append(formattedModuleName);

      MutableComponent messageWithSuffix = namedMessage.append(" has been set to ");

      int displayCode = keybind.code();

      boolean displayMouse = keybind.mouse();
      String capturedKeyName = KeyUtils.getKeyName(displayCode, displayMouse);

      Component formattedKeyName = this.formatText(capturedKeyName);
      MutableComponent message = messageWithSuffix.append(formattedKeyName);

      MessageSignature boundSignature = ChatUtils.createSignature(-1);
      ChatUtils.printChat(message, boundSignature);
   }

   @EventHandler
   private void onInputPress(InputPressEvent event) {

      Keybind keybind = new Keybind(
         readInputCode(event), readPendingMode(), event.isMouse()
      );
      Module moduleForBind = this.pendingModule;
      this.applyKeybind(moduleForBind, keybind);
      this.pendingModule = null;
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.unsubscribe(this);
   }

   private static int readInputCode(InputPressEvent event) {
      return event.getCode();
   }

   private Keybind.Mode readPendingMode() {
      Keybind currentKeybind = this.pendingModule.getKeybind();

      return currentKeybind.mode();
   }

}
