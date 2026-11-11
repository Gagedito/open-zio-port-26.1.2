/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import dev.zio.client.ClientAccess;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.StringSetting;

import java.util.Iterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

public class NameProtectModule extends Module {
   public static boolean replacementBypassed;
   public final Setting<String> nameSetting;
   public final Setting<Boolean> othersSetting;
   public final Setting<Boolean> skinSetting;
   public final Setting<Boolean> slimSetting;

   static {
      replacementBypassed = false;
   }

   public String protectNames(String text) {
      if (replacementBypassed || text == null || text.isEmpty()) {
         return text;
      }
      String nameToReplace = ClientAccess.zioMinecraft.getUser().getName();

      String protectedName = this.getProtectedName();
      text = this.replaceWholeName(text, nameToReplace, protectedName);
      if ((this.othersSetting.getValue()) && ClientAccess.zioMinecraft.level != null) {
         ClientLevel level = ClientAccess.zioMinecraft.level;
         Iterator<? extends AbstractClientPlayer> players = level.players().iterator();

         while (players.hasNext()) {
            nameToReplace = players.next().getGameProfile().name();

            text = this.replaceWholeName(text, nameToReplace, "redacted");
         }
      }

      return text;
   }

   private String replaceWholeName(String text, String name, String replacement) {
      if (name == null || name.isEmpty()) {
         return text;
      }

      StringBuilder result = new StringBuilder(text.length());
      int cursor = 0;
      int nameLength = name.length();
      while (cursor < text.length()) {
         int matchStart = text.indexOf(name, cursor);
         if (matchStart == -1) {
            result.append(text, cursor, text.length());
            break;
         }

         int matchEnd = matchStart + nameLength;
         boolean leftBoundary = matchStart == 0 || !Character.isLetterOrDigit(text.charAt(matchStart - 1));
         boolean rightBoundary = matchEnd == text.length() || !Character.isLetterOrDigit(text.charAt(matchEnd));
         if (leftBoundary && rightBoundary) {
            result.append(text, cursor, matchStart);
            result.append(replacement);
            cursor = matchEnd;
         } else {
            result.append(text, cursor, matchStart + 1);
            cursor = matchStart + 1;
         }
      }
      return result.toString();
   }

   public String getProtectedName() {
      String name = this.nameSetting.getValue();
      return name.length() > 16 ? name.substring(0, 16) : name;
   }

   public NameProtectModule() {
      super("NameProtect", "Hides your nickname from the curious people.", ModuleCategory.PLAYER, new String[0]);
      this.nameSetting = this.add(new StringSetting("Name", "Me"));
      this.othersSetting = this.add(new BooleanSetting("Others", false));
      this.skinSetting = this.add(new BooleanSetting("Skin", true));
      this.slimSetting = this.add(new BooleanSetting("Slim", false).dependsOn(this.skinSetting));
   }

}
