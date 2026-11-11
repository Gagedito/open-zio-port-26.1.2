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

public enum AutoCrystalSequentialChoice implements NamedEntry {
   NONE("None"),
   STRICT("Strict"),
   STRONG("Strong");

   private final String displayName;

   public boolean isEnabled() {
      return this != NONE;
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   AutoCrystalSequentialChoice(String displayName) {
      this.displayName = displayName;
   }
}
