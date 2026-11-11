/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

import dev.zio.client.render.RenderUtils;

import dev.zio.client.ClientAccess;
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.events.LocalPlayerMoveEvent;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MovementPhysics implements ClientAccess {
   public static float baseMoveSpeed = 0.2873F;
   public static float scaledBaseMoveSpeed = baseMoveSpeed * 0.2F;

   public static float getMovementYaw() {
      return getMovementYaw(ClientAccess.zioMinecraft.player.getYRot());
   }

   public static double getJumpBoost() {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      if (!player.hasEffect(MobEffects.JUMP_BOOST)) {
         return 0.0;
      }
      return (player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1D;
   }

   public static float getMovementYaw(float yaw) {
      double[] motion = getHorizontalMotion(yaw, ClientAccess.zioMinecraft.player.input, 1.0D);
      return (float)(Math.toDegrees(Math.atan2(motion[1], motion[0])) - MathConstants.NINETY_DEGREES);
   }

   public static boolean isMovingToward(Player player, Vec3 target) {
      Vec3 velocity = player.getDeltaMovement();
      if (!isMoving(player)) {
         return false;
      }
      Vec3 forward = player.position().add(velocity.x() * 10000.0D, 0.0, velocity.z() * 10000.0D);
      Vec3 backward = player.position().add(velocity.x() * -10000.0D, 0.0, velocity.z() * -10000.0D);
      return forward.distanceToSqr(target) < backward.distanceToSqr(target);
   }

   public static double applySpeedEffects(boolean includeSlowness, double baseSpeed) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      double speed = baseSpeed;
      if (player.hasEffect(MobEffects.SPEED)) {
         speed *= 1.0D + 0.2D * (player.getEffect(MobEffects.SPEED).getAmplifier() + 1);
      }
      if (includeSlowness && player.hasEffect(MobEffects.SLOWNESS)) {
         speed /= 1.0D + 0.2D * (player.getEffect(MobEffects.SLOWNESS).getAmplifier() + 1);
      }
      return speed;
   }

   public static double[] getHorizontalMotion(float yaw, ClientInput input, double speed) {
      float forward = input.moveVector.y;
      float strafe = input.moveVector.x;
      if (forward != 0.0F) {
         if (strafe > 0.0F) {
            yaw += forward > 0.0F ? -MathConstants.FORTY_FIVE_DEGREES : MathConstants.FORTY_FIVE_DEGREES;
         } else if (strafe < 0.0F) {
            yaw += forward > 0.0F ? MathConstants.FORTY_FIVE_DEGREES : -MathConstants.FORTY_FIVE_DEGREES;
         }
         strafe = 0.0F;
         if (forward > 0.0F) {
            forward = 1.0F;
         } else if (forward < 0.0F) {
            forward = -1.0F;
         }
      }

      double radians = Math.toRadians(yaw);
      double x = forward * speed * -Math.sin(radians) + strafe * speed * Math.cos(radians);
      double z = forward * speed * Math.cos(radians) - strafe * speed * -Math.sin(radians);
      return new double[]{x, z};
   }

   public static double accelerateTo(
      double target, double initial, double durationSeconds, long startedAtMillis
   ) {
      if (initial >= target) {
         return target;
      }

      double elapsedFraction = System.currentTimeMillis() - startedAtMillis;
      elapsedFraction /= durationSeconds * 1000.0D;
      double progress = Mth.clamp(elapsedFraction, 0.0, 1.0D);
      double value = initial + (target - initial) * progress;
      return Math.min(value, target);
   }

   public static double[] applyHorizontalMotion(LocalPlayerMoveEvent event, double speed) {
      if (!isLocalPlayerMoving()) {
         event.setHorizontalVelocity(0.0, 0.0);
         return new double[]{0.0, 0.0};
      }

      LocalPlayer player = ClientAccess.zioMinecraft.player;
      float yaw = player.getViewYRot(RenderUtils.getPartialTick());
      double[] motion = getHorizontalMotion(yaw, player.input, speed);
      event.setHorizontalVelocity(motion[0], motion[1]);
      return motion;
   }

   public static double getHorizontalSpeedKmh(Entity entity) {
      double x = entity.getX() - entity.xo;
      double z = entity.getZ() - entity.zo;
      double speed = Math.hypot(x, z);
      speed *= 20.0D;
      return speed * 3.6D;
   }

   public static double findStepHeight(double maximumHeight) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      if (!player.onGround() || !player.horizontalCollision) {
         return 0.0;
      }
      AABB probeBounds = player.getBoundingBox().move(0.0, 0.05D, 0.0).inflate(0.05D);
      probeBounds = probeBounds.setMaxY(probeBounds.maxY + maximumHeight);
      double height = -1.0D;
      for (VoxelShape shape : ClientAccess.zioMinecraft.level.getCollisions(player, probeBounds)) {
         for (AABB collisionBox : shape.toAabbs()) {
            if (collisionBox.maxY > height) {
               height = collisionBox.maxY;
            }
         }
      }
      height -= player.getY();
      return height > 0.0 && height <= maximumHeight ? height : 0.0;
   }

   public static boolean isLocalPlayerMoving() {
      return isMoving(ClientAccess.zioMinecraft.player);
   }

   public static boolean isMoving(Player player) {
      if (player == null) {
         return false;
      }
      if (player instanceof LocalPlayer localPlayer) {
         Vec2 movement = localPlayer.input.getMoveVector();
         return movement.y != 0.0F || movement.x != 0.0F;
      }
      return player.zza != 0.0F || player.xxa != 0.0F;
   }

   public static boolean isMovingToward(Vec3 target) {
      return isMovingToward(ClientAccess.zioMinecraft.player, target);
   }

   public static double getBaseMoveSpeed(boolean includeSlowness) {
      return applySpeedEffects(includeSlowness, baseMoveSpeed);
   }

}
