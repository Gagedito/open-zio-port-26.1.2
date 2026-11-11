/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.ClientContext;
import dev.zio.client.modules.player.FreecamModule;
import dev.zio.client.modules.render.XrayModule;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisGraph.class)
public class VisGraphMixin {
   private static FreecamModule freecam = ClientContext.getModule(FreecamModule.class);

   @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
   private void suppressOpaqueBlockMarking(BlockPos blockPos, CallbackInfo callback) {
      if (XrayModule.getInstance()
            .isToggled()
         || freecam.isToggled()) {
         callback.cancel();
      }
   }
}
