/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public final class StringSetSetting extends SetSetting<String> {

   public StringSetSetting(String name, String... defaultEntries) {
      super(name, defaultEntries);
   }

   public StringSetSetting(String name, Predicate<? super Set<String>> visibility, String... defaultEntries) {
      super(name, visibility, defaultEntries);
   }

   @Override
   public String parseEntry(String text) {
      return text.toLowerCase(Locale.ROOT);
   }

   @Override
   public Collection<String> getAvailableEntries() {
      return Collections.emptyList();
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json.isJsonArray()) {
         Set<String> values = this.getValue();
         values.clear();
         JsonArray array = json.getAsJsonArray();
         Iterator entries = array.iterator();
         while (entries.hasNext()) {
            JsonElement entry = (JsonElement)entries.next();
            String text = entry.getAsString();
            values.add(text);
         }
      }
   }

   @Override
   public String formatEntry(String entry) {
      return entry.toLowerCase(Locale.ROOT);
   }
}
