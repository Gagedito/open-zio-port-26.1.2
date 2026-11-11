/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.entity;

import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.settings.Setting;

public enum SwingAction {
   INTERACT,
   MINING,
   HIT;

   public boolean isEnabled(AntiCheatModule antiCheat) {
      Setting<Boolean> swingSetting = antiCheat.swingSetting;
      Boolean swingEnabled = swingSetting.getValue();
      if (!swingEnabled) {
         return false;
      } else if (this == MINING) {
         Setting<Boolean> miningSetting = antiCheat.miningSwingSetting;
         Boolean miningEnabled = miningSetting.getValue();
         return miningEnabled;
      } else if (this == HIT) {
         Setting<Boolean> hitSetting = antiCheat.hitSetting;
         Boolean hitEnabled = hitSetting.getValue();
         return hitEnabled;
      } else {
         Setting<Boolean> placeSetting = antiCheat.placeSetting;
         Boolean placeEnabled = placeSetting.getValue();
         return placeEnabled && this == INTERACT;
      }
   }
}
