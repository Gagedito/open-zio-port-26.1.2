/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings;

import net.minecraft.network.chat.Component;

public interface NamedEntry {
   String getName();

   default NamedEntry withName(String name) {
      return () -> name;
   }

   default Component getDisplayComponent() {
      return Component.literal(this.getName());
   }
}
