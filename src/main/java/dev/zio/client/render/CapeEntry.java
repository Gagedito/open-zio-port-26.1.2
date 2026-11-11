/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import net.minecraft.resources.Identifier;

public record CapeEntry(
   String playerName,
   String textureFileName
) {

   public Identifier getTexture() {
      String texturePath = "capes/" + this.textureFileName;
      return Identifier.fromNamespaceAndPath("zio", texturePath);
   }
}
