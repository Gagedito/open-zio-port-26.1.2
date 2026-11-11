/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.SwingInteractionManager;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.entity.SwingAction;
import dev.zio.client.utils.inventory.InventoryUtils;

import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.modules.movement.FireworksModule;
import dev.zio.client.settings.Setting;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class VanillaElytraFlyHandler extends ElytraFlyModeHandler {
   private static AntiCheatModule antiCheatModule;
   private static FireworksModule fireworksModule;
   private final VanillaElytraFlyHandler.FireworkAction fireworkAction = new VanillaElytraFlyHandler.FireworkAction();
   private boolean descendingPhase;
   private boolean fireworkAscentPending;
   private float targetPitch;

   private float computeInitialPitch() {
      if (this.isWorldUnavailable()) {
         return 40.0F;
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      return player.getXRot();
   }

   public VanillaElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void onEnable() {
      this.descendingPhase = true;

      this.targetPitch = this.computeInitialPitch();
   }

   public float getTargetPitch() {
      return this.targetPitch;
   }

   private boolean isFlightActive() {
      return this.module.infDurabilitySetting.getValue().isDisabled()
         ? PlayerStateUtils.isFallFlying()
         : this.module.isDurabilityBypassActive() && !PlayerStateUtils.isInWater(ClientAccess.zioMinecraft.player);
   }

   private boolean isGrimAutoLaunchActive() {
      return fireworksModule.isToggled() && fireworksModule.grimSetting.getValue()
         && fireworksModule.boostSetting.getValue() && fireworksModule.autoLaunchSetting.getValue();
   }

   @Override
   public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
      boolean grimAutoLaunch = this.isGrimAutoLaunchActive();
      if (!this.isFlightActive()) {
         this.targetPitch = this.computeInitialPitch();
      } else {
         if (antiCheatModule.is2b2tEnabled()) {
            if (ClientAccess.zioMinecraft.player.isUsingItem()) {
               return;
            }
         }

         if (!EntityUtils.hasFirework(ClientAccess.zioMinecraft.player) || grimAutoLaunch) {
            if (this.fireworkAction.isUsePending()) {
               this.fireworkAction.run();
            } else {
               if (!this.descendingPhase) {
                  double fireworkHeight = ClientAccess.zioMinecraft.player.getY();
                  Integer fireworkMinimumValue = this.module.minYSetting.getValue();
                  int fireworkMinimumY = fireworkMinimumValue;

                  if (fireworkHeight
                     <= fireworkMinimumY - 10) {
                     Boolean triggerFirework = this.module.useFireworkSetting.getValue();
                     if (triggerFirework) {
                        if (ClientAccess.zioMinecraft.player.getDeltaMovement().y < 0.0) {
                           this.fireworkAscentPending = true;
                        }
                     }
                  }
               }

               double ascentHeight = ClientAccess.zioMinecraft.player.getY();
               if (ascentHeight >= this.getMaximumAltitude()) {
                  this.fireworkAscentPending = false;
               }

               if (this.fireworkAscentPending) {
                  Boolean usePendingFirework = this.module.useFireworkSetting.getValue();
                  if (usePendingFirework && !grimAutoLaunch) {
                     this.fireworkAction.requestUseAfterDelay();
                     return;
                  }
               }

               double descentHeight = ClientAccess.zioMinecraft.player.getY();
               if (descentHeight >= this.getMaximumAltitude()) {
                  this.descendingPhase = true;
               } else {
                  double currentMinimumHeight = ClientAccess.zioMinecraft.player.getY();
                  Integer descentMinimumY = this.module.minYSetting.getValue();
                  if (currentMinimumHeight <= descentMinimumY.intValue() && this.descendingPhase) {
                     this.descendingPhase = false;
                  }
               }

               if (this.descendingPhase) {
                  float previousDescendingPitch = this.targetPitch;

                  this.targetPitch = previousDescendingPitch + (grimAutoLaunch ? 8.0F : 2.0F);
               } else {
                  float previousAscendingPitch = this.targetPitch;

                  this.targetPitch = previousAscendingPitch - 5.45F;
               }

               float unclampedPitch = this.targetPitch;

               this.targetPitch = Mth.clamp(unclampedPitch, -40.0F, 40.0F);
            }
         }
      }
   }

   @Override
   public void handleClientTick(MinecraftRunTickEvent event) {
      if (!this.isWorldUnavailable()) {

         if (this.isFlightActive()) {
            if (event.hasPendingTicks()) {
               if (EntityUtils.hasFirework(ClientAccess.zioMinecraft.player)) {

                  if (this.isGrimAutoLaunchActive()) {
                     ClientAccess.zioMinecraft.player.setXRot(this.targetPitch);
                  } else {
                     this.targetPitch = -75.0F;
                  }
               }

               RotationManager rotationManager = ClientContext.rotationManager;
               float pitch = this.targetPitch;
               Float boxedPitch = pitch;

               int rotationPriority = 999;
               boolean forceRotation = true;
               Float yaw = null;
               rotationManager.requestRotation(yaw, boxedPitch, rotationPriority, forceRotation);
               ClientContext.rotationManager.forceRotationOverride();
            }
         }
      }
   }

   private float getMaximumAltitude() {
      Integer minimumValue = this.module.minYSetting.getValue();
      int minimumY = minimumValue;
      Setting<Integer> heightSetting = this.module.heightSetting;
      Integer height = heightSetting.getValue();
      return minimumY + height;
   }

   public static void bindModuleReferences(ModuleRegistry registry) {
      antiCheatModule = (AntiCheatModule)registry.getModule(
         AntiCheatModule.class
      );
      fireworksModule = (FireworksModule)registry.getModule(FireworksModule.class);
   }

   private static class FireworkAction implements Runnable {
      private final ElapsedTimer lastUseTimer = new ElapsedTimer();
      private boolean usePending;

      @Override
      public void run() {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         Inventory inventory = player.getInventory();
         int selectedSlot = inventory.getSelectedSlot();
         boolean includeInventory = true;
         Item firework = Items.FIREWORK_ROCKET;
         InventoryUtils.SwapSlot fireworkSlot = InventoryUtils.findSwapSlot(
            firework, includeInventory
         );
         if (fireworkSlot.isFound()) {
            InventoryUtils.swapTo(fireworkSlot);
            MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
            LocalPlayer usePlayer = ClientAccess.zioMinecraft.player;
            InteractionHand useHand = fireworkSlot.hand();
            gameMode.useItem(usePlayer, useHand);
            SwingAction swingAction = SwingAction.INTERACT;
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            SwingInteractionManager swingManager = ClientContext.swingManager;
            swingManager.swingPacket(swingHand, swingAction);
            InventoryUtils.swapBack(selectedSlot, fireworkSlot);
            ElapsedTimer useTimer = this.lastUseTimer;

            useTimer.reset();
            this.usePending = false;
         }
      }

      public boolean isUsePending() {
         return this.usePending;
      }

      public void requestUseAfterDelay() {
         ElapsedTimer useTimer = this.lastUseTimer;

         long delayMillis = 250L;
         if (useTimer.hasElapsed(delayMillis)) {
            this.usePending = true;
         }
      }

   }
}
