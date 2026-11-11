/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.managers;

import dev.zio.client.modules.misc.SwingModule.HandChoice;

import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.entity.SwingAction;
import dev.zio.client.utils.network.PacketSender;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.CrystalPlacementEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.misc.SwingModule;
import dev.zio.client.settings.Setting;

import dev.zio.mixin.LivingEntityAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

public final class SwingInteractionManager implements ClientAccess {
   private static final AntiCheatModule antiCheat;
   private static final SwingModule swingModule;
   private final List<PendingCrystalPlacement> pendingCrystals;

   public SwingInteractionManager() {
      List<PendingCrystalPlacement> pending = new ArrayList<>(initialCrystalCapacity());
      this.pendingCrystals = Collections.synchronizedList(pending);
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   private static int initialCrystalCapacity() {
      return 10;
   }

   static {
      antiCheat = ClientContext.getModule(AntiCheatModule.class);
      swingModule = ClientContext.getModule(SwingModule.class);
   }

   public boolean tryTrackCrystalPlacement(BlockPos position) {
      if (this.isCrystalPlacementLimited()) {
         return false;
      }

      this.pendingCrystals.add(new PendingCrystalPlacement(position, System.currentTimeMillis() + 300L));
      ClientAccess.eventBus.post(new CrystalPlacementEvent(position));
      return true;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.pendingCrystals.removeIf(pending -> {
         long expiresAt = pending.expiresAtMillis();
         return expiresAt <= System.currentTimeMillis();
      });
   }

   public void swing(
      InteractionHand hand, SwingAction action, boolean packetOnly
   ) {
      AntiCheatModule antiCheatSnapshot = antiCheat;
      if (!action.isEnabled(antiCheatSnapshot)) {
         if (!packetOnly) {
            this.swingClientSide(hand);
         }
      } else {
         if (packetOnly) {
            PacketSender.sendSwing(hand);
         } else {
            LocalPlayer player = ClientAccess.zioMinecraft.player;
            player.swing(hand);
         }
      }
   }

   public void swing(InteractionHand hand, SwingAction action) {
      boolean packetOnly = false;
      this.swing(hand, action, packetOnly);
   }

   public void swingPacket(InteractionHand hand, SwingAction action) {
      boolean packetOnly = true;
      this.swing(hand, action, packetOnly);
   }

   public void swingClientSide(InteractionHand hand) {
      if (ClientAccess.zioMinecraft.player.swinging) {
         int swingTime = ClientAccess.zioMinecraft.player.swingTime;
         int swingDuration = ((LivingEntityAccessor)ClientAccess.zioMinecraft.player)
            .getSwingDuration();

         if (swingTime < swingDuration / 2
            && ClientAccess.zioMinecraft.player.swingTime >= 0) {
            return;
         }
      }

      ClientAccess.zioMinecraft.player.swingTime = -1;
      ClientAccess.zioMinecraft.player.swinging = true;
      SwingModule moduleSnapshot = swingModule;
      if (moduleSnapshot.isToggled()) {
         Setting<HandChoice> handSetting = swingModule.handSetting;
         SwingModule.HandChoice handChoice = handSetting.getValue();
         if (handChoice.getHand() != null) {
            LocalPlayer player = ClientAccess.zioMinecraft.player;
            Setting<HandChoice> overrideSetting = swingModule.handSetting;
            SwingModule.HandChoice overrideChoice = overrideSetting.getValue();
            player.swingingArm = overrideChoice.getHand();
            return;
         }
      }

      ClientAccess.zioMinecraft.player.swingingArm = hand;
   }

   public boolean isCrystalPlacementLimited() {
      AntiCheatModule antiCheatSnapshot = antiCheat;
      if (!antiCheatSnapshot.isPaperPlacement()) {
         return false;
      }

      int pendingCount = this.pendingCrystals.size();

      return pendingCount >= 9;
   }

   @EventHandler
   private void onPacketReceive(
      PacketEvent.Receive event
   ) {
      PacketEvent.Receive packetEvent = event;
      if (packetEvent.getPacket() instanceof ClientboundAddEntityPacket packet) {
         if (packet.getType() == EntityType.END_CRYSTAL) {
            EntityType spawnTypeSnapshot = packet.getType();
            double spawnXSnapshot = packet.getX();
            double spawnYSnapshot = packet.getY();
            double spawnZ = packet.getZ();
            AABB spawnBoundsSnapshot = spawnTypeSnapshot.getSpawnAABB(spawnXSnapshot, spawnYSnapshot, spawnZ);
            double inset = -BoundingBoxUtils.blockCornerInset;
            AABB crystalBounds = spawnBoundsSnapshot.inflate(inset);
            BlockableEventLoop<?> executor = ClientAccess.zioMinecraft;
            Runnable removePlacement = () -> this.pendingCrystals.removeIf(pending -> {
               AABB placementBounds = new AABB(capturePlacementPosition(pending));
               return crystalBounds.intersects(placementBounds);
            });
            executor.execute(removePlacement);
         }
      }
   }

   private static BlockPos capturePlacementPosition(PendingCrystalPlacement pending) {
      return pending.position();
   }

   private record PendingCrystalPlacement(
      BlockPos position,
      long expiresAtMillis
   ) {
   }
}
