/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import dev.zio.client.events.EventStage;
import dev.zio.client.utils.entity.PlayerStateTracker;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.utils.network.PacketSender;

import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.InventoryActionEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.exploit.MultiTaskModule;
import dev.zio.client.modules.movement.NoSlowModule;
import dev.zio.client.settings.options.MultiTaskModeChoice;
import dev.zio.client.settings.options.NoSlowModeChoice;

import dev.zio.mixin.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;

public class NoSlowInventoryNcpMode extends NoSlowInventoryMode {
   private static MultiTaskModule multiTask;
   private final ElapsedTimer airborneTimer = new ElapsedTimer();
   private boolean groundSpoofPending;
   private boolean positionOffsetSent;

   public NoSlowInventoryNcpMode(NoSlowModule noSlow) {
      super(noSlow);
   }

   static {
      multiTask = (MultiTaskModule)ClientContext.getModule(MultiTaskModule.class);
   }

   @Override
   public void onInventoryAction(InventoryActionEvent event) {
      Boolean guiMoveEnabled = this.noSlow.guiMoveSetting.getValue();
      if (!guiMoveEnabled) return;
      NoSlowModule guiModule = this.noSlow;
      if (!guiModule.isGuiMoveAllowed()) return;
      if (event.getStage() == EventStage.Pre) {
         boolean releaseItem = true;
         if (multiTask.getMode() == MultiTaskModeChoice.ALL) {
            releaseItem = this.noSlow.modeSetting.getValue() == NoSlowModeChoice.CONSTANTIAM;
         }
         if (releaseItem && !this.noSlow.releasedUsingItemThisTick) {
            MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
            LocalPlayer releasePlayer = ClientAccess.zioMinecraft.player;
            gameMode.releaseUsingItem(releasePlayer);
            this.noSlow.releasedUsingItemThisTick = true;
         }
         if (!MovementPhysics.isLocalPlayerMoving()) return;
         if (ClientContext.playerStateTracker.getGroundedTicks() > 1 && !this.positionOffsetSent) {
            double x = ClientAccess.zioMinecraft.player.getX();
            double offsetY = ClientAccess.zioMinecraft.player.getY() + 0.0626D;
            double z = ClientAccess.zioMinecraft.player.getZ();
            PacketSender.sendPosition(x, offsetY, z, false);
            this.positionOffsetSent = true;
            this.groundSpoofPending = true;
         }
         LocalPlayer sprintPlayer = ClientAccess.zioMinecraft.player;
         if (sprintPlayer.isSprinting()) {
            ServerboundPlayerCommandPacket stopSprinting = new ServerboundPlayerCommandPacket(ClientAccess.zioMinecraft.player, Action.STOP_SPRINTING);
            PacketSender.send(stopSprinting);
         }
         LocalPlayer shiftPlayer = ClientAccess.zioMinecraft.player;
         if (shiftPlayer.isShiftKeyDown()) {
            PlayerStateTracker shiftState = ClientContext.playerStateTracker;
            shiftState.setSneaking(false);
         }
      } else if (event.getStage() == EventStage.Post && MovementPhysics.isLocalPlayerMoving()) {
         LocalPlayer sprintPlayer = ClientAccess.zioMinecraft.player;
         if (sprintPlayer.isSprinting()) {
            ServerboundPlayerCommandPacket startSprinting = new ServerboundPlayerCommandPacket(ClientAccess.zioMinecraft.player, Action.START_SPRINTING);
            PacketSender.send(startSprinting);
         }
         LocalPlayer shiftPlayer = ClientAccess.zioMinecraft.player;
         if (shiftPlayer.isShiftKeyDown()) {
            PlayerStateTracker shiftState = ClientContext.playerStateTracker;
            shiftState.setSneaking(true);
         }
      }
   }

   @Override
   public void onTickStart(LocalPlayerPreTickEvent event) {
      LocalPlayer groundPlayer = ClientAccess.zioMinecraft.player;
      if (!groundPlayer.onGround()) {
         this.airborneTimer.reset();
      }
      this.positionOffsetSent = false;
   }

   @Override
   public void onPacketSend(PacketEvent.Send event) {
      if (!(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) return;
      ServerboundMovePlayerPacketAccessor accessor = (ServerboundMovePlayerPacketAccessor)packet;
      Boolean guiMoveEnabled = this.noSlow.guiMoveSetting.getValue();
      if (!guiMoveEnabled || !this.groundSpoofPending) return;
      if (ClientAccess.zioMinecraft.player.isHandsBusy()) return;
      LocalPlayer groundPlayer = ClientAccess.zioMinecraft.player;
      if (!groundPlayer.onGround()) return;
      ElapsedTimer timer = this.airborneTimer;
      if (!timer.hasElapsed(200L)) return;
      if (ClientAccess.zioMinecraft.player.isFallFlying()) return;
      accessor.setOnGround(false);
      this.groundSpoofPending = false;
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      multiTask = (MultiTaskModule)registry.getModule(
         MultiTaskModule.class
      );
   }
}
