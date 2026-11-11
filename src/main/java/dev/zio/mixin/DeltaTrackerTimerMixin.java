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
import net.minecraft.client.DeltaTracker.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Timer.class)
public class DeltaTrackerTimerMixin {
   @Shadow
   private float deltaTicks;

   @Inject(method = "advanceGameTime", at = @At(value = "FIELD", target = "Lnet/minecraft/client/DeltaTracker$Timer;lastMs:J"))
   public void applyClientTickRateMultiplier(long nowMillis, CallbackInfoReturnable<Integer> callback) {
      if (ClientContext.tickRateController != null) {
         this.deltaTicks = this.deltaTicks
            * ClientContext.tickRateController
               .getMultiplier();
      }
   }
}
