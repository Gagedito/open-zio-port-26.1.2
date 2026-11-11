/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;


import java.util.function.Predicate;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.StringSetting;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.chat.Component;

public class CustomDeathTextModule extends Module {
   final Setting<ModeChoice> modeSetting;
   private final Setting<String> messageSetting;
   private static final String[] randomMessages;

   static {
      String[] messages = new String[9];
      messages[0] = "YOU USELESS SHITSTAIN";
      messages[1] = "GAME OVER MOTHERFUCKER! YOU SUCK!";

      messages[2]
         = "HOW THE HELL ARE YOU SO BAD AT THIS GAME?";
      messages[3]
         = "HOW DO YOU FUCK UP SO BADLY JESUS FUCKING CHRIST?";
      messages[4]
         = "YOU DIED! CONGRATS FUCKFACE";
      messages[5]
         = "OH MY GOD YOU SUCK! AUTO-UNINSTALLING";

      messages[6]
         = "HOLY FUCKING SHIT SERIOUSLY";
      messages[7]
         = "YOU FUCKING RETARD!";

      messages[8] = "YOU DENSE FUCK";
      randomMessages = messages;
   }

   public Component getDeathText() {
      Setting<ModeChoice> modeSettingSnapshot = this.modeSetting;
      if (modeSettingSnapshot.getValue() == CustomDeathTextModule.ModeChoice.CUSTOM) {
         Setting<String> messageSettingSnapshot = this.messageSetting;
         String message = messageSettingSnapshot.getValue();
         String trimmedMessage = message.trim();
         return Component.nullToEmpty(trimmedMessage);
      } else {
         String[] messagesSnapshot = randomMessages;
         ThreadLocalRandom randomSnapshot = ThreadLocalRandom.current();
         int messageCount = randomMessages.length;
         String randomMessage = messagesSnapshot[randomSnapshot.nextInt(messageCount)];
         return Component.literal(randomMessage);
      }
   }

   public CustomDeathTextModule() {
      super("CustomDeathText", "Displays a custom message on your death screen.", ModuleCategory.MISC, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.CUSTOM));
      Predicate<Object> customMode = ignored -> this.modeSetting.getValue() == ModeChoice.CUSTOM;
      this.messageSetting = this.add(new StringSetting("Message", "Uh oh.", customMode));
      this.setDrawn(false);
   }

   public enum ModeChoice implements NamedEntry {
      CUSTOM("Custom"),
      RANDOM("Random");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
