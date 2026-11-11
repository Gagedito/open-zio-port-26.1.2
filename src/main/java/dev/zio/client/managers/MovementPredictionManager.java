/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.managers;

import dev.zio.client.utils.movement.PlayerMotionPredictor;

import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.ClientAccess;
import dev.zio.client.events.ClientLevelRemoveEntityEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.MinecraftDisconnectEvent;
import dev.zio.client.events.MinecraftRunTickEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MovementPredictionManager implements ClientAccess {
   private final Map<Integer, PlayerMotionPredictor> playerPredictions = Collections.synchronizedMap(new HashMap<>());
   private final ElapsedTimer predictionTimer = new ElapsedTimer();
   private Vec3 previousPlayerPosition;

   public Vec3 getAdjustedLocalMotion() {
      if (this.previousPlayerPosition == null) {
         return Vec3.ZERO;
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      Vec3 motion = player.position().subtract(this.previousPlayerPosition);
      if (player.isSprinting() && !player.isFallFlying()) {
         motion = motion.multiply((double)0.9F, 1.0D, (double)0.9F);
      }
      return motion;
   }

   public Vec3 getPredictedEyePosition() {
      return ClientAccess.zioMinecraft.player.getEyePosition().add(this.getAdjustedLocalMotion());
   }

   public MovementPredictionManager() {
      ClientAccess.eventBus.subscribe(this);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.previousPlayerPosition = new Vec3(
         ClientAccess.zioMinecraft.player.xo,
         ClientAccess.zioMinecraft.player.yo,
         ClientAccess.zioMinecraft.player.zo
      );
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (this.isWorldUnavailable() || !this.predictionTimer.hasElapsedAndReset(25L)) {
         return;
      }

      for (AbstractClientPlayer player : ClientAccess.zioMinecraft.level.players()) {
         this.playerPredictions.computeIfAbsent(player.getId(), ignored -> new PlayerMotionPredictor(player))
            .updatePredictions();
      }
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      this.playerPredictions.clear();
   }

   public synchronized AABB getPredictedBoundingBox(Player player, int tickIndex) {
      PlayerMotionPredictor prediction = this.playerPredictions.get(player.getId());
      if (prediction == null) {
         return player.getBoundingBox();
      }
      List<AABB> positions = prediction.getPredictedBoxes();
      return positions.isEmpty() ? player.getBoundingBox() : positions.get(Math.min(tickIndex, positions.size() - 1));
   }

   @EventHandler
   private void onClientLevelRemoveEntityEvent(ClientLevelRemoveEntityEvent event) {
      synchronized (this.playerPredictions) {
         this.playerPredictions.remove(event.getId());
      }
   }

}
