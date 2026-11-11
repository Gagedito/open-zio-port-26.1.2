/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.utils.inventory.PalettedContainerAccess;
import dev.zio.client.utils.inventory.PalettedContainerDataAccess;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainer.Data;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PalettedContainer.class)
public class PalettedContainerMixin implements PalettedContainerAccess {
   @Shadow
   private volatile Data data;

   @Override
   public PalettedContainerDataAccess zio$data() {
      return (PalettedContainerDataAccess)(Object)this.data;
   }
}
