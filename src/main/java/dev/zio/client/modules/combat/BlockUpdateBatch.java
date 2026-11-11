/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

public final class BlockUpdateBatch implements Iterable<BlockUpdateBatch.Update> {
   private final Iterable<Update> updates;
   private static final String unsupportedPacketMessage;

   public BlockUpdateBatch(Packet packet) {
      this.updates = extractUpdates(packet);
   }

   @Override
   public @NonNull Iterator<Update> iterator() {
      return this.updates.iterator();
   }

   public static boolean isSupportedPacket(Packet packet) {
      return packet instanceof ClientboundSectionBlocksUpdatePacket || packet instanceof ClientboundBlockUpdatePacket;
   }

   private static Iterable<Update> extractUpdates(Packet packet) {
      if (packet instanceof ClientboundBlockUpdatePacket singlePacket) {

         BlockPos singlePosition = singlePacket.getPos();
         BlockPos immutableSinglePosition = singlePosition.immutable();

         Update singleUpdate = new Update(immutableSinglePosition, singlePacket.getBlockState());
         return Collections.singleton(singleUpdate);
      } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
         ArrayList<Update> updates = new ArrayList<>();
         BiConsumer<BlockPos, BlockState> collectUpdate = (position, state) -> {

            Update update = new Update(position.immutable(), state);
            updates.add(update);
         };

         sectionPacket.runUpdates(collectUpdate);
         return updates;
      } else {
         throw new IllegalArgumentException(unsupportedPacketMessage);
      }
   }

   static {
      unsupportedPacketMessage = "packet does not have any block update info";
   }

   public record Update(BlockPos position, BlockState state) {
   }
}
