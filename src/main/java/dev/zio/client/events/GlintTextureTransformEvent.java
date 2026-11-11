/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public final class GlintTextureTransformEvent extends CancellableEvent {
   private static final GlintTextureTransformEvent INSTANCE = new GlintTextureTransformEvent();
   private float scale = 1.0F;

   public void setScale(float scale) {
      this.scale = scale;
   }

   public static GlintTextureTransformEvent resetAndGet() {
      INSTANCE.setScale(1.0F);
      return INSTANCE;
   }

   public float getScale() {
      return this.scale;
   }
}
