/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import dev.zio.client.render.BufferSourceCleanup;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.raphimc.immediatelyfast.feature.core.BatchableBufferSource;
import net.raphimc.immediatelyfast.feature.core.ByteBufferBuilderPool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(BatchableBufferSource.class)
public class BatchableBufferSourceMixin implements BufferSourceCleanup {
   @Shadow(remap = false)
   @Final
   protected Map dynamicBuffers;
   @Shadow(remap = false)
   @Final
   protected Set activeRenderTypes;

   @Override
   public void zio$cleanAll() {
      for (Object activeRenderType : this.activeRenderTypes) {
         RenderType renderType = (RenderType)activeRenderType;
         ReferenceSet builders = (ReferenceSet)this.dynamicBuffers.remove(renderType);
         if (builders != null) {
            for (Object activeBuilder : builders) {
               BufferBuilder builder = (BufferBuilder)activeBuilder;
               MeshData mesh = builder.build();
               ByteBufferBuilderPool.returnBufferBuilderSafe(builder.buffer);
               if (mesh != null) {
                  mesh.close();
               }
            }

            builders.clear();
         }
      }
   }
}
