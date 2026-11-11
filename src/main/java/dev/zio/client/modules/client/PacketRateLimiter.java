/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.client;

import dev.zio.client.ClientContext;
import dev.zio.client.utils.network.ImportantPacket;
import dev.zio.client.managers.RotationManager;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

public final class PacketRateLimiter {
   private final Lock lock = new ReentrantLock();
   private final PacketWindow global;
   private final PacketWindow interacts;
   private final List<PacketWindow> windows;


   public PacketRateLimiter() {
      this.global = PacketWindow.create("Global", (280), (320), PacketRateLimiter::isGlobalPacket);
      this.interacts = PacketWindow.create("Interacts", (90), (100), PacketRateLimiter::isInteractPacket);
      this.windows = List.of(this.global, this.interacts);
   }

   PacketWindow getGlobalWindow() {
      return this.global;
   }

   PacketWindow getInteractWindow() {
      return this.interacts;
   }

   int countRecentPackets(PacketWindow window) {
      this.lock.lock();
      try {
         return window.countRecentPackets(System.currentTimeMillis());
      } finally {
         this.lock.unlock();
      }
   }

   boolean isLimitReached(PacketWindow window) {
      return this.countRecentPackets(window) >= window.limit();
   }

   public boolean shouldCancelPacket(Packet packet) {
      if (packet instanceof ServerboundMovePlayerPacket
         || packet instanceof ServerboundKeepAlivePacket
         || packet instanceof ServerboundAcceptTeleportationPacket
         || packet instanceof ServerboundContainerClosePacket) {
         return false;
      }
      RotationManager rotationManager = ClientContext.rotationManager;
      if (rotationManager.isExecutingAction()) {
         return false;
      }
      this.lock.lock();
      try {
         long now = System.currentTimeMillis();
         if (packet instanceof ServerboundSwingPacket && this.interacts.countRecentPackets(now) >= this.interacts.limit()) {
            return true;
         }
         for (PacketWindow window : this.windows) {
            if (window.predicate().test(packet)
               && window.countRecentPackets(now) >= this.limitForPacket(window, packet)) {
               return true;
            }
         }
         for (PacketWindow window : this.windows) {
            if (window.predicate().test(packet)) {
               window.timestamps().addLast(now);
            }
         }
         return false;
      } finally {
         this.lock.unlock();
      }
   }

   private int limitForPacket(PacketWindow window, Packet packet) {
      return this.isImportantPacket(packet) ? window.importantLimit() : window.limit();
   }

   private boolean isImportantPacket(Packet packet) {
      if (packet instanceof ServerboundPlayerActionPacket actionPacket) {
         if (actionPacket.getAction() == Action.ABORT_DESTROY_BLOCK
            || actionPacket.getAction() == Action.START_DESTROY_BLOCK) {
            return true;
         }
         if (actionPacket.getAction() == Action.SWAP_ITEM_WITH_OFFHAND) {
            return true;
         }
      }
      if (packet instanceof ServerboundPongPacket) {
         return true;
      }
      return packet instanceof ImportantPacket importantPacket && importantPacket.zio$isImportant();
   }

   private static boolean isInteractPacket(Packet packet) {
      return packet instanceof ServerboundInteractPacket
         || packet instanceof ServerboundAttackPacket
         || packet instanceof ServerboundPlayerActionPacket;
   }

   private static boolean isGlobalPacket(Packet packet) {
      return packet instanceof ServerboundInteractPacket
         || packet instanceof ServerboundAttackPacket
         || packet instanceof ServerboundUseItemPacket
         || packet instanceof ServerboundUseItemOnPacket
         || packet instanceof ServerboundPlayerActionPacket
         || packet instanceof ServerboundPongPacket;
   }

   record PacketWindow(String name, int limit, int importantLimit, Predicate<Packet> predicate, Deque<Long> timestamps) {

      static PacketWindow create(String name, int limit, int importantLimit, Predicate<Packet> predicate) {
         return new PacketWindow(name, limit, importantLimit, predicate, new ArrayDeque<>());
      }

      int countRecentPackets(long now) {
         while (!this.timestamps.isEmpty()) {
            long ageMillis = now - this.timestamps.peekFirst();
            if (ageMillis <= 1000L) {
               break;
            }
            this.timestamps.pollFirst();
         }
         return this.timestamps.size();
      }
   }
}
