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
import dev.zio.client.modules.movement.EntityControlModule;
import dev.zio.client.managers.ModuleRegistry;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class MobMixin {
   private static EntityControlModule entityControlModule = ClientContext.getModule(EntityControlModule.class);

   @Inject(method = "isSaddled", at = @At("HEAD"), cancellable = true)
   private void forceSaddledForEntityControl(CallbackInfoReturnable<Boolean> callback) {
      if (entityControlModule.isToggled()) {
         callback.setReturnValue(true);
      }
   }

   @Unique
   private static void zio$bindMobModules(ModuleRegistry moduleRegistry) {
      entityControlModule = (EntityControlModule)moduleRegistry.getModule(
         EntityControlModule.class
      );
   }
}
