/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public class MainMixin {

   @Inject(method = "main*", at = @At("HEAD"), remap = false)
   private static void configureAwtAtStartup(CallbackInfo callback) {
      boolean macOs = System.getProperty("os.name", "").startsWith("Mac");
      System.setProperty("java.awt.headless", Boolean.toString(macOs));
   }
}
