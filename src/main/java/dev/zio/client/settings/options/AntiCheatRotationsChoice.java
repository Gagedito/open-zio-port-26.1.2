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

public enum AntiCheatRotationsChoice implements NamedEntry {
   MOTION("Motion"),
   SILENT("Silent");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AntiCheatRotationsChoice(String displayName) {
      this.displayName = displayName;
   }
}
