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
import dev.zio.client.ClientContext;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.events.EventStage;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;

public final class OnGroundSpeedHandler extends SpeedModeHandler {
   public OnGroundSpeedHandler(SpeedModule speedModule) {
      super(speedModule);
      this.movementStage = 2;
   }

   @Override
   public void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (EventStage.Post == event.getStage() || this.shouldResetGroundMovement()) {
         return;
      }
      super.onSendPosition(event);
      if (this.movementStage == 3) {
         double offset = this.hasVerticalMotionCollision() ? 0.2D : 0.4D;
         event.setY(event.getY() + offset + MovementPhysics.getJumpBoost());
      }
   }

   @Override
   public void onMove(LocalPlayerMoveEvent event) {
      if (this.shouldResetGroundMovement()) {
         this.speedModule.reset();
         MovementPhysics.applyHorizontalMotion(event, MovementPhysics.getBaseMoveSpeed(true));
         return;
      }
      if (this.movementStage == 4) {
         this.speedModule.reset();
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      if (!player.onGround() && this.movementStage != 3) {
         return;
      }
      if ((!player.horizontalCollision && player.zza != 0.0F) || player.xxa != 0.0F) {
         if (this.movementStage == 2) {
            this.moveSpeed *= 2.149D;
            this.movementStage = 3;
         } else if (this.movementStage == 3) {
            this.movementStage = 2;
            this.moveSpeed = this.lastHorizontalDistance
               - 0.66D * (this.lastHorizontalDistance - MovementPhysics.getBaseMoveSpeed(true));
         }
      }
      this.moveSpeed = Math.min(this.moveSpeed, this.speedModule.speedSetting.getValue());
      this.moveSpeed = Math.max(this.moveSpeed, MovementPhysics.getBaseMoveSpeed(true));
      double[] motion = MovementPhysics.getHorizontalMotion(player.getViewYRot(RenderUtils.getPartialTick()), player.input, this.moveSpeed);
      if (!ClientAccess.zioMinecraft.level.noCollision(player.getBoundingBox().expandTowards(motion[0], 0.0, motion[1]))) {
         this.speedModule.reset();
         this.moveSpeed = MovementPhysics.getBaseMoveSpeed(true);
      }
      MovementPhysics.applyHorizontalMotion(event, this.moveSpeed);
   }

   private boolean shouldResetGroundMovement() {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      ClientLevel level = ClientAccess.zioMinecraft.level;
      AABB bounds = player.getBoundingBox();
      double deltaX = player.getX() - player.xo;
      double deltaY = player.getY() - player.yo;
      double deltaZ = player.getZ() - player.zo;
      if (!level.noCollision(bounds.move(deltaX, deltaY, deltaZ)) || level.noCollision(bounds.expandTowards(0.0, -1.0D, 0.0))) {
         return true;
      }
      if (!player.horizontalCollision && !(player.getY() > Math.floor(player.getY()))
         && ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
         double predictedY = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, 3).minY;
         if (!(predictedY < player.getY())) {
            return false;
         }
      }
      return true;
   }

}
