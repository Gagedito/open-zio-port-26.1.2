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
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.managers.TaskScheduler;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.StringSetting;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;

public class AutoRespawnModule extends Module {
   private final ElapsedTimer respawnTimer = new ElapsedTimer();
   private final Setting<Boolean> saySetting;
   private final Setting<String> messageSetting;

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (ClientAccess.zioMinecraft.screen instanceof DeathScreen) {
         ElapsedTimer timer = this.respawnTimer;
         if (timer.hasElapsed(100L)) {
            LocalPlayer player = ClientAccess.zioMinecraft.player;
            player.respawn();
            this.respawnTimer.reset();
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundRespawnPacket) {
         Setting<Boolean> saySnapshot = this.saySetting;
         Boolean say = saySnapshot.getValue();
         if (say) {
            TaskScheduler scheduler = ClientContext.taskScheduler;
            Runnable sendMessage = () -> {
               Setting<String> messageSnapshot = this.messageSetting;
               String message = messageSnapshot.getValue();
               ChatUtils.sendChatOrCommand(message);
            };
            scheduler.scheduleRotationTask(sendMessage, 2);
         }
      }

      if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket) {
         this.respawnTimer.reset();
      }
   }

   public AutoRespawnModule() {
      super("AutoRespawn", "Respawns automatically.", ModuleCategory.MISC, new String[0]);
      Boolean sayDefault = false;
      BooleanSetting say = new BooleanSetting("Say", sayDefault);
      say.markVisibilityDependency();
      this.saySetting = this.add(say);
      StringSetting message = new StringSetting("Message", "/kit cattyn");
      Setting<?>[] dependencies = new Setting<?>[1];
      dependencies[0] = this.saySetting;
      this.messageSetting = this.add(message.dependsOn(dependencies));

      this.setDrawn(false);
   }

}
