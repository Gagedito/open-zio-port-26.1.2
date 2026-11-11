/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.managers.ConfigurationManager;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.StringSetting;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.apache.commons.io.FileUtils;

public class SpammerModule extends Module {
   private final Setting<String> pathSetting;
   private final Setting<Float> delaySetting;
   private final Setting<Boolean> refreshSetting;
   private final Setting<Boolean> smartDelaySetting;
   private final Setting<Boolean> randomSetting;
   private final Setting<Boolean> autoDisableSetting;
   private final Random messageRandom;
   private final List<String> messages;
   private final ElapsedTimer sendTimer;
   private int nextMessageIndex;

   private void loadMessages() {
      try {
         Path resolvedPath = ConfigurationManager.SPAMMER_DIRECTORY;
         Setting<String> pathSnapshot = this.pathSetting;
         String selectedPath = pathSnapshot.getValue();
         resolvedPath = resolvedPath.resolve(selectedPath);
         String[] extensions = new String[1];

         extensions[0] = ".txt";
         Path filePath = dev.zio.client.utils.FileUtils.findExistingWithSuffixes(resolvedPath, extensions);
         File existenceCheckFile = filePath.toFile();
         if (!existenceCheckFile.exists()) {

            throw new RuntimeException("Invalid spammer file path");
         }

         this.nextMessageIndex = 0;
         this.messages.clear();
         List<String> destinationMessages = this.messages;
         File readFile = filePath.toFile();
         Charset charset = StandardCharsets.UTF_8;
         destinationMessages.addAll(FileUtils.readLines(readFile, charset));
      } catch (Exception exception) {
         if (!this.isWorldUnavailable()) {
            Setting<String> failedPathSnapshot = this.pathSetting;

            String errorMessage = "Failed to open spammer file " + failedPathSnapshot.getValue();
            MutableComponent literalComponent = Component.literal(errorMessage);
            java.util.function.UnaryOperator<Style> errorStyle = style -> {
               ChatFormatting errorColor = ChatFormatting.RED;
               return style.withColor(errorColor);
            };
            MutableComponent errorComponent = literalComponent.withStyle(errorStyle);
            String exceptionText = exception.toString();
            int exceptionHash = exceptionText.hashCode();

            MessageSignature errorSignature = ChatUtils.createSignature(exceptionHash);
            ChatUtils.MessagePriority errorPriority = ChatUtils.MessagePriority.LOW;
            ChatUtils.printChat(errorComponent, errorSignature, errorPriority);
         }
      }
   }

   @Override
   public void onEnable() {
      this.loadMessages();
   }

   @EventHandler
   private void onConnectionSendPacketEvent(
      PacketEvent.Send event
   ) {
      if (event.getPacket() instanceof ServerboundChatPacket) {
         Setting<Boolean> smartDelaySnapshot = this.smartDelaySetting;
         Boolean smartDelayEnabled = smartDelaySnapshot.getValue();
         if (smartDelayEnabled) {
            this.sendTimer.reset();
         }
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      ElapsedTimer timerCapture = this.sendTimer;
      Setting<Float> delaySnapshot = this.delaySetting;
      Float delayValue = delaySnapshot.getValue();
      double delaySeconds = delayValue.floatValue();
      TimeUnit delayUnit = TimeUnit.SECONDS;
      if (timerCapture.hasElapsed(delaySeconds, delayUnit)
         && !this.messages.isEmpty()) {
         List<String> messagesSnapshot = this.messages;
         Setting<Boolean> randomSnapshot = this.randomSetting;
         Boolean randomEnabled = randomSnapshot.getValue();
         int selectedIndex;
         if (randomEnabled && this.messages.size() > 1) {
            Random random = this.messageRandom;
            int randomBound = this.messages.size() - 1;
            selectedIndex = random.nextInt(randomBound);
         } else {
            selectedIndex = this.nextMessageIndex;
         }

         String message = messagesSnapshot.get(selectedIndex);
         ChatUtils.sendChatOrCommand(message);

         this.nextMessageIndex = (
               this.nextMessageIndex + 1
            )
            % this.messages.size();
         timerCapture = this.sendTimer;

         timerCapture.reset();
         Setting<Boolean> autoDisableSnapshot = this.autoDisableSetting;
         Boolean autoDisableEnabled = autoDisableSnapshot.getValue();
         if (autoDisableEnabled) {
            this.toggle();
         }
      }
   }

   public SpammerModule() {
      super("Spammer", "Spams messages from a selected text file.", ModuleCategory.MISC, new String[0]);
      this.pathSetting = this.add(new StringSetting("Path", "spammer.txt"));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 5.0F, 0.0F, 300.0F).withUnit("s"));
      this.refreshSetting = this.add(new BooleanSetting("Refresh", false));
      this.smartDelaySetting = this.add(new BooleanSetting("SmartDelay", true));
      this.randomSetting = this.add(new BooleanSetting("Random", false));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false));
      this.messageRandom = new Random();
      this.messages = new ArrayList<>();
      this.sendTimer = new ElapsedTimer();
      this.nextMessageIndex = 0;
      this.pathSetting.setChangeCallback(this::loadMessages);
      this.refreshSetting.setChangeCallback(() -> {
         Boolean refreshRequested = this.refreshSetting.getValue();
         if (refreshRequested) {
            this.refreshSetting.setValue(false);
            this.loadMessages();
         }
      });
   }

}
