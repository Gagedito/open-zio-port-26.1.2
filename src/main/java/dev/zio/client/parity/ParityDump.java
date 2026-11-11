/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.parity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.commands.Command;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.Keybind;
import dev.zio.client.settings.Setting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Emits a structural parity snapshot (modules, settings, commands) so the
 * 1.21.11 baseline and the 26.1.2 port can be diffed mechanically. Inactive
 * unless the {@code zio.parity.dump} system property points at an output file;
 * must run before configuration load so defaults are captured.
 */
public final class ParityDump {
   private ParityDump() {
   }

   public static void dumpIfRequested() {
      String path = System.getProperty("zio.parity.dump");
      if (path == null) {
         return;
      }
      try {
         write(path);
         ClientAccess.logger.info("Parity dump written to {}", path);
      } catch (Exception failure) {
         throw new IllegalStateException("Parity dump failed", failure);
      }
   }

   private static void write(String path) throws IOException {
      JsonObject root = new JsonObject();
      root.addProperty("kind", "zio-parity-v1");
      root.add("modules", buildModules());
      root.add("commands", buildCommands());
      Files.createDirectories(Path.of(path).toAbsolutePath().getParent());
      Files.writeString(Path.of(path), root.toString() + System.lineSeparator(), StandardCharsets.UTF_8);
   }

   private static JsonArray buildModules() {
      List<Module> modules = new ArrayList<>();
      Collection<?> registered = (Collection<?>)ClientContext.moduleRegistry.getRegistry();
      registered.forEach(entry -> {
         if (entry instanceof Module module) modules.add(module);
      });
      modules.sort(Comparator.comparing(Module::getName));
      JsonArray array = new JsonArray();
      for (Module module : modules) {
         JsonObject object = new JsonObject();
         object.addProperty("name", module.getName());
         ModuleCategory category = module.getCategory();
         object.addProperty("category", category == null ? null : category.getName());
         object.addProperty("description", module.getDescription());
         Keybind keybind = module.getKeybind();
         object.addProperty("keybind", keybind == null ? null : keybind.toString());
         object.addProperty("wip", module.isWip());
         JsonArray aliases = new JsonArray();
         for (String alias : module.getAliases()) aliases.add(alias);
         object.add("aliases", aliases);
         object.add("settings", buildSettings(module));
         array.add(object);
      }
      return array;
   }

   private static JsonArray buildSettings(Module module) {
      JsonArray array = new JsonArray();
      for (Setting<?> setting : module.getSettings()) {
         JsonObject object = new JsonObject();
         object.addProperty("name", setting.getName());
         Object value = safeValue(setting);
         object.addProperty("type", value == null
            ? "null"
            : (value instanceof Enum<?>
               ? "enum:" + ((Enum<?>)value).getDeclaringClass().getSimpleName()
               : value.getClass().getSimpleName()));
         object.addProperty("default", value == null ? null : value.toString());
         Object minimum = safeBound(setting, false);
         object.addProperty("min", minimum == null ? "" : minimum.toString());
         Object maximum = safeBound(setting, true);
         object.addProperty("max", maximum == null ? "" : maximum.toString());
         if (value instanceof Enum<?>) {
            JsonArray options = new JsonArray();
            for (Object option : ((Enum<?>)value).getDeclaringClass().getEnumConstants()) {
               options.add(option.toString());
            }
            object.add("options", options);
         }
         array.add(object);
      }
      return array;
   }

   private static JsonArray buildCommands() {
      List<Command> commands = new ArrayList<>();
      Collection<?> registered = (Collection<?>)ClientContext.commandRegistry.getRegistry();
      registered.forEach(entry -> {
         if (entry instanceof Command command) commands.add(command);
      });
      commands.sort(Comparator.comparing(Command::getName));
      JsonArray array = new JsonArray();
      for (Command command : commands) {
         JsonObject object = new JsonObject();
         object.addProperty("name", command.getName());
         JsonArray aliases = new JsonArray();
         if (command.getAliases() != null) {
            for (String alias : command.getAliases()) aliases.add(alias);
         }
         object.add("aliases", aliases);
         array.add(object);
      }
      return array;
   }

   private static Object safeValue(Setting<?> setting) {
      try {
         return setting.getDefaultValue();
      } catch (Throwable failure) {
         return "THREW:" + failure.getClass().getSimpleName();
      }
   }

   private static Object safeBound(Setting<?> setting, boolean maximum) {
      try {
         return maximum ? setting.getMaximumValue() : setting.getMinimumValue();
      } catch (Throwable failure) {
         return "THREW:" + failure.getClass().getSimpleName();
      }
   }
}
