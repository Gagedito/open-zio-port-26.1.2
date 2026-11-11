/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.settings.NamedEntry;

public enum WarpRechargeChoice implements NamedEntry {
   HOLD("Hold"),
   INSTANT("Instant"),
   NONE("None");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   WarpRechargeChoice(String displayName) {
      this.displayName = displayName;
   }
}
