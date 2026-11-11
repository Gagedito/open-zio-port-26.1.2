/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.ClientPacketListenerHandleOpenSignEditorEvent;
import dev.zio.client.events.ScreenChangeEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.StringSetting;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;
import java.util.regex.Matcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

public class AutoSignModule extends Module {
   private static AntiCheatModule antiCheat;
   private static final DateFormat numericDateFormat;
   private static final DateFormat monthYearDateFormat;
   private final Setting<Boolean> fancyDateSetting;
   private final Setting<Boolean> onlyEmptySetting;
   private final Setting<Float> delaySetting;
   private final Setting<String> line1Setting;
   private final Setting<String> line2Setting;
   private final Setting<String> line3Setting;
   private final Setting<String> line4Setting;
   private final Queue<PendingSign> pendingSigns;

   private boolean hasInputMacro() {
      Setting<String> line1Snapshot = this.line1Setting;
      String combinedLines = line1Snapshot.getValue();
      Setting<String> line2Snapshot = this.line2Setting;
      String line2 = line2Snapshot.getValue();
      Setting<String> line3Snapshot = this.line3Setting;
      String line3 = line3Snapshot.getValue();
      Setting<String> line4Snapshot = this.line4Setting;
      String line4 = line4Snapshot.getValue();
      combinedLines = "\u0001\u0001\u0001\u0001"
         .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(combinedLines)))
         .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(line2)))
         .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(line3)))
         .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(line4)));
      return combinedLines.contains("<input>");
   }

   private long getDelayMillis() {
      Setting<Float> delaySnapshot = this.delaySetting;
      float delaySeconds = delaySnapshot.getValue();
      long delayMillis = (long)(delaySeconds * 1e+03F);
      AntiCheatModule antiCheatCapture = antiCheat;
      if (antiCheatCapture.is2b2tEnabled()) {
         return Math.max(400L, delayMillis);
      } else {
         return delayMillis;
      }
   }

   static {
      antiCheat = ClientContext.getModule(AntiCheatModule.class);
      numericDateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);
      monthYearDateFormat = new SimpleDateFormat("MMM yyyy", Locale.ENGLISH);
   }

   private void sendSignUpdate(BlockPos position, boolean frontText) {
      ServerboundSignUpdatePacket packet = new ServerboundSignUpdatePacket(
         position,
         frontText,
         formatLine(this.line1Setting),
         formatLine(this.line2Setting),
         formatLine(this.line3Setting),
         formatLine(this.line4Setting)
      );
      PacketSender.send(packet);
   }

   private String formatDate() {
      Calendar calendar = Calendar.getInstance();
      Setting<Boolean> fancyDateSnapshot = this.fancyDateSetting;
      Boolean fancyDate = fancyDateSnapshot.getValue();
      if (fancyDate) {
         int day = calendar.get((5));
         String ordinalDay = formatOrdinalDay(day);
         DateFormat fancyFormatterSnapshot = monthYearDateFormat;
         Date fancyTime = calendar.getTime();
         String monthYear = fancyFormatterSnapshot.format(fancyTime);
         return ordinalDay + " " + monthYear;
      } else {
         DateFormat numericFormatterSnapshot = numericDateFormat;
         Date numericTime = calendar.getTime();
         return numericFormatterSnapshot.format(numericTime);
      }
   }

   private boolean isSignEmpty(SignBlockEntity sign, boolean frontText) {
      SignText textSnapshot = sign.getText(frontText);
      for (Component message : textSnapshot.getMessages(false)) {
         String line = message.getString();
         if (!line.isBlank()) {
            return false;
         }
      }
      return true;
   }

   @EventHandler
   private void onScreenChanged(
      ScreenChangeEvent.AfterInit event
   ) {
      if (event.getNewScreen() instanceof SignEditScreen signScreen) {
         if (this.hasInputMacro()) {
            Setting<Boolean> onlyEmptySnapshot = this.onlyEmptySetting;
            Boolean onlyEmpty = onlyEmptySnapshot.getValue();
            if (onlyEmpty) {
               for (String message : signScreen.messages) {
                  if (!message.isBlank()) {
                     return;
                  }
               }
            }
            String[] messages = signScreen.messages;
            Setting<String> line1Snapshot = this.line1Setting;
            messages[0] = this.formatLine(line1Snapshot);
            messages = signScreen.messages;
            Setting<String> line2Snapshot = this.line2Setting;
            messages[1] = this.formatLine(line2Snapshot);
            messages = signScreen.messages;
            Setting<String> line3Snapshot = this.line3Setting;
            messages[(2)] = this.formatLine(line3Snapshot);
            messages = signScreen.messages;
            Setting<String> line4Snapshot = this.line4Setting;
            messages[(3)] = this.formatLine(line4Snapshot);
            boolean inputLineSelected = false;
            for (int lineIndex = 0; lineIndex < signScreen.messages.length; lineIndex++) {
               String inputCheckLine = signScreen.messages[lineIndex];
               if (inputCheckLine.contains("<input>")) {
                  messages = signScreen.messages;
                  String inputReplaceLine = signScreen.messages[lineIndex];
                  messages[lineIndex] = inputReplaceLine.replace("<input>", "");
                  if (!inputLineSelected) {
                     signScreen.line = lineIndex;
                     inputLineSelected = true;
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onOpenSignEditor(
      ClientPacketListenerHandleOpenSignEditorEvent event
   ) {
      if (!this.hasInputMacro()) {
         Setting<Boolean> onlyEmptySnapshot = this.onlyEmptySetting;
         Boolean onlyEmpty = onlyEmptySnapshot.getValue();
         if (onlyEmpty) {
            SignBlockEntity signSnapshot = event.getSign();
            boolean emptyFrontText = event.isFrontText();
            if (!this.isSignEmpty(signSnapshot, emptyFrontText)) {
               return;
            }
         }
         Setting<Float> delaySettingSnapshot = this.delaySetting;
         if (delaySettingSnapshot.isAtMinimum()) {
            BlockPos immediatePositionSnapshot = event.getPosition();
            boolean immediateFrontSnapshot = event.isFrontText();
            this.sendSignUpdate(immediatePositionSnapshot, immediateFrontSnapshot);
         } else {
            synchronized (this.pendingSigns) {
               Queue<PendingSign> queueSnapshot = this.pendingSigns;
               queueSnapshot.add(
                  new AutoSignModule.PendingSign(
                     pendingSignPosition(event), event.isFrontText(), pendingSignDueAtMillis()
                  )
               );
            }
         }
         event.cancel();
      }
   }

   private static BlockPos pendingSignPosition(ClientPacketListenerHandleOpenSignEditorEvent event) {
      return event.getPosition();
   }

   private long pendingSignDueAtMillis() {
      long queuedAtMillis = System.currentTimeMillis();
      return queuedAtMillis + this.getDelayMillis();
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerPreTickEvent event) {
      synchronized (this.pendingSigns) {
         if (!this.pendingSigns.isEmpty()) {
            long dueAtMillis = this.pendingSigns.peek()
               .dueAtMillis;
            if (dueAtMillis <= System.currentTimeMillis()) {
               AutoSignModule.PendingSign pendingSign = this.pendingSigns.poll();
               if (pendingSign != null) {
                  BlockPos positionCapture = pendingSign.position();
                  boolean frontCapture = pendingSign.frontText();
                  this.sendSignUpdate(positionCapture, frontCapture);
               }
            }
         }
      }
   }

   private static String formatOrdinalDay(int day) {
      if (day >= (11)) {
         if (day <= 13) {

            return (day) + "th";
         }
      }
      return switch (day % (10)) {
         case 1 -> day + "st";
         case 2 -> day + "nd";
         case 3 -> day + "rd";
         default -> day + "th";
      };
   }

   private String formatLine(Setting<String> lineSetting) {
      String line = lineSetting.getValue();
      line = line.replace("<date>", this.formatDate());
      Minecraft minecraft = ClientAccess.zioMinecraft;
      User user = minecraft.getUser();
      line = line.replace("<name>", user.getName());
      int configuredMaximumLength = 16;
      int lineLength = line.length();
      int endIndex = Math.min(configuredMaximumLength, lineLength);
      return line.substring(0, endIndex);
   }

   public static void bindModules(ModuleRegistry manager) {
      antiCheat = (AntiCheatModule)manager.getModule(
         AntiCheatModule.class
      );
   }

   public AutoSignModule() {
      super("AutoSign", ModuleCategory.MISC, new String[0]);
      this.fancyDateSetting = this.add(new BooleanSetting("FancyDate", true));
      this.onlyEmptySetting = this.add(new BooleanSetting("OnlyEmpty", false));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 0.0F, 0.0F, 5.0F).withUnit("s"));
      this.line1Setting = this.add(new StringSetting("Line1", "get Zio"));
      this.line2Setting = this.add(new StringSetting("Line2", "at"));
      this.line3Setting = this.add(new StringSetting("Line3", "github.com/"));
      this.line4Setting = this.add(new StringSetting("Line4", "spectrumdevs/zio"));
      this.pendingSigns = new ArrayDeque<>();
      String formattedDescription = "Puts stuff on your signs automatically\n%sMacros: <date> <name> <input>\n".formatted(ChatFormatting.YELLOW);
      this.setDescription(formattedDescription);
   }

   record PendingSign(
      BlockPos position,
      boolean frontText,
      long dueAtMillis
   ) {
   }
}
