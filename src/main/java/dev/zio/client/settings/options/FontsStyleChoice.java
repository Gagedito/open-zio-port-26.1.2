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

public enum FontsStyleChoice implements NamedEntry {
   PLAIN("Plain"),
   BOLD("Bold"),
   ITALIC("Italic"),
   BOLDITALIC("BoldItalic");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   FontsStyleChoice(String displayName) {
      this.displayName = displayName;
   }
}
