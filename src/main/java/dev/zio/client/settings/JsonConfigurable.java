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

public interface JsonConfigurable {
   void fromJson(JsonElement json);

   JsonElement toJson();

   default String getConfigName() {
      return "";
   }
}
