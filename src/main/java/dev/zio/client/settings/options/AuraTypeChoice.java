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

public enum AuraTypeChoice implements NamedEntry {
   BOX("Box"),
   CIRCLE("Circle");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AuraTypeChoice(String displayName) {
      this.displayName = displayName;
   }
}
