/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.zio.client.config.JsonUtils;
import dev.zio.client.utils.collections.NamedIdentifier;
import java.util.function.Predicate;

public final class IdentifierSetting extends Setting<NamedIdentifier> {

   public IdentifierSetting(String name, NamedIdentifier defaultValue) {
      super(name, defaultValue);
   }

   public IdentifierSetting(String name, NamedIdentifier defaultValue, Predicate<? super NamedIdentifier> visibility) {
      super(name, defaultValue, visibility);
   }

   @Override
   public void fromJson(JsonElement element) {
      JsonObject object = element.getAsJsonObject();
      if (JsonUtils.hasProperties(object, "name", "category")) {
         String name = object.get("name").getAsString();
         String category = object.get("category").getAsString();
         NamedIdentifier identifier = new NamedIdentifier(category, name);
         this.setValue(identifier);
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();
      NamedIdentifier identifier = this.getValue();
      object.addProperty("name", identifier.getName());
      object.addProperty("category", identifier.getCategory());
      return object;
   }

   @Override
   public void parseValue(String value) {
      String[] parts = value.split(":");
      if (parts.length == 1) {
         NamedIdentifier identifier = new NamedIdentifier(value);
         this.setValue(identifier);
      } else if (parts.length == 2) {
         NamedIdentifier identifier = new NamedIdentifier(parts[0], parts[1]);
         this.setValue(identifier);
      }
   }
}
