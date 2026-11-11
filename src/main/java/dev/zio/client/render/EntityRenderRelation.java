/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.function.BiConsumer;

public enum EntityRenderRelation {
   DEFAULT,
   FRIEND,
   ENEMY;

   private final MeshBuffer meshBuffer = MeshBuffer.create()
      .setFormat(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

   public MeshBuffer getMeshBuffer() {
      return this.meshBuffer;
   }

   public static void forEachMesh(BiConsumer consumer) {
      for (EntityRenderRelation relation : values()) {
         consumer.accept(relation, relation.getMeshBuffer());
      }
   }
}
