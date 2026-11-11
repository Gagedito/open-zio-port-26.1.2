/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.network;

import java.util.HashMap;
import java.util.Map;

public final class AutoPresetServerEntry {
   private final Map presets = new HashMap();
   private final String server;
   private String kitName;

   public Map getPresets() {
      return this.presets;
   }

   public String getKitName() {
      return this.kitName;
   }

   public boolean hasKit() {
      return this.kitName != null;
   }

   public String getServer() {
      return this.server;
   }

   public AutoPresetServerEntry(String server) {
      this.server = server;
   }

   public void setKitName(String kitName) {
      this.kitName = kitName;
   }
}
