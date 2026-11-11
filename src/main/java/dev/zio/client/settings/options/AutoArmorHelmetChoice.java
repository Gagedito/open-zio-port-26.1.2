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

public enum AutoArmorHelmetChoice implements NamedEntry {
   BEST("Best"),
   TURTLE("Turtle"),
   GOLD("Golden"),
   NONE("None");

   private final String displayName;

   public boolean isNone() {
      return this == NONE;
   }

   public boolean isTurtle() {
      return this == TURTLE;
   }

   public boolean isGold() {
      return this == GOLD;
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   AutoArmorHelmetChoice(String displayName) {
      this.displayName = displayName;
   }

}
