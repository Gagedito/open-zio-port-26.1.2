/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.zio.client.ClientAccess;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class NameTagItemRenderer {
   private static final SubmitNodeStorage submitNodes = new SubmitNodeStorage();
   private static final NameTagBufferSource buffers = new NameTagBufferSource();
   private static final FeatureRenderDispatcher featureDispatcher = new FeatureRenderDispatcher(
      submitNodes,
      ClientAccess.zioMinecraft.getModelManager(),
      buffers,
      ClientAccess.zioMinecraft.getAtlasManager(),
      NoOpOutlineBufferSource.INSTANCE,
      DiscardingBufferSource.INSTANCE,
      ClientAccess.zioMinecraft.font,
      ClientAccess.zioMinecraft.gameRenderer.getGameRenderState()
   );

   private NameTagItemRenderer() {
   }

   public static SubmitNodeStorage getSubmitNodes() {
      return submitNodes;
   }

   public static void flush() {
      featureDispatcher.renderAllFeatures();
      buffers.endBatch();
      submitNodes.endFrame();
   }

   private static final class NameTagBufferSource extends BufferSource {
      private NameTagBufferSource() {
         super(new ByteBufferBuilder(786432), new Object2ObjectLinkedOpenHashMap<>());
      }

      @Override
      public VertexConsumer getBuffer(RenderType renderType) {
         RenderType itemType = ClientRenderTypes.nameTagItem(renderType);
         if (ClientRenderPipelines.isGlint(itemType.state.pipeline)) {
            this.fixedBuffers.computeIfAbsent(itemType, type -> new ByteBufferBuilder(type.bufferSize()));
         }
         return super.getBuffer(itemType);
      }
   }
}
