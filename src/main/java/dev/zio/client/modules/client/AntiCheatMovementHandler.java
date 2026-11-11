/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.client;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.utils.movement.MovementSpeedTracker;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.AntiCheatRotationsChoice;

import dev.zio.mixin.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public final class AntiCheatMovementHandler implements ClientAccess {
   private final AntiCheatModule module;

   public AntiCheatMovementHandler(AntiCheatModule antiCheatModule) {
      this.module = antiCheatModule;
   }

   public boolean isBaritoneCompatibilityOverrideActive() {
      return false;
   }

   boolean shouldOffsetPacketYaw(ServerboundMovePlayerPacket packet) {

      if (!MovementPhysics.isLocalPlayerMoving()) {
         MovementSpeedTracker speedTracker = ClientContext.movementSpeedTracker;
         double averageSpeed = speedTracker.getAverageSpeed();
         if (averageSpeed < 5.0D) {
            return false;
         }
      }

      Setting<Boolean> twoBTwoTSetting = this.module.value2b2tSetting;
      Boolean twoBTwoTEnabled = twoBTwoTSetting.getValue();
      if (!twoBTwoTEnabled) {
         return false;
      }

      if (PlayerStateUtils.isFallFlying()) {
         return false;
      }

      if (packet != null) {
         boolean inventoryScreenWithPlayerMenu = ClientAccess.zioMinecraft.screen instanceof AbstractContainerScreen
            && ClientAccess.zioMinecraft.player.containerMenu
               == ClientAccess.zioMinecraft.player.inventoryMenu;
         if (!packet.hasRotation() && !inventoryScreenWithPlayerMenu) {
            return false;
         }
      }

      return this.module.movementYawOffsetEnabled
         || packet == null;
   }

   public void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundMovePlayerPacket packet && this.shouldOffsetPacketYaw(packet)) {
         ((ServerboundMovePlayerPacketAccessor)packet).setYaw(packet.getYRot(0.0F) + AntiCheatModule.ROTATION_JITTER_STEP);
      }
   }

   public void prepareMovement() {

      if (this.shouldPrepareMovement()) {
         Setting<AntiCheatRotationsChoice> rotationModeSetting = this.module.rotationsSetting;
         if (rotationModeSetting.getValue() == AntiCheatRotationsChoice.SILENT) {
            LocalPlayer playerForX = ClientAccess.zioMinecraft.player;
            double playerX = playerForX.getX();
            LocalPlayer playerForY = ClientAccess.zioMinecraft.player;
            double playerY = playerForY.getY();
            LocalPlayer playerForZ = ClientAccess.zioMinecraft.player;
            double playerZ = playerForZ.getZ();
            LocalPlayer playerForYaw = ClientAccess.zioMinecraft.player;
            float playerYaw = playerForYaw.getYRot();
            LocalPlayer playerForPitch = ClientAccess.zioMinecraft.player;
            float playerPitch = playerForPitch.getXRot();
            LocalPlayer playerForGround = ClientAccess.zioMinecraft.player;
            boolean capturedOnGround = playerForGround.onGround();
            PacketSender.sendPositionRotation(
               playerX, playerY, playerZ, playerYaw, playerPitch, capturedOnGround
            );
         }
      }
   }

   public boolean shouldPrepareMovement() {
      return this.shouldOffsetPacketYaw(null);
   }

}
