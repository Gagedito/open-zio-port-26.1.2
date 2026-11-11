/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import dev.zio.client.utils.inventory.NoOpVertexConsumer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

public class NoOpOutlineBufferSource extends OutlineBufferSource {
   public static final NoOpOutlineBufferSource INSTANCE = new NoOpOutlineBufferSource();

   @Override
   public void endOutlineBatch() {
   }

   private NoOpOutlineBufferSource() {
   }

   @Override
   public VertexConsumer getBuffer(RenderType renderType) {
      return NoOpVertexConsumer.INSTANCE;
   }
}
