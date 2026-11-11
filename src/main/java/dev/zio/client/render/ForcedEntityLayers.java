/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

public interface ForcedEntityLayers {
   void zio$setForcedLayers(boolean forced);

   boolean zio$areLayersForced();
}
