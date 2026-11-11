/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MultiPlayerGameModeStartDestroyBlockEvent;
import dev.zio.client.utils.world.BlockBreakingState;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class AutoToolModule extends Module {
   private static AntiCheatModule antiCheatModule;
   private static NukerModule nukerModule;
   public final Setting<Float> blockDamageSetting;
   private final Setting<Boolean> inventorySetting;
   private final Setting<Boolean> swapBackSetting;
   public final Setting<Boolean> noBreakDelaySetting;
   private int swapBackSlot;
   private boolean swapBackPending;
   private boolean inventorySwapActive;

   public boolean isBlockDamageOverrideActive() {
      if (!this.isToggled()) {
         return false;
      }

      Setting<Float> blockDamage = this.blockDamageSetting;
      return !blockDamage.isAtMaximum();
   }

   static {
      antiCheatModule = (AntiCheatModule)ClientContext.getModule(AntiCheatModule.class);
      nukerModule = (NukerModule)ClientContext.getModule(NukerModule.class);
   }

   @EventHandler
   private void onMultiPlayerGameModeStartDestroyBlockEvent(
      MultiPlayerGameModeStartDestroyBlockEvent event
   ) {
      if (nukerModule.isToggled()) {
         NukerModule activeNuker = nukerModule;
         BlockPos nukerPos = event.getPos();
         if (activeNuker.canMineBlock(nukerPos)) {
            return;
         }
      }

      if (!this.isBlockDamageOverrideActive()) {
         return;
      }

      this.onLocalPlayerPreTickEvent(null);
      BlockPos blockPos = event.getPos();
      Direction direction = event.getDirection();
      ClientLevel level = ClientAccess.zioMinecraft.level;
      BlockState blockState = level.getBlockState(blockPos);
      ItemStack heldItem = ClientAccess.zioMinecraft.player.getMainHandItem();
      boolean onGround = ClientAccess.zioMinecraft.player.onGround();
      double blockDamage = WorldUtils.getBreakProgress(heldItem, blockState, onGround);
      Setting<Float> blockDamageSetting = this.blockDamageSetting;
      Float threshold = blockDamageSetting.getValue();
      if (blockDamage < threshold.floatValue()) {
         return;
      }
      if (event.isCancelled()) {
         return;
      }

      if (!WorldUtils.canMineBlock(blockPos)) {
         return;
      }

      Action startAction = Action.START_DESTROY_BLOCK;
      PacketSender.sendPlayerAction(startAction, blockPos, direction);
      Action stopAction = Action.STOP_DESTROY_BLOCK;
      PacketSender.sendPlayerAction(stopAction, blockPos, direction);
      if (antiCheatModule.is2b2tEnabled()) {
         Action abortAction = Action.ABORT_DESTROY_BLOCK;
         PacketSender.sendPlayerAction(abortAction, blockPos, direction);
      }

      ClientContext.worldStateManager.destroyBlockPredicted(blockPos);
      event.cancel();
   }

   @EventHandler
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent tickEvent) {
      BlockPos breakingPos = ((BlockBreakingState)ClientAccess.zioMinecraft.gameMode).getCurrentBreakingBlock();
      if (tickEvent == null) {
         HitResult hitResult = ClientAccess.zioMinecraft.hitResult;
         if (hitResult instanceof BlockHitResult blockHit) {
            breakingPos = blockHit.getBlockPos();
         }
      }

      if (breakingPos != null && ClientAccess.zioMinecraft.options.keyAttack.isDown()) {
         this.swapBackPending = true;
         Boolean inventorySearch = this.inventorySetting.getValue();
         boolean hotbarOnly = !inventorySearch;
         int bestToolSlot = InventoryUtils.findBestToolSlot(breakingPos, hotbarOnly);
         if (this.inventorySetting.getValue()) {
            if (bestToolSlot > (9) && !this.inventorySwapActive) {
               this.swapBackSlot = bestToolSlot;
               InventoryUtils.swapIntoSelectedSlot(bestToolSlot);
               this.inventorySwapActive = true;
               return;
            }
         }

         if (!this.inventorySwapActive) {
            InventoryUtils.selectHotbarSlotForPlacement(bestToolSlot);
         }
         return;
      }

      Setting<Boolean> swapBackSetting = this.swapBackSetting;
      Boolean swapBack = swapBackSetting.getValue();
      if (!swapBack) {
         return;
      }
      if (this.swapBackPending) {
         this.swapBackPending = false;
         if (this.inventorySwapActive) {
            int restoreSlot = this.swapBackSlot;
            InventoryUtils.swapIntoSelectedSlot(restoreSlot);
            this.inventorySwapActive = false;
            return;
         }

         Inventory restoreInventory = ClientAccess.zioMinecraft.player.getInventory();
         int restoreSlot = this.swapBackSlot;
         restoreInventory.setSelectedSlot(restoreSlot);
      } else if (!this.inventorySwapActive) {
         this.swapBackSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
      }
   }

   public static void bindModules(ModuleRegistry moduleRegistry) {
      antiCheatModule = (AntiCheatModule)moduleRegistry.getModule(
         AntiCheatModule.class
      );
      nukerModule = (NukerModule)moduleRegistry.getModule(
         NukerModule.class
      );
   }

   public AutoToolModule() {
      super("AutoTool", "Equips the best tool to use on a block for you.", ModuleCategory.PLAYER, new String[0]);
      this.blockDamageSetting = this.add(new NumberSetting<>("BlockDamage", 1.0F, 0.7F, 1.0F));
      this.inventorySetting = this.add(new BooleanSetting("Inventory", false));
      this.swapBackSetting = this.add(new BooleanSetting("SwapBack", false));
      this.noBreakDelaySetting = this.add(new BooleanSetting("NoBreakDelay", false));
      Setting<Float> vanillaBlockDamageSetting = this.blockDamageSetting;

      SettingLabelCondition maximumValueMarker = SettingLabelCondition.MAX;

      vanillaBlockDamageSetting.setBoundaryLabel("Vanilla", maximumValueMarker);
   }

}
