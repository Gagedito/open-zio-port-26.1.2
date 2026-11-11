/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.world;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.ClientChunkCacheReplaceWithPacketDataEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public class ChunkScanner implements ClientAccess {
   private final int workerCount;
   private final BiConsumer chunkLoadCallback;
   private final BiConsumer chunkUnloadCallback;
   private final BiConsumer blockUpdateCallback;
   private ExecutorService executor;

   public void start() {
      int requestedWorkers = this.workerCount;

      this.executor = createExecutor(requestedWorkers);
      ClientAccess.eventBus.subscribe(this);
   }

   public void scanLoadedChunks() {
      if (this.chunkLoadCallback != null) {

         Iterator chunks = WorldUtils.getLoadedChunks().iterator();

         while (chunks.hasNext()) {
            LevelChunk chunk = (LevelChunk)chunks.next();
            this.dispatch(() -> {
               this.chunkLoadCallback.accept(chunk.getPos(), chunk);
            });
         }
      }
   }

   public void restartExecutor() {
      this.executor.shutdownNow();
      int requestedWorkers = this.workerCount;

      this.executor = createExecutor(requestedWorkers);
   }

   public ChunkScanner(int workerCount, BiConsumer chunkLoadCallback, BiConsumer chunkUnloadCallback, BiConsumer blockUpdateCallback) {
      this.workerCount = workerCount;
      this.chunkLoadCallback = chunkLoadCallback;
      this.chunkUnloadCallback = chunkUnloadCallback;
      this.blockUpdateCallback = blockUpdateCallback;
   }

   public ChunkScanner(int workerCount, ChunkScanner.Listener listener) {
      this(
         workerCount,
         (java.util.function.BiConsumer<net.minecraft.world.level.ChunkPos, net.minecraft.world.level.chunk.LevelChunk>)listener::onChunkLoaded,
         (java.util.function.BiConsumer<net.minecraft.world.level.ChunkPos, net.minecraft.world.level.chunk.LevelChunk>)listener::onChunkUnloaded,
         (java.util.function.BiConsumer<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>)listener::onBlockUpdated
      );
   }

   public ExecutorService getExecutor() {
      return this.executor;
   }

   @EventHandler
   private void onClientChunkCacheReplaceWithPacketDataEvent(
      ClientChunkCacheReplaceWithPacketDataEvent event
   ) {
      if (this.chunkLoadCallback != null) {
         this.dispatch(
            () -> {
               BiConsumer loadCallback = this.chunkLoadCallback;
               LevelChunk chunkForPosition = event.getChunk();
               ChunkPos chunkPosition = chunkForPosition.getPos();
               loadCallback.accept(chunkPosition, event.getChunk());
            }
         );
      }
   }

   @EventHandler
   public void onPacketReceive(
      PacketEvent.Receive event
   ) {
      net.minecraft.world.level.ChunkPos extractedChunkPosition;
      if (ClientAccess.zioMinecraft.level != null) {
         if (this.blockUpdateCallback != null) {
            if (event.getPacket() instanceof ClientboundBlockUpdatePacket blockPacket) {
               this.dispatch(() -> {
                  BiConsumer updateCallback = this.blockUpdateCallback;
                  BlockPos blockPosition = blockPacket.getPos();
                  updateCallback.accept(blockPosition, blockPacket.getBlockState());
               });
               return;
            }
         }

         if (this.blockUpdateCallback != null) {
            if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
               this.dispatch(() -> sectionPacket.runUpdates((position, state) -> {
                  BiConsumer updateCallback = this.blockUpdateCallback;
                  updateCallback.accept(position.immutable(), state);
               }));
               return;
            }
         }

         if (this.chunkUnloadCallback != null) {
            if (event.getPacket() instanceof ClientboundForgetLevelChunkPacket forgetPacket) {

               try {
                  extractedChunkPosition = forgetPacket.pos();
               } catch (Throwable extractionFailure) {
                  throw new MatchException(extractionFailure.toString(), extractionFailure);
               }

               ChunkPos copiedChunkPosition = new ChunkPos(extractedChunkPosition.x(), extractedChunkPosition.z());
               int chunkZ = copiedChunkPosition.z();
               int chunkX = copiedChunkPosition.x();
               ClientLevel level = ClientAccess.zioMinecraft.level;
               LevelChunk chunk = level.getChunk(chunkX, chunkZ);
               this.dispatch(() -> this.chunkUnloadCallback.accept(copiedChunkPosition, chunk));
            }
         }
      }
   }

   private void dispatch(Runnable action) {
      if (this.workerCount == 0) {
         action.run();
      } else {
         this.executor.execute(action);
      }
   }

   public void stop() {
      ClientAccess.eventBus.unsubscribe(this);
      this.executor.shutdownNow();
      this.executor = null;
   }

   private static ExecutorService createExecutor(int requestedWorkers) {
      int workers = Math.max(requestedWorkers, 1);
      return Executors.newFixedThreadPool(workers);
   }

   public interface Listener {
      void onChunkLoaded(ChunkPos chunkPosition, LevelChunk chunk);

      void onChunkUnloaded(ChunkPos chunkPosition, LevelChunk chunk);

      void onBlockUpdated(BlockPos position, BlockState state);
   }
}
