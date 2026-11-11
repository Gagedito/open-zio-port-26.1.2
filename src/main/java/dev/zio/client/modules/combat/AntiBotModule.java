/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.ClientAccess;
import dev.zio.client.commands.FakeplayerCommand;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;

import java.util.Iterator;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;

public class AntiBotModule extends Module {
   private final Setting<Boolean> illegalCharsSetting;
   private final Setting<Boolean> illegalNamesSetting;

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      Player lastBot = null;

      Iterator<?> players = ClientAccess.zioMinecraft.level.players().iterator();

      while (players.hasNext()) {
         Player candidate = (Player)players.next();

         if (this.isBot(candidate)) {
            lastBot = candidate;
         }
      }

      if (lastBot != null) {
         RemovalReason removalReason = RemovalReason.KILLED;

         lastBot.setRemoved(removalReason);
      }
   }

   private boolean isBot(Player candidate) {
      if (ClientAccess.zioMinecraft.player == candidate
         || candidate instanceof FakeplayerCommand.SimulatedPlayer) {
         return false;
      }

      String name = candidate.getGameProfile().name();
      if (name == null) {
         return true;
      }

      int nameLength = name.length();

      if (this.illegalNamesSetting.getValue()) {
         if (nameLength < 3) {
            return true;
         }

         if (nameLength > 16) {
            return true;
         }
      }

      if (this.illegalCharsSetting.getValue()) {
         for (int characterIndex = 0; characterIndex < nameLength; characterIndex++) {
            char character = name.charAt(characterIndex);
            char lowercaseCharacter = Character.toLowerCase(character);
            if (lowercaseCharacter >= '0') {
               if (lowercaseCharacter <= '9') {
                  continue;
               }
            }

            if (lowercaseCharacter != '_') {
               if (lowercaseCharacter < 'a') {
                  return true;
               }

               if (lowercaseCharacter > 'z') {
                  return true;
               }
            }
         }
      }

      ClientPacketListener connectionSnapshot = ClientAccess.zioMinecraft.player.connection;

      UUID profileId = candidate.getGameProfile().id();

      PlayerInfo playerInfo = connectionSnapshot.getPlayerInfo(profileId);
      return playerInfo == null || playerInfo.getProfile() == null && candidate.hasCustomName();
   }

   public AntiBotModule() {
      super("AntiBot", "Removes the bots that will be spawned by some anti cheats.", ModuleCategory.COMBAT, new String[0]);
      this.illegalCharsSetting = this.add(new BooleanSetting("IllegalChars", false));
      this.illegalNamesSetting = this.add(new BooleanSetting("IllegalNames", false));
   }
}
