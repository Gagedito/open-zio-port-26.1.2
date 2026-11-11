/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.render.SkyboxModule;
import net.minecraft.client.multiplayer.ClientLevel.ClientLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevelData.class)
public class ClientLevelClientLevelDataMixin {
   private static final SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);

   @Inject(method = "getHorizonHeight", at = @At("HEAD"), cancellable = true)
   private void overrideHorizonHeightForSkybox(CallbackInfoReturnable<Double> callback) {
      if (ClientAccess.zioMinecraft.level != null) {
         if (skyboxModule.isToggled()
            && skyboxModule.isCurrentDimensionEnabled()
            && !ClientAccess.zioMinecraft
               .level
               .dimension()
               .identifier()
               .getPath()
               .contains("over")) {
            callback.setReturnValue(1.0);
            callback.cancel();
         }
      }
   }
}
