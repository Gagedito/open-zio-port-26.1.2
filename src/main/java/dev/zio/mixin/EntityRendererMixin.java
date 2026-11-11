/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.render.EntityRenderStateAccess;
import dev.zio.client.events.NameTagRenderEvent;
import dev.zio.client.modules.render.NameTagsModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
   private static final NameTagsModule nameTagsModule = ClientContext.getModule(NameTagsModule.class);


   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void suppressVanillaNameTagInRenderState(
      Entity entity, EntityRenderState renderState, float partialTick, CallbackInfo callback
   ) {
      boolean hide = ClientContext.shaderRegistry.renderingIntoTarget;
      if (!hide) {
         // repite el NameTagRenderEvent que antes se disparaba en submitNameTag
         // (consumers: NameTagsModule.onNameTagCheck y NoRenderModule.onNameTagRender;
         // ambos solo leen getEntity, el pose puede ir nulo).
         NameTagRenderEvent event = new NameTagRenderEvent(null, entity);
         ClientAccess.eventBus.post(event);
         hide = event.isCancelled();
      }

      if (hide) {
         renderState.nameTag = null;
      }
   }

   @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
   private void suppressVanillaPlayerNameTag(
      Entity entity, double distanceSquared, CallbackInfoReturnable<Boolean> callback
   ) {
      if (entity instanceof Player && nameTagsModule.isToggled()) {
         callback.cancel();
         callback.setReturnValue(entity.hasCustomName());
      }
   }

   @Inject(method = "extractRenderState", at = @At("HEAD"))
   private void bindEntityToRenderState(
      Entity entity, EntityRenderState renderState, float partialTick, CallbackInfo callback
   ) {
      EntityRenderStateAccess.setEntity(renderState, entity);
   }
}
