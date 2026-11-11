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
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerboundUseItemOnPacket.class)
public class ServerboundUseItemOnPacketMixin implements ImportantPacket {
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
