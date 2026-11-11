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

public enum AutoArmorBlastChoice implements NamedEntry {
   NONE("None"),
   LEGS("Legs"),
   SMART("1.21"),
   ALL("All");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AutoArmorBlastChoice(String displayName) {
      this.displayName = displayName;
   }
}
