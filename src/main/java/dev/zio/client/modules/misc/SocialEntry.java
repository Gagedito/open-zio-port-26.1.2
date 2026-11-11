/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.options.SocialRole;

public record SocialEntry(
   String name,
   SocialRole role
) implements NamedEntry {

   @Override
   public String getName() {
      return this.name;
   }
}
