/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrashReport.class)
public class CrashReportMixin {

   @Inject(method = "getDetails(Ljava/lang/StringBuilder;)V", at = @At("TAIL"))
   public void appendClientCrashDetails(StringBuilder details, CallbackInfo callback) {
      details.append("\n\n-- https://github.com/spectrumdevs/zio --\n");
      details.append("Details:\n");
      details.append("\tMagic: b9edAc3b3Ba4eD721B4efaEe\n\n");
   }
}
