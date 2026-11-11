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

public enum SpeedMineValue121Choice implements NamedEntry {
   OFF("Off"),
   TICK("Tick"),
   SLOW("Slow");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   SpeedMineValue121Choice(String displayName) {
      this.displayName = displayName;
   }
}
