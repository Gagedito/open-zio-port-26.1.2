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

public enum ChestStealerModeChoice implements NamedEntry {
   STEAL("Steal"),
   FILL("Fill"),
   DROP("Drop"),
   REFILL("Refill"),
   REKIT("Rekit");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ChestStealerModeChoice(String displayName) {
      this.displayName = displayName;
   }
}
