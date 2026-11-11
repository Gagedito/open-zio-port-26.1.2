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
import dev.zio.client.ClientContext;
import dev.zio.client.modules.render.NoRenderModule;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.MapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MapRenderer.class)
public class MapRendererMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void suppressMapRendering(
      MapRenderState mapState, PoseStack poses, SubmitNodeCollector collector, boolean ignoredRenderFlag, int packedLight, CallbackInfo callback
   ) {
      if (noRenderModule.shouldHideMaps()) {
         callback.cancel();
      }
   }
}
