/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import dev.zio.client.ClientAccess;

import com.mojang.blaze3d.vertex.PoseStack;

public abstract class TracerRenderer implements ClientAccess {
   protected final TracersModule module;

   public TracerRenderer(TracersModule module) {
      this.module = module;
   }

   public abstract void render(PoseStack poseStack, float tickDelta);

}
