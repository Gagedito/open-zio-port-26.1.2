/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.ClientAccess;
import dev.zio.client.modules.hud.WelcomerModule;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.Setting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;

public enum WelcomerMode implements ClientAccess, NamedEntry {
   NAME("Name") {

      @Override
      public Component createText(WelcomerModule ignoredWelcomer) {
         Minecraft minecraft = ClientAccess.zioMinecraft;
         User user = minecraft.getUser();
         String username = user.getName();

         String greeting = "Hello " + username + " :')";
         return Component.literal(greeting);
      }

   },

   CUSTOM("Custom") {

      @Override
      public Component createText(WelcomerModule welcomer) {
         Setting<String> textSetting = welcomer.textSetting;
         String text = textSetting.getValue();

         Minecraft minecraft = ClientAccess.zioMinecraft;
         User user = minecraft.getUser();
         text = text.replace("{name}", user.getName());
         return Component.literal(text);
      }

   };

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   WelcomerMode(String displayName) {
      this.displayName = displayName;
   }

   public Component createText(WelcomerModule ignoredWelcomer) {
      return null;
   }
}
