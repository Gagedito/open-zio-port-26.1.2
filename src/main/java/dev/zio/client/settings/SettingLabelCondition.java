/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings;

import dev.zio.client.settings.Setting;

public enum SettingLabelCondition {
   MIN {
      @Override
      public boolean matches(Setting<?> setting) {
         Object value = setting.getValue();
         return value.equals(setting.getMinimumValue());
      }
   },
   MAX {
      @Override
      public boolean matches(Setting<?> setting) {
         Object value = setting.getValue();
         return value.equals(setting.getMaximumValue());
      }
   };

   public boolean matches(Setting<?> setting) {
      return false;
   }
}
