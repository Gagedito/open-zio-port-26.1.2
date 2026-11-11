/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.zio.client.render.BuiltinResourcePack;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.server.packs.resources.FallbackResourceManager.class)
public class FallbackResourceManagerMixin {
   @ModifyReturnValue(method = "getResource", at = @At("RETURN"))
   private Optional<Resource> resolveClientResource(
      Optional<Resource> originalResource, @Local(argsOnly = true) Identifier resourceId
   ) {
      return BuiltinResourcePack.resolveResource(resourceId, originalResource);
   }
}
