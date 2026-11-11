/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.misc.ExtraScreenshotModule;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screenshot.class)
public class ScreenshotMixin {
   private static ExtraScreenshotModule extraScreenshotModule = ClientContext.getModule(ExtraScreenshotModule.class);

   // Mojang leaves the screenshot save lambda unnamed.
   @Inject(method = "method_22691(Lcom/mojang/blaze3d/platform/NativeImage;Ljava/io/File;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
   private static void processExtraScreenshot(
      NativeImage image, File directory, Consumer messageConsumer, CallbackInfo callback
   ) {
      if (extraScreenshotModule.isToggled()) {
         Util.ioPool()
            .execute(
               () -> extraScreenshotModule.processScreenshot(
                  image
               )
            );
         if (extraScreenshotModule.shouldDeleteFile()) {
            callback.cancel();
         }
      }
   }

   @ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
   private static Consumer suppressVanillaScreenshotMessage(Consumer messageConsumer) {
      return extraScreenshotModule.isToggled()
            && !extraScreenshotModule.isModeNone()
         ? ignoredMessage -> {}
         : messageConsumer;
   }
}
