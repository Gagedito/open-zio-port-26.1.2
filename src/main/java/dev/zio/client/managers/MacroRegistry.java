/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.managers;

import dev.zio.client.config.JsonUtils;
import dev.zio.client.modules.misc.HoldMacro;
import dev.zio.client.modules.misc.Macro;

import dev.zio.client.utils.KeyUtils;
import dev.zio.client.ClientAccess;
import dev.zio.client.events.InputPressEvent;
import dev.zio.client.events.MacroTriggerEvent;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.managers.Registry;
import dev.zio.client.modules.Keybind;
import dev.zio.client.settings.JsonConfigurable;
import dev.zio.client.settings.options.MacroType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Iterator;
import java.util.List;

public final class MacroRegistry extends Registry<Macro> implements ClientAccess, JsonConfigurable {

   public MacroRegistry() {
      ClientAccess.eventBus.subscribe(this);
   }

   @Override
   public void fromJson(JsonElement json) {
      if (!json.isJsonObject()) {
         return;
      }
      JsonObject rootObject = json.getAsJsonObject();
      if (!rootObject.has("macros")) {
         return;
      }
      rootObject = json.getAsJsonObject();
      JsonArray macroArray = rootObject.getAsJsonArray("macros");
      Iterator macroIterator = macroArray.iterator();
      while (macroIterator.hasNext()) {
         JsonElement macroElement = (JsonElement)macroIterator.next();
         if (!macroElement.isJsonObject()) {
            continue;
         }
         JsonObject macroJson = macroElement.getAsJsonObject();
         String[] propertyNames = new String[5];
         propertyNames[0] = "name";
         propertyNames[1] = "type";
         propertyNames[2] = "key";
         propertyNames[3] = "mouse";
         propertyNames[4] = "commands";
         if (!JsonUtils.hasProperties(macroJson, propertyNames)) {
            continue;
         }
         try {
            JsonElement typeElement = macroJson.get("type");
            String typeName = typeElement.getAsString();
            MacroType type = MacroType.fromName(
               typeName
            );
            JsonElement nameElement = macroJson.get("name");
            String name = nameElement.getAsString();
            Keybind keybind = new Keybind(
               macroJson.get("key").getAsInt(), Keybind.Mode.TOGGLE, macroJson.get("mouse").getAsBoolean()
            );
            Macro macro = type.create(
               name, keybind
            );
            if (macroJson.has("toggled")) {
               JsonElement toggleElement = macroJson.get("toggled");
               boolean toggled = toggleElement.getAsBoolean();
               macro.setToggled(toggled);
            }
            JsonElement commandsElement = macroJson.get("commands");
            JsonArray commandsArray = commandsElement.getAsJsonArray();
            Iterator commandIterator = commandsArray.iterator();
            while (commandIterator.hasNext()) {
               JsonElement commandElement = (JsonElement)commandIterator.next();
               List commandsList = macro.getCommands();
               commandsList.add(commandElement.getAsString());
            }
            if (type == MacroType.HOLD) {
               while (true) {
                  int commandCount = macro.getCommands().size();
                  if (commandCount <= 2) {
                     break;
                  }
                  macro.getCommands().removeLast();
               }
            }
            this.registerEntry(macro);
         } catch (Exception ignoredException) {
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonArray macrosArray = new JsonArray();
      JsonObject rootObject = new JsonObject();
      Iterator<Macro> macroIterator = this.entries.iterator();
      while (macroIterator.hasNext()) {
         Macro macro = macroIterator.next();
         JsonObject macroJson = new JsonObject();
         macroJson.addProperty("name", macro.getName());
         macroJson.addProperty("toggled", macro.isToggled());
         MacroType type = macro.getType();
         macroJson.addProperty("type", type.getName());
         Keybind keybind = macro.getKeybind();
         macroJson.addProperty("key", keybind.code());
         keybind = macro.getKeybind();
         macroJson.addProperty("mouse", keybind.mouse());
         JsonArray commandsArray = new JsonArray();
         Iterator commandIterator = macro.getCommands().iterator();
         while (commandIterator.hasNext()) {
            String command = (String)commandIterator.next();
            commandsArray.add(command);
         }
         macroJson.add("commands", commandsArray);
         macrosArray.add(macroJson);
      }
      rootObject.add("macros", macrosArray);
      return rootObject;
   }

   @Override
   public String getConfigName() {
      return "macros.json";
   }

   @EventHandler
   private void onInputPress(InputPressEvent event) {
      if (ClientAccess.zioMinecraft.screen == null && !this.isWorldUnavailable()) {
         Iterator<Macro> macroIterator = this.entries.iterator();
         while (macroIterator.hasNext()) {
            Macro macro = macroIterator.next();
            if (macro.isToggled()) {
               Keybind keybind = macro.getKeybind();
               boolean mouse = keybind.mouse();
               if (mouse == event.isMouse()) {
                  keybind = macro.getKeybind();
                  int keyCode = keybind.code();
                  if (keyCode == event.getCode()) {
                     macro.run();
                     ClientAccess.eventBus.post(new MacroTriggerEvent(macro));
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onTick(WorldRenderEvent.Main event) {
      if (ClientAccess.zioMinecraft.screen == null && !this.isWorldUnavailable()) {
         Iterator<Macro> macroIterator = this.entries.iterator();
         while (macroIterator.hasNext()) {
            Macro macro = macroIterator.next();
            if (macro instanceof HoldMacro holdMacro) {
               if (holdMacro.isToggled()) {
                  Keybind keybind = holdMacro.getKeybind();
                  boolean physicallyDown = KeyUtils.isPhysicallyDown(keybind);
                  holdMacro.updatePressed(physicallyDown);
               }
            }
         }
      }
   }
}
