/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.zio.client.ClientAccess;
import dev.zio.client.events.ClientChunkCacheReplaceWithPacketDataEvent;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheMixin {

   @Inject(
      method = "replaceWithPacketData",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;onChunkLoaded(Lnet/minecraft/world/level/ChunkPos;)V", shift = Shift.BEFORE)
   )
   private void postChunkPacketDataEvent(
      int chunkX, int chunkZ, FriendlyByteBuf packetData, Map<Heightmap.Types, long[]> heightmaps, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntityConsumer, CallbackInfoReturnable<LevelChunk> callback, @Local LevelChunk chunk
   ) {
      ClientAccess.eventBus
         .post(
            new ClientChunkCacheReplaceWithPacketDataEvent(chunk)
         );
   }
}
