/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.events.EventStage;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;

public abstract class SpeedModeHandler implements ClientAccess {
   protected final SpeedModule speedModule;
   protected double moveSpeed;
   protected double lastHorizontalDistance;
   protected int movementStage;

   public void onRotationUpdate() {
   }

   public boolean hasVerticalMotionCollision() {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      AABB bounds = ClientAccess.zioMinecraft.player.getBoundingBox();
      double verticalMotion = ClientAccess.zioMinecraft.player.getDeltaMovement().y;
      return !level.noCollision(bounds.move(0.0, verticalMotion, 0.0));
   }

   protected SpeedModeHandler(SpeedModule speedModule) {
      this.speedModule = speedModule;
   }

   public void onEnable() {
   }

   public void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (EventStage.Post == event.getStage()) {
         return;
      }
      if (!MovementPhysics.isLocalPlayerMoving()) {
         this.movementStage = 4;
         this.moveSpeed = 0.0;
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      double deltaX = player.getX() - player.xo;
      double deltaZ = player.getZ() - player.zo;
      this.lastHorizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
   }

   public abstract void onMove(LocalPlayerMoveEvent event);

}
