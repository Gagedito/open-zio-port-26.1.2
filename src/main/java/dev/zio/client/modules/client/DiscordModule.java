/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.client;

import dev.zio.client.ClientAccess;
import dev.zio.client.ZioClient;
import dev.zio.client.events.MinecraftTickEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.entities.ActivityType;
import com.jagrosh.discordipc.entities.DiscordBuild;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.StatusDisplayType;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

public class DiscordModule extends Module {
   private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
   private static final String ICON_URL = "https://cdn.discordapp.com/app-icons/1055222411819241572/22387af3b091639d90c2335d52cf11b7.png";
   private final Setting<Boolean> serverSetting;
   private CompletableFuture<IPCClient> connectionFuture;
   private long startTimestamp;
   private final ElapsedTimer updateTimer;

   public DiscordModule() {
      super("Discord", "Displays Zio as your Discord activity.", ModuleCategory.CLIENT, new String[0]);
      this.serverSetting = this.add(new BooleanSetting("Server", false));
      this.startTimestamp = System.currentTimeMillis();
      this.updateTimer = new ElapsedTimer();

      this.setDrawn(false);
   }

   @Override
   public void onEnable() {
      this.closeClient();
      CompletableFuture<IPCClient> pending = CompletableFuture.supplyAsync(() -> {
         IPCClient client = new IPCClient(1055222411819241572L);
         try {
            client.connect(new DiscordBuild[0]);
            return client;
         } catch (Exception failure) {
            try { client.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw new java.util.concurrent.CompletionException(failure);
         }
      });
      this.connectionFuture = pending;
      pending.whenComplete((client, failure) -> {
         if (failure != null) {
            LOGGER.warn("Discord connection failed: {}", failure.getClass().getSimpleName());
            ClientAccess.zioMinecraft.execute(() -> {
               if (this.connectionFuture == pending && this.isToggled()) this.toggle();
            });
         }
      });
   }

   @Override
   public void onDisable() {
      this.closeClient();
   }

   @EventHandler
   private void onTick(MinecraftTickEvent event) {
      try {
         CompletableFuture<IPCClient> pendingConnection = this.connectionFuture;
         if (pendingConnection == null || !pendingConnection.isDone()) {
            return;
         }
         ElapsedTimer timerSnapshot = this.updateTimer;
         if (!timerSnapshot.hasElapsed(2500L)) {
            return;
         }
         timerSnapshot = this.updateTimer;
         timerSnapshot.reset();
         IPCClient ipcClient = this.connectionFuture.getNow(null);
         if (ipcClient == null) {
            return;
         }
         RichPresence.Builder presenceBuilder = new RichPresence.Builder();
         presenceBuilder.setLargeImage(ICON_URL, "Zio v" + ZioClient.VERSION, ICON_URL);

         long timestamp = this.startTimestamp;
         presenceBuilder.setStartTimestamp(timestamp);
         ActivityType activityType = ActivityType.Playing;
         presenceBuilder.setActivityType(activityType);
         StatusDisplayType displayType = StatusDisplayType.Name;
         presenceBuilder.setStatusDisplayType(displayType);
         Setting<Boolean> serverVisibilitySetting = this.serverSetting;
         Boolean showServer = serverVisibilitySetting.getValue();
         if (showServer) {
            String serverDetails = "Not playing";
            if (!this.isWorldUnavailable()) {
               ClientPacketListener connection = ClientAccess.zioMinecraft.player.connection;
               ServerData serverData = connection.getServerData();
               Minecraft minecraft = ClientAccess.zioMinecraft;
               String serverLocation;
               if (!minecraft.isLocalServer() && serverData != null) {
                  String serverAddress = serverData.ip;
                  serverLocation = "on " + serverAddress;
               } else {
                  serverLocation = "singleplayer";
               }
               serverDetails = "Playing " + serverLocation;
            }
            presenceBuilder.setDetails(serverDetails);
         }
         RichPresence presence = presenceBuilder.build();
         ipcClient.sendRichPresence(presence);
      } catch (Exception ignored) {
      }
   }

   public void closeClient() {
      CompletableFuture<IPCClient> pending = this.connectionFuture;
      this.connectionFuture = null;
      if (pending == null) return;
      pending.thenAccept(client -> {
         if (client == null) return;
         try {
            client.sendRichPresence(null);
         } catch (Exception failure) {
            LOGGER.debug("Could not clear Discord activity", failure);
         } finally {
            try { client.close(); } catch (Exception failure) { LOGGER.debug("Could not close Discord connection", failure); }
         }
      });
   }

}
