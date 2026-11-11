/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.zio.client.gui.screens.ClientScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
   @ModifyExpressionValue(method = "getThrottleReason", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"))
   private Screen zio$ignoreClientScreenForThrottle(Screen screen) {
      return screen instanceof ClientScreen ? null : screen;
   }
}
