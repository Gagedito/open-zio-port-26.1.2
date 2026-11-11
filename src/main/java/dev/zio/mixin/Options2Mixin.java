/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.zio.client.modules.render.AmbienceModule;
import dev.zio.client.ClientContext;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net/minecraft/client/Options$2")
public class Options2Mixin {
   private static final AmbienceModule ambienceModule = ClientContext.getModule(AmbienceModule.class);

   // Mojang leaves the option parse error lambda unnamed.
   @WrapWithCondition(
      method = "method_42571(Ljava/lang/String;Lnet/minecraft/client/OptionInstance;Lcom/mojang/serialization/DataResult$Error;)V",
      at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;[Ljava/lang/Object;)V", remap = false)
   )
   private static boolean shouldLogOptionParseError(
      Logger logger, String message, Object[] arguments
   ) {
      return ambienceModule.brightnessSetting.getValue()
         != AmbienceModule.BrightnessChoice.GAMMA;
   }
}
