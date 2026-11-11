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
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class ChamsBufferSource extends BufferSource {
   public static final ChamsBufferSource INSTANCE = new ChamsBufferSource();
   private final Object2ObjectOpenHashMap<RenderType, VertexConsumer> consumersByRenderType = new Object2ObjectOpenHashMap<>();

   ChamsBufferSource() {
      super(null, null);
   }

   @Override
   public VertexConsumer getBuffer(RenderType renderType) {
      return renderType.state.outputTarget == OutputTarget.ITEM_ENTITY_TARGET
         ? NoOpVertexConsumer.INSTANCE
         : this.consumersByRenderType.computeIfAbsent(renderType, ignored -> new ChamsVertexConsumer());
   }
}
