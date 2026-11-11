/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.access.ChatLineAccess;
import dev.zio.client.managers.ConfigurationManager;
import dev.zio.client.access.VisibleChatLinesAccess;
import dev.zio.client.managers.CommandRegistry;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Keybind;
import dev.zio.client.modules.Module;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

public final class PanicCommand extends Command {
   public static boolean unloaded;
   private static final String HIDDEN_PREFIX = "ㅤ";

   static {
      unloaded = false;
   }

   public PanicCommand() {
      super("panic");
   }

   private void unload() {
      ConfigurationManager configuration = ClientContext.configurationManager;
      configuration.save();
      unloaded = true;
      String previousPrefix = CommandRegistry.getPrefix();
      CommandRegistry.setPrefix(HIDDEN_PREFIX);
      ModuleRegistry modules = ClientContext.moduleRegistry;
      Iterator moduleIterator = ((List)modules.getRegistry()).iterator();

      while (moduleIterator.hasNext()) {
         Module module = (Module)moduleIterator.next();
         Function<Keybind, Keybind> unbind = binding -> {
            return binding.withCode(-1);
         };
         module.modifyKeybind(unbind);
         if (module.isToggled()) {
            module.toggle();
         }
      }

      Gui recentChatGui = ClientAccess.zioMinecraft.gui;
      ChatComponent recentChat = recentChatGui.getChat();
      recentChat.getRecentChat().removeIf((Predicate<String>)message -> message.startsWith(previousPrefix));
      Gui visibleChatGui = ClientAccess.zioMinecraft.gui;
      ((VisibleChatLinesAccess)visibleChatGui.getChat()).getVisible().removeIf((Predicate<Line>)line -> {
         ChatLineAccess access = (ChatLineAccess)(Object)line;
         if (access.getSignature() != null) {
            MessageSignature signature = access.getSignature();
            ByteBuffer signatureBytes = signature.asByteBuffer();
            if (signatureBytes.getInt() < 0) {
               return true;
            }
         }
         return false;
      });
      Gui remainingChatGui = ClientAccess.zioMinecraft.gui;
      ChatComponent remainingChat = remainingChatGui.getChat();
      remainingChat.getRecentChat().removeIf((Predicate<String>)message -> message.startsWith(previousPrefix));
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      com.mojang.brigadier.Command<Object> command = context -> {
         this.scheduleRotationTask(() -> {
            Minecraft minecraft = ClientAccess.zioMinecraft;
            ConfirmScreen confirmationScreen = new ConfirmScreen(
               (BooleanConsumer)(confirmed -> {
                  if (confirmed) {
                     this.unload();
                  }
                  Minecraft currentMinecraft = ClientAccess.zioMinecraft;
                  currentMinecraft.setScreen(null);
               }),
               Component.literal("Unload the client."),
               Component.literal("Continue?")
            );
            minecraft.setScreen(confirmationScreen);
         });
         return 1;
      };
      builder.executes(command);
   }
}
