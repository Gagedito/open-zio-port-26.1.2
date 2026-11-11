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
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Keybind;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.client.FontsModule;
import dev.zio.client.modules.client.UIModule;
import dev.zio.client.modules.render.BlurModule;
import dev.zio.client.settings.Setting;
import dev.zio.client.utils.chat.ChatUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public final class DefaultsCommand extends Command {

   public DefaultsCommand() {
      super("defaults");
   }

   @Override
   public void exec(LiteralArgumentBuilder commandBuilder) {
      com.mojang.brigadier.Command<Object> resetCommand = context -> {
         ConfirmScreen confirmationScreen = new ConfirmScreen((BooleanConsumer)(confirmed -> {
            if (confirmed) {
               ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
               Iterator moduleIterator = ((List)moduleRegistry.getRegistry()).iterator();

               while (moduleIterator.hasNext()) {
                  Module module = (Module)moduleIterator.next();

                  Iterator settingIterator = module.getSettings().iterator();

                  while (settingIterator.hasNext()) {
                     Setting<?> setting = (Setting<?>)settingIterator.next();
                     setting.reset();
                  }

                  if (module instanceof UIModule) {

                     Keybind defaultUiBinding = new Keybind(defaultUiKeyCode(), Keybind.Mode.TOGGLE, false);
                     module.setKeybind(defaultUiBinding);
                  } else {
                     Keybind defaultBinding = Keybind.UNBOUND;
                     module.setKeybind(defaultBinding);
                  }

                  if (!(module instanceof FontsModule) && !(module instanceof BlurModule)) {
                     module.setToggled(false);
                  }
               }
               this.scheduleRotationTask(() -> ChatUtils.printChat(Component.literal("All modules have been reset to defaults."), ChatUtils.DEFAULT_SIGNATURE));
            }

            Minecraft client = ClientAccess.zioMinecraft;
            client.setScreen(null);
         }), resetConfirmationTitle(), Component.nullToEmpty("Continue?"));
         Runnable showConfirmation = () -> {
            Minecraft client = ClientAccess.zioMinecraft;
            client.setScreen(confirmationScreen);
         };
         this.scheduleRotationTask(showConfirmation);
         return 1;
      };
      commandBuilder.executes(resetCommand);
   }

   private static Component resetConfirmationTitle() {
      return Component.nullToEmpty("Reset to defaults.");
   }

   private static int defaultUiKeyCode() {

      return 344;
   }
}
