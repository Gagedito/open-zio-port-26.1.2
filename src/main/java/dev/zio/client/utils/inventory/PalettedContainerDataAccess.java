/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;

public interface PalettedContainerDataAccess {
   static BitStorage getStorage(PalettedContainer container) {
      PalettedContainerDataAccess data = ((PalettedContainerAccess)container).zio$data();
      return data.zio$storage();
   }

   static Palette getPalette(PalettedContainer container) {
      PalettedContainerDataAccess data = ((PalettedContainerAccess)container).zio$data();
      return data.zio$palette();
   }

   BitStorage zio$storage();

   Palette zio$palette();
}
