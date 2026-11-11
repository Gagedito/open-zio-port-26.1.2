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

public enum AutoCrystalInstantChoice implements NamedEntry {
   ALWAYS("Always"),
   STRICT("Strict"),
   NONE("None");

   private final String displayName;

   public boolean isEnabled() {
      return this != NONE;
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   AutoCrystalInstantChoice(String displayName) {
      this.displayName = displayName;
   }
}
