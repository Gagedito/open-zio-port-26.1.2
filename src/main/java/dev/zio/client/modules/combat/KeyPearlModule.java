/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.managers.SwingInteractionManager;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.entity.SwingAction;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.combat.RaycastUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public class KeyPearlModule extends Module {
   private static ElytraFlyModule elytrafly;
   private final Setting<Boolean> noEntityTraceSetting;
   private final Setting<Boolean> solidSetting;
   private final Setting<Boolean> disableOnElytraSetting;
   private final Setting<Boolean> disableOnRidingSetting;
   private final Setting<Boolean> awaitSetting;

   static {
      elytrafly = (ElytraFlyModule)ClientContext.getModule(ElytraFlyModule.class);
   }

   @Override
   public void onEnable() {
      if (this.isWorldUnavailable()) {
         this.disable();
      } else {
         if (!this.awaitSetting.getValue()) {
            if (PlayerStateUtils.isEnderPearlOnCooldown()) {
               this.disable();
               return;
            }
         }

         boolean shouldDisable = false;

         if (elytrafly.isToggled()) {
            if (this.disableOnElytraSetting.getValue()) {
               if (!ClientAccess.zioMinecraft.player.onGround()) {
                  shouldDisable = PlayerStateUtils.isWearingElytra(ClientAccess.zioMinecraft.player);
               }
            }
         }

         if (ClientAccess.zioMinecraft.player.isPassenger()) {
            if (this.disableOnRidingSetting.getValue()) {
               shouldDisable = true;
            }
         }

         if (this.disableOnElytraSetting.getValue()) {
            ElapsedTimer timerSnapshot = ClientContext.playerStateTracker.getFallFlyingTimer();

            if (!timerSnapshot.hasElapsed(150L)) {
               shouldDisable = true;
            }
         }

         if (shouldDisable) {
            this.disable();
         }
      }
   }

   private boolean isObstructingBlock(BlockPos position) {
      BlockState state = ClientAccess.zioMinecraft.level.getBlockState(position);
      if (!state.canBeReplaced()) {
         Block cobweb = Blocks.COBWEB;

         if (!state.is(cobweb)) {
            return true;
         }
      }

      return false;
   }

   private void swapPearlSlot(int slot, boolean inventorySwap) {
      if (inventorySwap) {
         InventoryUtils.swapIntoSelectedSlot(slot);
      } else {
         InventoryUtils.selectHotbarSlotForPlacement(slot);
      }
   }

   @EventHandler(priority = 100)
   private void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (event.getStage() == EventStage.Pre) {
         if (this.awaitSetting.getValue()) {
            if (PlayerStateUtils.isEnderPearlOnCooldown()) {
               return;
            }
         }

         HitResult hitResult = RaycastUtils.raycastWithEntityTrace(ClientAccess.zioMinecraft.player);
         Item pearlItemSnapshot = Items.ENDER_PEARL;

         int hotbarPearlSlot = InventoryUtils.findHotbarSlot(pearlItemSnapshot);
         Item inventoryPearlItem = Items.ENDER_PEARL;
         int inventoryPearlSlot = InventoryUtils.findMenuSlot(inventoryPearlItem);

         int previousSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
         boolean entityTraceBlocked = false;

         if (this.noEntityTraceSetting.getValue()) {
            if (hitResult instanceof EntityHitResult entityHit) {
               Entity hitEntity = entityHit.getEntity();

               if (!hitEntity.getBoundingBox().contains(ClientAccess.zioMinecraft.player.getEyePosition())) {
                  entityTraceBlocked = true;
               }
            }
         }

         if (inventoryPearlSlot != -1 && !entityTraceBlocked) {
            if (hitResult instanceof BlockHitResult blockHit) {
               BlockPos blockPositionSnapshot = blockHit.getBlockPos();

               if (this.isObstructingBlock(blockPositionSnapshot)) {
                  if (blockHit.getType() != Type.MISS) {
                     if (!this.solidSetting.getValue()) {
                        this.disable();
                        return;
                     }
                  }
               }
            }

            if (!OffhandModule.isRecentSwapWindowActive()) {
               boolean useInventorySwap = hotbarPearlSlot == -1;
               int selectedPearlSlot = useInventorySwap ? inventoryPearlSlot : hotbarPearlSlot;
               this.swapPearlSlot(selectedPearlSlot, useInventorySwap);
               MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
               LocalPlayer usePlayer = ClientAccess.zioMinecraft.player;
               InteractionHand useHand = InteractionHand.MAIN_HAND;

               gameMode.useItem(usePlayer, useHand);
               SwingInteractionManager swingManager = ClientContext.swingManager;
               InteractionHand swingHand = InteractionHand.MAIN_HAND;
               SwingAction swingMode = SwingAction.INTERACT;

               swingManager.swing(swingHand, swingMode);
               int previousPearlSlot = useInventorySwap ? inventoryPearlSlot : previousSlot;
               this.swapPearlSlot(previousPearlSlot, useInventorySwap);

               ClientContext.moduleRegistry.getPlacementModules().stream().filter(placementModule -> {
                  return placementModule.placementPearlSetting.getValue();
               }).forEach(Module::disable);
               this.disable();
            }
         } else {
            this.disable();
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      elytrafly = (ElytraFlyModule)registry.getModule(ElytraFlyModule.class);
   }

   public KeyPearlModule() {
      super("KeyPearl", "Throws an ender pearl.", ModuleCategory.COMBAT, new String[0]);

      this.noEntityTraceSetting = this.add(new BooleanSetting("NoEntityTrace", true));
      this.solidSetting = this.add(new BooleanSetting("Solid", true));
      this.disableOnElytraSetting = this.add(new BooleanSetting("DisableOnElytra", false));
      this.disableOnRidingSetting = this.add(new BooleanSetting("DisableOnRiding", false));
      this.awaitSetting = this.add(new BooleanSetting("Await", false));

      this.setDrawn(false);
   }
}
