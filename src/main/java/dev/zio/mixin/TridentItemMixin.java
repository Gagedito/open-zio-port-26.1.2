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
import dev.zio.client.ClientContext;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.exploit.TridentModule;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(TridentItem.class)
public class TridentItemMixin {
   private static TridentModule tridentModule = ClientContext.getModule(TridentModule.class);

   @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
   private boolean allowRiptideOnRelease(boolean inWaterOrRain) {
      return inWaterOrRain
         || tridentModule.isAlwaysRiptideEnabled();
   }

   @ModifyExpressionValue(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
   private boolean allowRiptideOnUse(boolean inWaterOrRain) {
      return inWaterOrRain
         || tridentModule.isAlwaysRiptideEnabled();
   }

   @ModifyArgs(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;push(DDD)V"))
   private void scaleRiptidePush(Args arguments) {
      arguments.set(
         0,
         arguments.<Double>get(0)
            * tridentModule.getBoostMultiplier()
      );
      arguments.set(
         1,
         arguments.<Double>get(1)
            * tridentModule.getBoostMultiplier()
      );
      arguments.set(
         2,
         arguments.<Double>get(2)
            * tridentModule.getBoostMultiplier()
      );
   }

   @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "CONSTANT", args = "intValue=10"))
   private int removeMinimumChargeTime(int originalChargeTime) {
      return tridentModule.isToggled() ? 0 : originalChargeTime;
   }

   @Unique
   private static void zio$bindTridentItemModules(ModuleRegistry moduleRegistry) {
      tridentModule = (TridentModule)moduleRegistry.getModule(
         TridentModule.class
      );
   }
}
