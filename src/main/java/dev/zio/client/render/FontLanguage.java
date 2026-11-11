/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import dev.zio.client.render.text.CharacterRange;
import dev.zio.client.render.text.ContiguousCharacterRange;
import dev.zio.client.render.text.DeltaEncodedCharacterRange;
import dev.zio.client.render.text.ExplicitCharacterRange;
import dev.zio.client.ZioClient;

public enum FontLanguage {
   SPECIAL(new ExplicitCharacterRange('∞', '♫', '♪')),
   LATIN(new ContiguousCharacterRange(0, 383)),
   CYRILLIC(new ContiguousCharacterRange(1024, 1327)),
   CHINESE(new DeltaEncodedCharacterRange(19968, ZioClient.class.getResourceAsStream("/assets/zio/font/range_cn.bin"))),
   JAPANESE(
      new DeltaEncodedCharacterRange(19968, ZioClient.class.getResourceAsStream("/assets/zio/font/range_jp.bin"))
   ),
   JAPANESE_ALPHABET(new ContiguousCharacterRange(12288, 12543));

   private final CharacterRange characterRange;

   FontLanguage(CharacterRange characterRange) {
      this.characterRange = characterRange;
   }

   public CharacterRange getRange() {
      return this.characterRange;
   }

}
