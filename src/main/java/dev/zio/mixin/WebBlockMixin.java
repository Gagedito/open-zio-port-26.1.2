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
import dev.zio.client.modules.movement.FastWebModule;
import dev.zio.client.managers.ModuleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WebBlock.class)
public class WebBlockMixin {
   private static FastWebModule fastWebModule = ClientContext.getModule(FastWebModule.class);

   @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
   private void cancelGrimWebEffects(
      BlockState state, Level level, BlockPos position, Entity entity, InsideBlockEffectApplier effects, boolean bl, CallbackInfo callback
   ) {
      if (fastWebModule.isGrimEnabled()) {
         callback.cancel();
      }
   }

   @Unique
   private static void zio$bindWebBlockModules(ModuleRegistry moduleRegistry) {
      fastWebModule = (FastWebModule)moduleRegistry.getModule(
         FastWebModule.class
      );
   }
}
