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

public enum DimensionType implements NamedEntry {
   OVERWORLD("overworld", "Overworld"),
   THE_NETHER("the_nether", "Nether"),
   THE_END("the_end", "End");

   private final String name;
   private final String displayName;

   public boolean isEnd() {
      return this == THE_END;
   }

   public boolean isOverworld() {
      return this == OVERWORLD;
   }

   public boolean isNether() {
      return this == THE_NETHER;
   }

   @Override
   public String getName() {
      return this.name;
   }

   DimensionType(String name, String displayName) {
      this.name = name;
      this.displayName = displayName;
   }

   public String getDisplayName() {
      return this.displayName;
   }
}
