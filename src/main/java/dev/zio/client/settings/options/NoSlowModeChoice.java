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

public enum NoSlowModeChoice implements NamedEntry {
   NONE("None"),
   NCP("NCP"),
   GRIMOLD("GrimOld"),
   GRIMV3("Slow"),
   _2B2T("2b2t"),
   CONSTANTIAM("Constantiam");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   NoSlowModeChoice(String displayName) {
      this.displayName = displayName;
   }

}
