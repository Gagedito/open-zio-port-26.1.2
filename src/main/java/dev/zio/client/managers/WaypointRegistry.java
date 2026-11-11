/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.managers;

import dev.zio.client.utils.world.Waypoint;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.settings.JsonConfigurable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

public final class WaypointRegistry extends AbstractRegistry<Waypoint> implements ClientAccess, JsonConfigurable {
   public WaypointRegistry() {
      super(new ArrayList<>());
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   @Override
   public boolean register(Object entry) {
      Waypoint waypoint = (Waypoint)entry;
      return this.registerWaypoint(waypoint);
   }

   @Override
   public boolean unregister(Object entry) {
      Waypoint waypoint = (Waypoint)entry;
      return this.unregisterWaypoint(waypoint);
   }

   public List<Waypoint> getVisible() {
      if (this.isWorldUnavailable()) {
         return Collections.emptyList();
      }

      ClientPacketListener serverCheckConnection = ClientAccess.zioMinecraft.player.connection;
      String serverAddress;
      if (serverCheckConnection.getServerData() == null) {
         serverAddress = "singleplayer";
      } else {
         ClientPacketListener addressConnection = ClientAccess.zioMinecraft.player.connection;
         serverAddress = addressConnection.getServerData().ip;
      }

      String currentServer = serverAddress;
      return this.entries.stream()
         .filter(candidate -> {
            if (candidate.getServer() != null) {
               String waypointServer = candidate.getServer();
               if (currentServer.equalsIgnoreCase(waypointServer)) {
                  return true;
               }
            }
            return false;
         })
         .sorted(Comparator.comparing((Waypoint waypoint) -> {
            LocalPlayer playerSnapshot = ClientAccess.zioMinecraft.player;
            double waypointX = waypoint.getX();
            double waypointY = waypoint.getY();
            double waypointZ = waypoint.getZ();
            return playerSnapshot.distanceToSqr(waypointX, waypointY, waypointZ);
         }))
         .toList();
   }

   @Override
   public void fromJson(JsonElement json) {
      if (!json.isJsonObject()) {
         return;
      }
      JsonObject configObject = json.getAsJsonObject();
      if (!configObject.has("waypoints")) {
         return;
      }
      configObject = json.getAsJsonObject();
      JsonArray waypointArray = configObject.getAsJsonArray("waypoints");
      Iterator<JsonElement> iterator = waypointArray.iterator();
      while (iterator.hasNext()) {
         JsonElement element = iterator.next();
         try {
            Collection destination = this.entries;
            Gson gson = ClientAccess.gson;
            Class<Waypoint> waypointClass = Waypoint.class;
            destination.add((Waypoint)gson.fromJson(element, waypointClass));
         } catch (Exception loadFailure) {
            loadFailure.printStackTrace();
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonArray waypointArray = new JsonArray();
      JsonObject configObject = new JsonObject();
      Iterator<Waypoint> iterator = this.entries.iterator();
      while (iterator.hasNext()) {
         Waypoint waypoint = iterator.next();
         Gson gson = ClientAccess.gson;
         JsonElement waypointJson = gson.toJsonTree(waypoint);
         waypointArray.add(waypointJson);
      }
      configObject.add("waypoints", waypointArray);
      return configObject;
   }

   @Override
   public String getConfigName() {
      return "waypoints.json";
   }

   public boolean registerWaypoint(Waypoint waypoint) {
      Iterator<Waypoint> iterator = this.getRegistry().iterator();
      while (iterator.hasNext()) {
         Waypoint existingWaypoint = iterator.next();
         String existingName = existingWaypoint.getName();
         String requestedName = waypoint.getName();
         if (existingName.equalsIgnoreCase(requestedName)) {
            String existingServer = existingWaypoint.getServer();
            String requestedServer = waypoint.getServer();
            if (existingServer.equalsIgnoreCase(requestedServer)) {
               return false;
            }
         }
      }
      return this.getRegistry().add(waypoint);
   }

   public boolean unregisterWaypoint(Waypoint waypoint) {
      return this.getRegistry().removeIf((java.util.function.Predicate<Waypoint>)candidate -> {
         if (candidate != null && waypoint != null) {
            String candidateName = candidate.getName();
            String requestedName = waypoint.getName();
            if (candidateName.equalsIgnoreCase(requestedName)) {
               String candidateServer = candidate.getServer();
               String requestedNameComparison = waypoint.getName();
               if (candidateServer.equalsIgnoreCase(requestedNameComparison)) {
                  return true;
               }
            }
         }
         return false;
      });
   }
}
