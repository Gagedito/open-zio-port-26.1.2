/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;


import dev.zio.client.ClientAccess;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.Setting;

import dev.zio.mixin.PositionMoveRotationAccessor;
import dev.zio.mixin.ServerboundMovePlayerPacketAccessor;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NoRotateModule extends Module {
   private final Setting<InBlocksChoice> inBlocksSetting;
   private float spoofYaw;
   private float spoofPitch;
   private boolean spoofPending;

   private boolean isInsideBlockingShape() {
      LocalPlayer ladderPlayer = ClientAccess.zioMinecraft.player;
      if (ladderPlayer.isSuppressingSlidingDownLadder()) {
         return false;
      }
      LocalPlayer cobwebPlayer = ClientAccess.zioMinecraft.player;
      if (PlayerStateUtils.isInCobweb(cobwebPlayer)) {
         return false;
      }

      ClientLevel level = ClientAccess.zioMinecraft.level;
      LocalPlayer blockPlayer = ClientAccess.zioMinecraft.player;
      BlockPos blockPosition = blockPlayer.blockPosition();
      BlockState blockState = level.getBlockState(blockPosition);
      if (blockState.getBlock() instanceof FallingBlock) {
         return false;
      }

      ClientLevel collisionLevel = ClientAccess.zioMinecraft.level;
      BlockPos collisionPosition = BlockPos.ZERO;
      VoxelShape collisionShape = blockState.getCollisionShape(collisionLevel, collisionPosition);
      if (collisionShape.isEmpty()) {
         return false;
      }
      LocalPlayer heightPlayer = ClientAccess.zioMinecraft.player;
      double playerY = heightPlayer.getY();
      LocalPlayer floorPlayer = ClientAccess.zioMinecraft.player;
      double floorY = floorPlayer.getY();
      double heightWithinBlock = playerY - Math.floor(floorY);
      if (heightWithinBlock >= collisionShape.bounds().maxY) {
         return false;
      }
      AABB localShapeBounds = collisionShape.bounds();
      LocalPlayer positionPlayer = ClientAccess.zioMinecraft.player;
      BlockPos shapePosition = positionPlayer.blockPosition();
      AABB worldShapeBounds = localShapeBounds.move(shapePosition);
      LocalPlayer boundsPlayer = ClientAccess.zioMinecraft.player;
      AABB playerBounds = boundsPlayer.getBoundingBox();
      AABB inflatedPlayerBounds = playerBounds.inflate(-0.05D);
      return worldShapeBounds.intersects(inflatedPlayerBounds);
   }

   @EventHandler
   private void onPacketSend(
      PacketEvent.Send event
   ) {
      if (event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket
         && this.spoofPending) {
         ServerboundMovePlayerPacketAccessor packetAccessor = (ServerboundMovePlayerPacketAccessor)movementPacket;
         packetAccessor.setYaw(this.spoofYaw);
         packetAccessor.setPitch(this.spoofPitch);
         this.spoofPending = false;
      }

      Setting<InBlocksChoice> inBlocksSettingSnapshot = this.inBlocksSetting;
      if (inBlocksSettingSnapshot.getValue() == NoRotateModule.InBlocksChoice.CANCEL
         && !MovementPhysics.isLocalPlayerMoving()
         && event.getPacket() instanceof ServerboundMovePlayerPacket
         && this.isInsideBlockingShape()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onPacketReceive(
      PacketEvent.Receive event
   ) {
      if (!(event.getPacket() instanceof ClientboundPlayerPositionPacket positionPacket)) {
         return;
      }

      if (this.isWorldUnavailable()) {
         return;
      }
      int playerTicks = ClientAccess.zioMinecraft.player.tickCount;

      if (playerTicks <= (20)) {
         return;
      }
      Minecraft minecraft = ClientAccess.zioMinecraft;
      if (minecraft.isLocalServer()) {
         return;
      }

      LocalPlayer positionPlayer = ClientAccess.zioMinecraft.player;
      PositionMoveRotation playerPosition = PositionMoveRotation.of(positionPlayer);
      PositionMoveRotation serverChange = positionPacket.change();
      Set<Relative> relativeFlags = positionPacket.relatives();
      PositionMoveRotation absolutePosition = PositionMoveRotation.calculateAbsolute(playerPosition, serverChange, relativeFlags);
      LocalPlayer yawPlayer = ClientAccess.zioMinecraft.player;
      float preservedYaw = yawPlayer.getYRot();
      LocalPlayer pitchPlayer = ClientAccess.zioMinecraft.player;
      float preservedPitch = pitchPlayer.getXRot();
      if (positionPacket.relatives().contains(Relative.Y_ROT)) {
         preservedYaw = 0.0F;
      }

      if (positionPacket.relatives().contains(Relative.X_ROT)) {
         preservedPitch = 0.0F;
      }

      ((PositionMoveRotationAccessor)(Object)positionPacket.change()).zio$setYaw(preservedYaw);
      ((PositionMoveRotationAccessor)(Object)positionPacket.change()).zio$setPitch(preservedPitch);
      Setting<InBlocksChoice> inBlocksSettingSnapshot = this.inBlocksSetting;
      if (inBlocksSettingSnapshot.getValue() == NoRotateModule.InBlocksChoice.SPOOF
         || RotationManager.isMovementSyncEnabled()) {
         this.spoofYaw = absolutePosition.yRot();
         this.spoofPitch = absolutePosition.xRot();
         this.spoofPending = true;
      }
   }

   public NoRotateModule() {
      super("NoRotate", "Cancels the rotations server sets for you.", ModuleCategory.MISC, new String[0]);
      NoRotateModule.InBlocksChoice inBlocksDefault = NoRotateModule.InBlocksChoice.OFF;
      this.inBlocksSetting = this.add(new EnumSetting<>("InBlocks", inBlocksDefault));
   }

   enum InBlocksChoice implements NamedEntry {
      OFF("Off"),
      CANCEL("Cancel"),
      SPOOF("Spoof");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      InBlocksChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
