/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.managers.ModuleRegistry;
import net.minecraft.world.level.block.WebBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WebBlock.class)
public interface WebBlockModuleBindings {
   @Invoker(value = "zio$bindWebBlockModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
