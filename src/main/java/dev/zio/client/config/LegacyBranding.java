/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.zio.client.ZioClient;
import java.util.Locale;

public final class LegacyBranding {
    private LegacyBranding() {
    }

    public static String canonicalModuleName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "antivoid" -> "Avoid";
            case "autoupgrade" -> "AutoTemplate";
            case "discordnotifs" -> "WebhookNotifs";
            default -> name;
        };
    }

    public static JsonElement findModuleConfig(JsonObject config, String moduleName) {
        if (config.has(moduleName)) {
            return config.get(moduleName);
        }
        return switch (moduleName) {
            case "Avoid" -> config.get("AntiVoid");
            case "AutoTemplate" -> config.get("AutoUpgrade");
            case "WebhookNotifs" -> config.get("DiscordNotifs");
            default -> null;
        };
    }

    public static JsonElement migrateModule(String moduleName, JsonElement input) {
        if (!(input instanceof JsonObject module) || !(module.get("settings") instanceof JsonObject)) {
            return input;
        }
        if (!"Watermark".equals(moduleName) && !"Welcomer".equals(moduleName)
                && !"AutoSign".equals(moduleName) && !"DiscordNotifs".equals(moduleName)
                && !"WebhookNotifs".equals(moduleName) && !"FeetPlace".equals(moduleName)
                && !"AutoFarm".equals(moduleName) && !"EntityControl".equals(moduleName)) {
            return input;
        }
        JsonObject migrated = module.deepCopy();
        JsonObject settings = migrated.getAsJsonObject("settings");
        switch (moduleName) {
            case "AutoFarm" -> {
                if (!settings.has("Frequency") && settings.has("BPT")) {
                    settings.add("Frequency", settings.get("BPT"));
                }
                settings.remove("BPT");
            }
            case "EntityControl" -> {
                if (!settings.has("Baritone") && settings.has("PathFinder")) {
                    settings.add("Baritone", settings.get("PathFinder"));
                }
                settings.remove("PathFinder");
            }
            case "FeetPlace" -> {
                JsonElement blockIgnore = settings.get("BlockIgnore");
                JsonElement mineIgnore = settings.get("MineIgnore");
                boolean mining = mineIgnore != null && mineIgnore.isJsonPrimitive()
                    && mineIgnore.getAsJsonPrimitive().isBoolean() && mineIgnore.getAsBoolean();
                if (blockIgnore != null && blockIgnore.isJsonPrimitive() && blockIgnore.getAsJsonPrimitive().isBoolean()) {
                    settings.addProperty("BlockIgnore", blockIgnore.getAsBoolean() ? "Both" : mining ? "Mining" : "None");
                } else if (blockIgnore == null && mining) {
                    settings.addProperty("BlockIgnore", "Mining");
                }
                settings.remove("MineIgnore");
                JsonElement sequential = settings.get("Sequential");
                if (sequential != null && sequential.isJsonPrimitive() && sequential.getAsJsonPrimitive().isBoolean()) {
                    settings.addProperty("Sequential", sequential.getAsBoolean() ? "Strict" : "None");
                }
            }
            case "Watermark" -> replaceDefault(settings, "Text", "Mio", ZioClient.NAME);
            case "Welcomer" -> replaceDefault(settings, "Text", "Welcome to Mio.", "Welcome to Zio.");
            case "AutoSign" -> {
                replaceDefault(settings, "Line1", "get mioclient", "get Zio");
                if (equalsString(settings.get("Line3"), "www.mioclient.me")) {
                    if (equalsString(settings.get("Line4"), ":p")) {
                        settings.addProperty("Line3", "github.com/");
                        settings.addProperty("Line4", "spectrumdevs/zio");
                    } else {
                        settings.addProperty("Line3", ZioClient.PROJECT_URL);
                    }
                }
            }
            case "DiscordNotifs", "WebhookNotifs" -> {
                if (!settings.has("IgnoreZio") && settings.has("IgnoreMio")) {
                    settings.add("IgnoreZio", settings.get("IgnoreMio"));
                }
                settings.remove("IgnoreMio");
            }
            default -> throw new AssertionError(moduleName);
        }
        return migrated;
    }

    private static void replaceDefault(JsonObject settings, String key, String previous, String current) {
        if (equalsString(settings.get(key), previous)) {
            settings.addProperty(key, current);
        }
    }

    private static boolean equalsString(JsonElement value, String expected) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
            && expected.equals(value.getAsString());
    }
}
