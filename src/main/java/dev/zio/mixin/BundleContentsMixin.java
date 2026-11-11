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
import dev.zio.client.modules.render.TooltipsModule;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BundleContents.class)
public abstract class BundleContentsMixin {
   private static TooltipsModule tooltipsModule = ClientContext.getModule(TooltipsModule.class);

   @Shadow
   public abstract int size();

   @Inject(method = "getNumberOfItemsToShow", at = @At("HEAD"), cancellable = true)
   private void overrideBundlePreviewItemCount(CallbackInfoReturnable<Integer> callback) {
      if (tooltipsModule.isToggled()
         && tooltipsModule.bundleContentSetting.getValue()) {
         callback.setReturnValue(this.size());
      }
   }

   @Unique
   private static void zio$bindBundleContentsModules(ModuleRegistry moduleRegistry) {
      tooltipsModule = (TooltipsModule)moduleRegistry.getModule(
         TooltipsModule.class
      );
   }
}
