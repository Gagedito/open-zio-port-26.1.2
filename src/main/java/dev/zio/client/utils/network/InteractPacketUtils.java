/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.network;

import dev.zio.client.ClientAccess;
import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;

public final class InteractPacketUtils implements ClientAccess {
   private InteractPacketUtils() {
   }

   public static Entity getEntity(ServerboundInteractPacket packet) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      return level.getEntity(packet.entityId());
   }

   public static Entity getEntity(int entityId) {
      // compat 26.1.2: los ATTACK viajan en ServerboundAttackPacket dedicado
      // (solo entityId), no en el record interact.
      ClientLevel level = ClientAccess.zioMinecraft.level;
      return level.getEntity(entityId);
   }

   public static Action getAction(ServerboundInteractPacket packet) {
      // 26.1.2 turned the packet into a record without an explicit action byte:
      if (packet.hand() == null) {
         return Action.ATTACK;
      }

      return packet.location() != null ? Action.INTERACT_AT : Action.INTERACT;
   }

   public enum Action {
      INTERACT,
      ATTACK,
      INTERACT_AT
   }
}
