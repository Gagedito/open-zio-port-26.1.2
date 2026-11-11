/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import org.lwjgl.system.MemoryStack;

public class SodiumMeshBuffer extends MeshBuffer implements VertexBufferWriter {
   public SodiumMeshBuffer(int capacity) {
      super(capacity);
   }

   @Override
   public void push(MemoryStack stack, long vertices, int vertexCount, VertexFormat format) {

   }

   @Override
   public boolean canUseIntrinsics() {
      return false;
   }

}
