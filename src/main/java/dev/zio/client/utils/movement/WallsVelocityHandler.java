/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

import dev.zio.client.access.LocalPlayerTickAccess;
import dev.zio.client.events.EventStage;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;

import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.ClientAccess;
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.events.ClientPacketListenerHandleExplosionEvent;
import dev.zio.client.events.ClientPacketListenerHandleSetEntityMotionEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.movement.FastWebModule;
import dev.zio.client.modules.movement.SprintModule;
import dev.zio.client.modules.movement.VelocityModule;

import dev.zio.mixin.LocalPlayerAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallsVelocityHandler extends VelocityModeHandler {
   private static SprintModule sprintModule;
   private static FastWebModule fastWebModule;
   private final List<Long> recentPositionCorrections = Collections.synchronizedList(new ArrayList<>());
   private final ElapsedTimer correctionCooldown = new ElapsedTimer();
   private boolean groundCollisionPresent;

   private float getAbsolutePositionDeltaSum() {
      double deltaSum = ClientAccess.zioMinecraft.player.getX() - ClientAccess.zioMinecraft.player.xo;
      deltaSum = deltaSum + ClientAccess.zioMinecraft.player.getY() - ClientAccess.zioMinecraft.player.yo;
      deltaSum = deltaSum + ClientAccess.zioMinecraft.player.getZ() - ClientAccess.zioMinecraft.player.zo;
      return (float)Math.abs(deltaSum);
   }

   public static boolean hasRaisedPlayerCollision() {
      return hasPlayerCollisionAtHeight(ClientAccess.zioMinecraft.player.getY() + 0.1D);
   }

   private static boolean allOccupiedBlocksHaveCollision() {
      Set<BlockPos> positions = PlayerStateUtils.getSurroundPositions(ClientAccess.zioMinecraft.player);
      if (positions.size() != 4) {
         return false;
      }
      for (BlockPos position : positions) {
         if (ClientAccess.zioMinecraft.level.getBlockState(position)
            .getCollisionShape(ClientAccess.zioMinecraft.level, position).isEmpty()) {
            return false;
         }
      }
      return true;
   }

   private boolean canApplyWallVelocity() {
      return this.isStationaryStaticMode() || this.groundCollisionPresent;
   }

   public static boolean hasUsableGroundCollision() {
      return !PlayerStateUtils.isFallFlying() && hasRaisedPlayerCollision();
   }

   private boolean isStationaryStaticMode() {
      return this.velocityModule.staticSetting.getValue() && !PlayerStateUtils.isFallFlying()
         && this.getAbsolutePositionDeltaSum() < 0.01D;
   }

   public WallsVelocityHandler(VelocityModule velocityModule) {
      super(velocityModule);
   }

   private void applyYawAndPitch(LocalPlayerSendPositionEvent event) {
      LocalPlayerAccessor access = (LocalPlayerAccessor)ClientAccess.zioMinecraft.player;
      float previousYaw = access.lastYaw();
      boolean moving = MovementPhysics.isLocalPlayerMoving();
      boolean yawWithinThreshold = Mth.degreesDifferenceAbs(event.getYaw(), previousYaw) <= 2.0F;
      if (sprintModule.isToggled() && sprintModule.shouldDeferToVanillaSprinting()
         && !allOccupiedBlocksHaveCollision() && yawWithinThreshold) {
         moving = false;
      }
      if (!moving) {
         if (this.isStationaryStaticMode()) {
            access.zio$setTicksSinceLastPositionPacketSent(Math.min(access.zio$getTicksSinceLastPositionPacketSent(), 18));
         }
         event.setYaw(previousYaw);
      }
      event.setPitch(89.0F);
   }

   @Override
   public void onEntityMotion(
      ClientPacketListenerHandleSetEntityMotionEvent event
   ) {
      int entityId = event.getEntityId();
      LocalPlayer player = ClientAccess.zioMinecraft.player;

      if (entityId == player.getId() && this.shouldCancelVelocity()) {
         Vec3 incomingVelocity = event.getVelocity();
         double verticalSpeed = Math.abs(incomingVelocity.y);
         if (verticalSpeed < 0.0788D && incomingVelocity.x == 0.0 && incomingVelocity.z == 0.0) {
            return;
         }

         event.setVelocity(0.0F, 0.0F, 0.0F);
         event.cancel();
      }
   }

   @Override
   public void onExplosion(
      ClientPacketListenerHandleExplosionEvent event
   ) {
      if (this.shouldCancelVelocity()) {
         event.setVelocityX(0.0F);
         event.setVelocityY(0.0F);
         event.setVelocityZ(0.0F);
      }
   }

   private void applyRandomizedPitch(LocalPlayerSendPositionEvent event) {
      LocalPlayerAccessor access = (LocalPlayerAccessor)ClientAccess.zioMinecraft.player;
      access.zio$setTicksSinceLastPositionPacketSent(20);
      LocalPlayerTickAccess tickAccess = (LocalPlayerTickAccess)ClientAccess.zioMinecraft.player;
      tickAccess.zio$updateServer((float)Math.random(), (float)Math.random());
      float jitter = AntiCheatModule.ROTATION_JITTER_STEP * (ClientAccess.zioMinecraft.player.tickCount % 6 - 3);
      event.setPitch(Mth.clamp(event.getPitch() + jitter, -MathConstants.NINETY_DEGREES, MathConstants.NINETY_DEGREES));
   }

   public boolean shouldCancelVelocity() {
      if (fastWebModule.isToggled() && fastWebModule.isGrimEnabled()
         && PlayerStateUtils.isInCobweb(ClientAccess.zioMinecraft.player)) {
         return true;
      }
      return this.correctionCooldown.hasElapsed(150L) && this.canApplyWallVelocity();
   }

   public static boolean hasCollisionAtHeight(Player player, double height) {
      AABB bounds = player.getBoundingBox();
      bounds = bounds.deflate(BoundingBoxUtils.blockCornerInset, 0.0, BoundingBoxUtils.blockCornerInset);
      bounds = bounds.setMinY(height);
      AABB collisionBounds = bounds.setMaxY(height + 0.1D);
      return BlockPos.betweenClosedStream(collisionBounds).anyMatch(position -> intersectsCollision(collisionBounds, position));
   }

   private static boolean intersectsCollision(AABB bounds, BlockPos position) {
      BlockStateBase state = ClientAccess.zioMinecraft.level.getBlockState(position);
      VoxelShape shape = state.getCollisionShape(ClientAccess.zioMinecraft.level, position);
      return !shape.isEmpty() && bounds.intersects(shape.bounds().move(position));
   }

   @Override
   public void onReceivePacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.recentPositionCorrections.add(System.currentTimeMillis());
      }
   }

   @Override
   public void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (EventStage.Post == event.getStage()
         || ClientAccess.zioMinecraft.screen instanceof AbstractContainerScreen
         || !this.canApplyWallVelocity()
         || !this.velocityModule.reduceSetting.getValue()) {
         return;
      }
      if (RotationManager.isSilentRotationMode()) {
         this.applyRandomizedPitch(event);
      } else {
         this.applyYawAndPitch(event);
      }
   }

   public static boolean hasPlayerCollisionAtHeight(double height) {
      return hasCollisionAtHeight(ClientAccess.zioMinecraft.player, height);
   }

   @Override
   public void onTick() {
      this.groundCollisionPresent = hasUsableGroundCollision();
      this.recentPositionCorrections.removeIf(receivedAt -> System.currentTimeMillis() > receivedAt + 500L);
      if (this.recentPositionCorrections.size() > 2) {
         this.correctionCooldown.reset();
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      sprintModule = (SprintModule)modules.getModule(SprintModule.class);
      fastWebModule = (FastWebModule)modules.getModule(FastWebModule.class);
   }

}
