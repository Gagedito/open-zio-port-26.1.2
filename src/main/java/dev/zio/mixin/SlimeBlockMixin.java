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
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.movement.NoSlowModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlimeBlock.class)
public class SlimeBlockMixin {
   private static NoSlowModule noslow = ClientContext.getModule(NoSlowModule.class);

   @Inject(method = "stepOn", at = @At("HEAD"), cancellable = true)
   private void suppressSlimeStepEffect(
      Level level, BlockPos blockPos, BlockState blockState, Entity entity, CallbackInfo callback
   ) {
      if (noslow.isToggled() && noslow.slimeSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "bounceUp", at = @At("HEAD"), cancellable = true)
   private void suppressSlimeBounce(Entity entity, CallbackInfo callback) {
      if (noslow.isToggled() && noslow.slimeSetting.getValue() && noslow.noSlimeBounceSetting.getValue()) {
         callback.cancel();
      }
   }

   @Unique
   private static void zio$bindSlimeBlockModules(ModuleRegistry moduleRegistry) {
      noslow = (NoSlowModule)moduleRegistry.getModule(NoSlowModule.class);
   }
}
