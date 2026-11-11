/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.render.EntityRenderStateAccess;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EntityRenderStateAccess {
   @Unique
   private Entity renderedEntity;

   @Override
   public Entity zio$getEntity() {
      return this.renderedEntity;
   }

   @Override
   public void zio$setEntity(Entity entity) {
      this.renderedEntity = entity;
   }
}
