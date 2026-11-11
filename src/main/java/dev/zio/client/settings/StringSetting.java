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
import com.google.gson.JsonPrimitive;
import java.util.function.Predicate;

public final class StringSetting extends Setting<String> {

   public StringSetting(String name, String defaultValue, Predicate<? super String> visibilityPredicate) {
      super(name, defaultValue, visibilityPredicate);
   }

   public StringSetting(String name, String defaultValue) {
      super(name, defaultValue);
   }

   @Override
   public void fromJson(JsonElement json) {
      this.setValue(json.getAsString());
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.getValue());
   }

   @Override
   public void parseValue(String text) {
      this.setValue(text);
   }
}
