/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.managers;

import dev.zio.client.access.LocalPlayerTickAccess;

import dev.zio.client.ClientAccess;

import dev.zio.mixin.LivingEntityAccessor;
import net.minecraft.world.phys.Vec3;

public final class PlayerTickExecutor implements ClientAccess {
   private static boolean runningExtraTick;
   private PlayerTickExecutor() {
   }

   public static boolean isRunningExtraTick() {
      return runningExtraTick;
   }

   public static void runExtraTick() {
      runningExtraTick = true;
      int useItemRemainingTicks = ClientAccess.zioMinecraft.player.getUseItemRemainingTicks();
      float moveDistance = ClientAccess.zioMinecraft.player.moveDist;
      float walkAnimationSpeed = ClientAccess.zioMinecraft.player.walkAnimation.speed();
      float walkDistance = ClientAccess.zioMinecraft.player.avatarState().walkDist;
      float previousWalkDistance = ClientAccess.zioMinecraft.player.avatarState().walkDistO;
      float verticalBob = ClientAccess.zioMinecraft.player.yBob;
      float previousVerticalBob = ClientAccess.zioMinecraft.player.yBobO;
      float horizontalBob = ClientAccess.zioMinecraft.player.xBob;
      float previousHorizontalBob = ClientAccess.zioMinecraft.player.xBobO;
      Vec3 previousPosition = new Vec3(
         ClientAccess.zioMinecraft.player.xo,
         ClientAccess.zioMinecraft.player.yo,
         ClientAccess.zioMinecraft.player.zo
      );
      int attackStrengthTicks = ClientAccess.zioMinecraft.player.attackStrengthTicker;
      int swingTime = ClientAccess.zioMinecraft.player.swingTime;
      float previousAttackAnimation = ClientAccess.zioMinecraft.player.oAttackAnim;
      float attackAnimation = ClientAccess.zioMinecraft.player.attackAnim;
      LocalPlayerTickAccess baseTickPlayer = (LocalPlayerTickAccess)ClientAccess.zioMinecraft.player;
      baseTickPlayer.superTick();
      LocalPlayerTickAccess eventResetPlayer = (LocalPlayerTickAccess)ClientAccess.zioMinecraft.player;
      eventResetPlayer.resetEvent();
      ClientAccess.zioMinecraft.player.attackStrengthTicker = attackStrengthTicks;
      ((LivingEntityAccessor)ClientAccess.zioMinecraft.player)
         .setItemUseTimeLeft(useItemRemainingTicks);
      ClientAccess.zioMinecraft.player.moveDist = moveDistance;
      ClientAccess.zioMinecraft.player.walkAnimation.setSpeed(walkAnimationSpeed);
      ClientAccess.zioMinecraft.player.avatarState().walkDist = walkDistance;
      ClientAccess.zioMinecraft.player.avatarState().walkDistO = previousWalkDistance;
      ClientAccess.zioMinecraft.player.yBob = verticalBob;
      ClientAccess.zioMinecraft.player.yBobO = previousVerticalBob;
      ClientAccess.zioMinecraft.player.xBob = horizontalBob;
      ClientAccess.zioMinecraft.player.xBobO = previousHorizontalBob;
      ClientAccess.zioMinecraft.player.swingTime = swingTime;
      ClientAccess.zioMinecraft.player.oAttackAnim = previousAttackAnimation;
      ClientAccess.zioMinecraft.player.attackAnim = attackAnimation;
      LocalPlayerTickAccess movementPacketPlayer = (LocalPlayerTickAccess)ClientAccess.zioMinecraft.player;
      movementPacketPlayer.sendMovementPacketsWrapper();
      LocalPlayerTickAccess rotationResetPlayer = (LocalPlayerTickAccess)ClientAccess.zioMinecraft.player;
      rotationResetPlayer.resetRotations();
      ClientAccess.zioMinecraft.player.xo = previousPosition.x;
      ClientAccess.zioMinecraft.player.yo = previousPosition.y;
      ClientAccess.zioMinecraft.player.zo = previousPosition.z;
      runningExtraTick = false;
   }

}
