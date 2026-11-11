/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.config;

import dev.zio.client.managers.AbstractRegistry;

import dev.zio.client.ClientContext;
import dev.zio.client.modules.Module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.function.Predicate;

public final class ModulePresetCodec {
   public ModulePresetCodec.DecodedPreset decode(String encodedPreset) {
      encodedPreset = encodedPreset.trim().replace("\n", "");
      String jsonText = new String(Base64.getDecoder().decode(encodedPreset));
      JsonElement parsedJson = JsonParser.parseString(jsonText);
      if (!parsedJson.isJsonObject()) {
         throw new IllegalArgumentException();
      }

      JsonObject presetJson = parsedJson.getAsJsonObject();
      String moduleConfigName = LegacyBranding.canonicalModuleName(this.readModuleConfigName(presetJson));
      AbstractRegistry registry = ClientContext.moduleRegistry;
      Predicate<Module> matchesModule = candidateModule -> {
         String candidateName = candidateModule.getConfigName();
         return candidateName.equals(moduleConfigName);
      };
      Module module = (Module)registry.findFirstOrNull(matchesModule);
      if (module == null) {
         throw new IllegalArgumentException();
      }
      return new ModulePresetCodec.DecodedPreset(module, presetJson);
   }

   private String readModuleConfigName(JsonObject presetJson) {
      if (presetJson.has("m")) {
         return presetJson.get("m").getAsString();
      } else if (presetJson.has("module")) {
         return presetJson.get("module").getAsString();
      } else {
         throw new IllegalArgumentException();
      }
   }

   public String encode(Module module) {
      JsonElement moduleJson = module.toJson();
      JsonObject presetJson = moduleJson.getAsJsonObject();
      presetJson.remove("key");
      presetJson.remove("state");
      presetJson.remove("mouse");
      presetJson.remove("enabled");
      presetJson.remove("drawn");
      String moduleConfigName = module.getConfigName();
      presetJson.addProperty("m", moduleConfigName);
      Encoder base64Encoder = Base64.getEncoder();
      String jsonText = presetJson.toString();
      Charset charset = StandardCharsets.UTF_8;
      byte[] jsonBytes = jsonText.getBytes(charset);
      return base64Encoder.encodeToString(jsonBytes);
   }

   public record DecodedPreset(
      Module module,
      JsonObject json
   ) {
   }
}
