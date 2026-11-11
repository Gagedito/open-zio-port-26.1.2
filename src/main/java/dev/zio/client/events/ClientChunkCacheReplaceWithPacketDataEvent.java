/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import net.minecraft.world.level.chunk.LevelChunk;

public final class ClientChunkCacheReplaceWithPacketDataEvent extends CancellableEvent {
   private final LevelChunk chunk;

   public ClientChunkCacheReplaceWithPacketDataEvent(LevelChunk chunk) {
      this.chunk = chunk;
   }

   public LevelChunk getChunk() {
      return this.chunk;
   }
}
