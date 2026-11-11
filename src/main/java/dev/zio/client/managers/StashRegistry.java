/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.managers;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.managers.Registry;
import dev.zio.client.modules.misc.StashFinderModule;
import dev.zio.client.modules.misc.StashEntry;
import dev.zio.client.settings.JsonConfigurable;
import dev.zio.client.settings.Setting;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Iterator;
import java.util.Collection;

public final class StashRegistry extends Registry<StashEntry> implements ClientAccess, JsonConfigurable {
   private static StashFinderModule stashFinderModule;

   public StashRegistry() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   static {
      stashFinderModule = ClientContext.getModule(StashFinderModule.class);
   }

   @Override
   public void fromJson(JsonElement element) {
      if (!element.isJsonObject()) {
         return;
      }
      JsonObject object = element.getAsJsonObject();
      if (!object.has("stashes")) {
         return;
      }
      object = element.getAsJsonObject();
      JsonArray stashes = object.getAsJsonArray("stashes");
      Iterator<JsonElement> iterator = stashes.iterator();
      while (iterator.hasNext()) {
         JsonElement stashElement = iterator.next();
         try {
            Collection<StashEntry> stashEntries = this.entries;
            Gson gson = ClientAccess.gson;
            stashEntries.add((StashEntry)gson.fromJson(stashElement, StashEntry.class));
         } catch (Exception ignored) {
         }
      }
   }

   @Override
   public JsonElement toJson() {
      Setting<Boolean> logSetting = stashFinderModule.logSetting;
      Boolean loggingEnabled = logSetting.getValue();
      if (!loggingEnabled) {
         return new JsonObject();
      }

      JsonArray stashes = new JsonArray();
      JsonObject object = new JsonObject();
      Iterator<StashEntry> iterator = ClientContext.stashRegistry.getRegistry().iterator();

      while (iterator.hasNext()) {
         StashEntry stash = iterator.next();
         Gson gson = ClientAccess.gson;
         stashes.add(gson.toJsonTree(stash));
      }

      object.add("stashes", stashes);
      return object;
   }

   @Override
   public String getConfigName() {
      return "stashfinder.json";
   }

}
