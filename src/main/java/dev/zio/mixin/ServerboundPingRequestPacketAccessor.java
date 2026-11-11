/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundPingRequestPacket.class)
public interface ServerboundPingRequestPacketAccessor {
   @Accessor("time")
   @Mutable
   void zio$startTime(long time);

   @Accessor("time")
   @Mutable
   long zio$startTime();
}
