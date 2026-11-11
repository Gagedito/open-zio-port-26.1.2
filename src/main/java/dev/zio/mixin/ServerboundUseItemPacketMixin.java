/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.utils.network.ImportantPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerboundUseItemPacket.class)
public class ServerboundUseItemPacketMixin implements ImportantPacket {
   @Unique
   private boolean important;

   @Override
   public void zio$markImportant() {
      this.important = true;
   }

   @Override
   public boolean zio$isImportant() {
      return this.important;
   }
}
