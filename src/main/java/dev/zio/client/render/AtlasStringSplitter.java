/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import net.minecraft.client.StringSplitter;

public class AtlasStringSplitter extends StringSplitter {

   public AtlasStringSplitter(AtlasTextRenderer renderer) {
      super(
         (codePoint, ignoredStyle) -> renderer.getCharacterWidth(
            Character.toString(codePoint).charAt(0)
         )
      );
   }

}
