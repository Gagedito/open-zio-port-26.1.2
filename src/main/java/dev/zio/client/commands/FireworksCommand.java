/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.entity.SwingAction;
import dev.zio.client.managers.SwingInteractionManager;
import dev.zio.client.managers.InputOverrideManager;
import dev.zio.client.modules.movement.FireworksModule;
import dev.zio.client.utils.ElapsedTimer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class FireworksCommand extends Command {
   private static final FireworksModule fireworksModule = ClientContext.getModule(FireworksModule.class);
   private int launchTicksRemaining;

   public FireworksCommand() {
      super("fireworks");
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   private void useRocket() {
      int selectedSlot;
      int rocketSlot;
      boolean useInventorySwap;
      rocketSlotSelection: {
         selectedSlot = InventoryUtils.getSelectedSlot();
         Item rocketItem = Items.FIREWORK_ROCKET;
         rocketSlot = InventoryUtils.findHotbarSlot(rocketItem);
         Item inventoryRocketItem = Items.FIREWORK_ROCKET;
         int inventoryRocketSlot = InventoryUtils.findMenuSlot(inventoryRocketItem);
         useInventorySwap = false;
         if (rocketSlot != -1) {
            LocalPlayer usingItemPlayer = ClientAccess.zioMinecraft.player;
            if (!usingItemPlayer.isUsingItem()) {
               break rocketSlotSelection;
            }

            LocalPlayer usedHandPlayer = ClientAccess.zioMinecraft.player;
            if (usedHandPlayer.getUsedItemHand() != InteractionHand.MAIN_HAND) {
               break rocketSlotSelection;
            }
         }

         rocketSlot = inventoryRocketSlot;
         useInventorySwap = true;
      }

      this.withTemporaryRocketSlot(selectedSlot, rocketSlot, useInventorySwap, () -> {
         ElapsedTimer rocketUseTimer = fireworksModule.rocketUseTimer;
         rocketUseTimer.reset();
         MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         InteractionHand useHand = InteractionHand.MAIN_HAND;
         gameMode.useItem(player, useHand);
         SwingInteractionManager swingManager = ClientContext.swingManager;
         InteractionHand swingHand = InteractionHand.MAIN_HAND;
         SwingAction swingAction = SwingAction.INTERACT;
         swingManager.swing(swingHand, swingAction);
      });
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      builder.then(Command.literal("launch").executes(context -> {
         this.launchTicksRemaining = 3;
         return 1;
      }));
   }

   private void withTemporaryRocketSlot(int selectedSlot, int rocketSlot, boolean useInventorySwap, Runnable useRocket) {
      if (rocketSlot != -1) {
         if (useInventorySwap) {
            int menuSlot = InventoryUtils.toMenuSlot(rocketSlot);
            InventoryUtils.swapIntoSelectedSlot(menuSlot);
            useRocket.run();

            menuSlot = InventoryUtils.toMenuSlot(rocketSlot);
            InventoryUtils.swapIntoSelectedSlot(menuSlot);
         } else {
            InventoryUtils.selectHotbarSlot(rocketSlot);
            useRocket.run();
            InventoryUtils.selectHotbarSlot(selectedSlot);
         }
      }
   }

   @EventHandler
   private void onLocalPlayerTick(LocalPlayerPreTickEvent event) {
      if (this.launchTicksRemaining > 0) {
         this.launchTicksRemaining--;
         InputOverrideManager inputOverrides = ClientContext.inputOverrides;
         inputOverrides.requestJump(true);
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         if (!player.onGround()) {
            this.useRocket();
            this.launchTicksRemaining = 0;
         }
      }
   }
}
