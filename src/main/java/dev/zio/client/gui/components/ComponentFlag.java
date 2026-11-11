/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

public enum ComponentFlag {
   PADDING_SHIFT,
   SILENT,
   SILENT_HOVER;

   private final int mask = 1 << this.ordinal();

   public int getMask() {
      return this.mask;
   }
}
