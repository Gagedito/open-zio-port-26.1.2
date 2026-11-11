/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.utils.inventory.PalettedContainerDataAccess;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer.Data;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Data.class)
public class PalettedContainerDataMixin implements PalettedContainerDataAccess {
   @Shadow
   @Final
   BitStorage storage;
   @Shadow
   @Final
   Palette palette;

   @Override
   public BitStorage zio$storage() {
      return this.storage;
   }

   @Override
   public Palette zio$palette() {
      return this.palette;
   }
}
