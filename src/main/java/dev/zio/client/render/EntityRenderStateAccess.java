/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

public interface EntityRenderStateAccess {
   Entity zio$getEntity();

   void zio$setEntity(Entity entity);

   static Entity getEntity(EntityRenderState renderState) {
      EntityRenderStateAccess access = (EntityRenderStateAccess)renderState;
      return access.zio$getEntity();
   }

   static void setEntity(EntityRenderState renderState, Entity entity) {
      EntityRenderStateAccess access = (EntityRenderStateAccess)renderState;
      access.zio$setEntity(entity);
   }
}
