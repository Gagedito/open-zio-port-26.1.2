/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings;

import dev.zio.client.render.TextUtils;

public interface CyclingTextSetting {
   boolean matchesText(String text);

   void setValueFromText(String text);

   default void parseValue(String text) {
      String[] values = TextUtils.splitQuotedArguments(text);
      if (values.length > 1) {
         this.cycleValues(values);
      } else {
         this.setValueFromText(text == null ? "" : text);
      }
   }

   private void cycleValues(String[] values) {
      for (int index = 0; index < values.length; index++) {
         String candidate = values[index];
         if (this.matchesText(candidate)) {
            this.setValueFromText(values[(index + 1) % values.length]);
            return;
         }
      }

      this.setValueFromText(values[0]);
   }
}
