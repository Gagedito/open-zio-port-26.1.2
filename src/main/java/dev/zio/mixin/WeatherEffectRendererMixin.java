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
import dev.zio.client.modules.render.AmbienceModule;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.biome.Biome.Precipitation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin implements ClientAccess {
   private static final AmbienceModule ambienceModule = ClientContext.getModule(AmbienceModule.class);

   @ModifyExpressionValue(method = "renderInstances", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"))
   private int zio$applyWeatherOpacity(int originalColor) {
      if (ambienceModule.isToggled()
         && ambienceModule.worldWeatherSetting.getValue()) {
         float opacityMultiplier = (ambienceModule.opacitySetting.getValue())
               .intValue()
            / 100.0F;
         int adjustedAlpha = (int)(ARGB.alpha(originalColor) * opacityMultiplier) & 0xFF;
         return adjustedAlpha << 24 | originalColor & 0xFFFFFF;
      } else {
         return originalColor;
      }
   }

   @ModifyExpressionValue(
      method = "extractRenderState",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I")
   )
   private int zio$forceWeatherBelowSurface(int originalHeight) {
      return ambienceModule.isToggled()
            && ambienceModule.worldWeatherSetting.getValue()
            && ambienceModule.weatherAlwaysSetting.getValue()
            && (ambienceModule.weatherSetting.getValue()).supportsPrecipitation()
         ? Integer.MIN_VALUE
         : originalHeight;
   }

   @ModifyExpressionValue(
      method = "getPrecipitationAt",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;")
   )
   private Precipitation zio$overridePrecipitation(Precipitation originalPrecipitation) {
      Precipitation configuredPrecipitation = (ambienceModule.weatherSetting
            .getValue())
         .getPrecipitation();
      return ambienceModule.isToggled()
            && ambienceModule.worldWeatherSetting.getValue()
            && configuredPrecipitation != null
         ? configuredPrecipitation
         : originalPrecipitation;
   }
}
