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

public enum AntiCheatMiningChoice implements NamedEntry {
   VANILLA("Vanilla"),
   GRIM("Grim"),
   GRIM_STRICT("GrimStrict");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AntiCheatMiningChoice(String displayName) {
      this.displayName = displayName;
   }
}
