/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import net.minecraft.client.renderer.MultiBufferSource;

public final class BufferSourceUtils {
   private BufferSourceUtils() {
   }

   public static void cleanup(MultiBufferSource source) {
      if (source instanceof BufferSourceCleanup cleanup) {
         cleanup.zio$cleanAll();
      }
   }
}
