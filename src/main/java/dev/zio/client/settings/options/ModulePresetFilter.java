/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.ClientContext;
import dev.zio.client.config.LegacyBranding;
import dev.zio.client.managers.AbstractRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.render.LogoutSpotsModule;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Predicate;

public final class ModulePresetFilter {
   public static final ModulePresetFilter INSTANCE = new ModulePresetFilter();

   private ModulePresetFilter() {
   }

   public void retainScalarSettings(JsonObject modulesJson) {
      Iterator moduleValues = modulesJson.asMap().values().iterator();

      while (moduleValues.hasNext()) {
         JsonElement moduleElement = (JsonElement)moduleValues.next();
         if (moduleElement instanceof JsonObject moduleJson) {
            moduleJson.remove("key");

            if (moduleJson.has("settings")) {
               JsonElement settingsElement = moduleJson.get("settings");
               if (settingsElement.isJsonObject()) {
                  ArrayList nestedSettingKeys = new ArrayList();
                  JsonObject settingsJson = settingsElement.getAsJsonObject();
                  Iterator settingEntries = settingsJson.asMap().entrySet().iterator();

                  while (settingEntries.hasNext()) {
                     Entry settingEntry = (Entry)settingEntries.next();
                     JsonElement settingValue = (JsonElement)settingEntry.getValue();
                     if (settingValue.isJsonObject()) {
                        nestedSettingKeys.add((String)settingEntry.getKey());
                     }
                  }

                  settingEntries = nestedSettingKeys.iterator();

                  while (settingEntries.hasNext()) {
                     String removalKey = (String)settingEntries.next();
                     JsonObject removalSettingsJson = settingsElement.getAsJsonObject();
                     removalSettingsJson.remove(removalKey);
                  }
               }
            }
         }
      }
   }

   public void retainModuleSettings(JsonObject modulesJson, Predicate includedModules) {
      Iterator moduleNames = modulesJson.asMap().keySet().iterator();

      while (moduleNames.hasNext()) {
         String moduleName = (String)moduleNames.next();

         if (modulesJson.get(moduleName) instanceof JsonObject moduleJson) {
            AbstractRegistry modules = ClientContext.moduleRegistry;
            Predicate<Module> modulePredicate = module -> {
               return module.getConfigName().equals(LegacyBranding.canonicalModuleName(moduleName));
            };

            Optional matchingModule = modules.findFirst(modulePredicate);

            if (!matchingModule.isEmpty()) {
               boolean includeModule = includedModules.test((Module)matchingModule.get());
               if (!includeModule || matchingModule.orElse(null) instanceof LogoutSpotsModule) {
                  moduleJson.remove("enabled");
               }

               moduleJson.remove("key");

               if (moduleJson.has("settings")) {
                  JsonElement settingsElement = moduleJson.get("settings");
                  if (settingsElement.isJsonObject()) {
                     ArrayList filteredSettingKeys = new ArrayList();
                     JsonObject settingsJson = settingsElement.getAsJsonObject();
                     Iterator settingEntries = settingsJson.asMap().entrySet().iterator();

                     while (settingEntries.hasNext()) {
                        Entry settingEntry = (Entry)settingEntries.next();
                        if (includeModule) {
                           break;
                        }

                        String settingName = (String)settingEntry.getKey();
                        JsonElement settingValue = (JsonElement)settingEntry.getValue();

                        if (!this.isVisualSetting(settingName, settingValue)) {
                           filteredSettingKeys.add((String)settingEntry.getKey());
                        }
                     }

                     settingEntries = filteredSettingKeys.iterator();

                     while (settingEntries.hasNext()) {
                        String removalKey = (String)settingEntries.next();
                        JsonObject removalSettingsJson = settingsElement.getAsJsonObject();
                        removalSettingsJson.remove(removalKey);
                     }
                  }
               }
            }
         }
      }
   }

   public void retainKeyBindings(JsonObject modulesJson) {
      Iterator moduleValues = modulesJson.asMap().values().iterator();

      while (moduleValues.hasNext()) {
         JsonElement moduleElement = (JsonElement)moduleValues.next();
         if (moduleElement instanceof JsonObject moduleJson) {
            moduleJson.remove("enabled");

            moduleJson.remove("settings");
         }
      }
   }

   public boolean isVisualSetting(String settingName, JsonElement settingValue) {
      return settingName.equalsIgnoreCase("render") || settingName.equalsIgnoreCase("linewidth") || settingValue.isJsonObject();
   }
}
