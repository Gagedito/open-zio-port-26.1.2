/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.LivingEntityRotationEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.FireworkRocketEntityTickEvent;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.modules.movement.ElytraFlyModule;

public abstract class ElytraFlyModeHandler implements ClientAccess {
   protected final ElytraFlyModule module;

   protected ElytraFlyModeHandler(ElytraFlyModule module) {
      this.module = module;
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void handleFireworkTick(FireworkRocketEntityTickEvent event) {
   }

   public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
   }

   public void handleOutgoingPacket(
      PacketEvent.Send event
   ) {
   }

   public void handlePlayerTick(LocalPlayerTickEvent event) {
   }

   public void handleMovement(LocalPlayerMoveEvent event) {
   }

   public void handleTravelRotation(LivingEntityRotationEvent event) {
   }

   public void handleClientTick(MinecraftRunTickEvent event) {
   }

   public void handleRotationManagerEvent(RotationRequestEvent event) {
   }

   public void handleIncomingPacket(
      PacketEvent.Receive event
   ) {
   }

   public void handleSendPosition(LocalPlayerSendPositionEvent event) {
   }
}
