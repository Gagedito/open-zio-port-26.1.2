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
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.managers.SwingInteractionManager;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.combat.DamageUtils;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.entity.SwingAction;
import dev.zio.client.utils.inventory.EnchantmentUtils;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.inventory.EquipmentUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.misc.AntiAimModule;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.Iterator;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public class AutoExpModule extends Module {
   public final Setting<ModeChoice> modeSetting;
   private final Setting<Integer> delaySetting;
   private final Setting<Integer> frequencySetting;
   private final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> airSetting;
   private final Setting<Boolean> inventorySetting;
   final Setting<Boolean> stopSetting;
   private final Setting<Integer> stopAtSetting;
   private final Setting<Boolean> autoDisableSetting;
   private final Setting<Boolean> toolsSetting;
   private static AntiAimModule antiAim;
   private static ElytraFlyModule elytraFly;
   private final ElapsedTimer throwTimer;
   private int swappedHotbarSlot;
   private int swappedInventorySlot;
   private int originalSelectedSlot;
   private boolean active;
   public boolean stopped;

   private boolean needsMending(ItemStack stack) {
      ResourceKey mendingEnchantment = Enchantments.MENDING;
      if (!EnchantmentUtils.hasEnchantment(mendingEnchantment, stack)) {
         return false;
      }
      if (!stack.isDamageableItem() || stack.isEmpty()) {
         return false;
      }

      int durabilityPercent = DamageUtils.getDurabilityPercent(stack);
      Integer stopAtValue = this.stopAtSetting.getValue();
      return durabilityPercent < stopAtValue;
   }

   static {
      antiAim = (AntiAimModule)ClientContext.getModule(AntiAimModule.class);
      elytraFly = (ElytraFlyModule)ClientContext.getModule(ElytraFlyModule.class);
   }

   public boolean isActive() {
      return this.active;
   }

   @Override
   public String getInfo() {
      if (this.stopped) {
         return "Stopped";
      } else {
         Predicate<ItemStack> bottleFilter = stack -> {
            Item bottleItem = Items.EXPERIENCE_BOTTLE;

            return stack.is(bottleItem);
         };

         int bottleCount = InventoryUtils.countItems(bottleFilter);
         return String.valueOf(bottleCount);
      }
   }

   @Override
   public void onEnable() {
      if (!this.isWorldUnavailable()) {
         if (this.rotateSetting.getValue()) {
            this.throwTimer.reset();
         }

         if (antiAim.isToggled() && !this.stopped) {
            antiAim.pauseRotations();
         }

         Inventory inventory = ClientAccess.zioMinecraft.player.getInventory();
         this.originalSelectedSlot = inventory.getSelectedSlot();
      }
   }

   @Override
   public void onDisable() {
      this.active = false;
      if (this.isWorldUnavailable()) {
         this.swappedInventorySlot = -1;
      } else {
         restoreInventorySwap(this);

         if (ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot() != this.originalSelectedSlot) {
            if (this.modeSetting.getValue() == AutoExpModule.ModeChoice.NORMAL) {
               InventoryUtils.selectHotbarSlotForPlacement(this.originalSelectedSlot);
            }
         }
      }
   }

   @EventHandler(priority = 250)
   private void updateMending(RotationRequestEvent event) {
      this.active = false;
      performMending(this);
   }

   private static void performMending(AutoExpModule owner) {
      if (!owner.shouldPauseMending()) {
         float[] targetRotation = owner.calculateThrowRotations();
         Item bottleToFind = Items.EXPERIENCE_BOTTLE;

         InventoryUtils.SwapSlot bottleSlot = InventoryUtils.findSwapSlot(
            bottleToFind, false
         );
         LocalPlayer holdingPlayer = ClientAccess.zioMinecraft.player;
         Item bottleItem = Items.EXPERIENCE_BOTTLE;
         boolean holdingBottles = holdingPlayer.isHolding(bottleItem);
         if (!holdingBottles) {
            if (!bottleSlot.isFound()) {
               if (owner.swapBottlesFromInventory()) {
                  holdingBottles = true;
               }
            }
         }

         mendingStopped: {
            if (owner.stopSetting.getValue()) {
               boolean allItemsRepaired = true;

               Iterator<ItemStack> armorItems = EquipmentUtils.getArmorStacks(ClientAccess.zioMinecraft.player).iterator();

               while (armorItems.hasNext()) {
                  ItemStack armorStack = armorItems.next();

                  if (owner.needsMending(armorStack)) {
                     allItemsRepaired = false;
                     break;
                  }
               }

               heldToolsChecked: {
                  if (!owner.needsMending(ClientAccess.zioMinecraft.player.getMainHandItem())) {
                     if (!owner.needsMending(ClientAccess.zioMinecraft.player.getOffhandItem())) {
                        break heldToolsChecked;
                     }
                  }

                  if (owner.toolsSetting.getValue()) {
                     allItemsRepaired = false;
                  }
               }

               if (allItemsRepaired) {
                  break mendingStopped;
               }
               if (!bottleSlot.isFound() && !holdingBottles) {
                  break mendingStopped;
               }
            }
            if (bottleSlot.isFound() || holdingBottles) {
               ElapsedTimer delayTimer = owner.throwTimer;

               int delayTicks = owner.delaySetting.getValue();
               long delayMillis = delayTicks * 50;
               if (delayTimer.hasElapsed(delayMillis)) {
                  owner.stopped = false;

                  int previousSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
                  InventoryUtils.swapTo(bottleSlot);
                  for (int bottlesThrown = 0; bottlesThrown < owner.frequencySetting.getValue(); bottlesThrown++) {
                     if (owner.rotateSetting.getValue()) {
                        InteractionHand throwHand = bottleSlot.hand();
                        float throwYaw = targetRotation[0];
                        float throwPitch = targetRotation[1];
                        PacketSender.sendUseItem(throwHand, throwYaw, throwPitch);
                     } else {
                        InteractionHand throwHandWithoutRotation = bottleSlot.hand();
                        PacketSender.sendUseItem(throwHandWithoutRotation);
                     }
                  }

                  SwingInteractionManager swingManager = ClientContext.swingManager;
                  InteractionHand swingHand = bottleSlot.hand();
                  SwingAction interactionType = SwingAction.INTERACT;

                  swingManager.swingPacket(swingHand, interactionType);

                  if (owner.modeSetting.getValue() == AutoExpModule.ModeChoice.SILENT) {
                     InventoryUtils.swapBack(previousSlot, bottleSlot);
                  }

                  owner.throwTimer.reset();
               }
            }

            if (owner.rotateSetting.getValue()) {
               ClientContext.rotationManager.requestRotation(targetRotation, 400);
            }

            owner.active = true;
            return;
         }

         owner.stopped = true;

         if (owner.autoDisableSetting.getValue()) {
            owner.disable();
         }
      }
   }

   private boolean swapBottlesFromInventory() {
      if (!this.inventorySetting.getValue()) {
         return false;
      }

      Item bottleItem = Items.EXPERIENCE_BOTTLE;

      int bottleInventorySlot = InventoryUtils.findMenuSlot(bottleItem);

      int selectedHotbarSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
      if (bottleInventorySlot == -1) {
         return false;
      }

      ContainerInput clickType = ContainerInput.SWAP;
      InventoryUtils.clickSlot(bottleInventorySlot, selectedHotbarSlot, clickType);
      this.swappedInventorySlot = bottleInventorySlot;
      this.swappedHotbarSlot = selectedHotbarSlot;
      return true;
   }

   private static void restoreInventorySwap(AutoExpModule owner) {
      if (owner.swappedInventorySlot != -1) {
         int inventorySlot = owner.swappedInventorySlot;
         int hotbarSlot = owner.swappedHotbarSlot;
         ContainerInput clickType = ContainerInput.SWAP;
         InventoryUtils.clickSlot(inventorySlot, hotbarSlot, clickType);
         owner.swappedInventorySlot = -1;
         owner.swappedHotbarSlot = -1;
      }
   }

   private boolean shouldPauseMending() {
      if (!this.airSetting.getValue()) {
         HitResult hitResult = ClientAccess.zioMinecraft.hitResult;
         if (hitResult.getType() == Type.MISS) {
            return true;
         }

         if (ClientAccess.zioMinecraft.hitResult instanceof BlockHitResult blockHit) {
            ClientLevel level = ClientAccess.zioMinecraft.level;

            BlockPos position = blockHit.getBlockPos();

            BlockState state = level.getBlockState(position);
            if (state.canBeReplaced()) {
               return true;
            }
         }
      }

      if (!OffhandModule.isRecentSwapWindowActive()) {
         return ClientAccess.zioMinecraft.player.isFallFlying() && !this.canMendDuringFlight();
      } else {
         return true;
      }
   }

   private boolean canMendDuringFlight() {
      if (!this.rotateSetting.getValue()) {
         return false;
      }
      ElytraFlyModule.ModeChoice mode = elytraFly.modeSetting.getValue();
      return elytraFly.isToggled()
         && PlayerStateUtils.isFallFlying() && !MovementPhysics.isLocalPlayerMoving()
         && (mode == ElytraFlyModule.ModeChoice.CONTROL || mode == ElytraFlyModule.ModeChoice.NEWGRIM);
   }

   private float[] calculateThrowRotations() {
      if (this.canMendDuringFlight()) {
         return new float[]{ClientAccess.zioMinecraft.player.getYRot(), -90.0F};
      }
      AABB playerBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(ClientAccess.zioMinecraft.player, 1);
      return RotationManager.getRotationsToPosition(BoundingBoxUtils.getBottomCenter(playerBounds));
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      antiAim = (AntiAimModule)modules.getModule(
         AntiAimModule.class
      );
      elytraFly = (ElytraFlyModule)modules.getModule(ElytraFlyModule.class);
   }

   public AutoExpModule() {
      super("AutoExp", "Mends your armor for you.", ModuleCategory.COMBAT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.SILENT));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 2, 0, 5));
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 16));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", false));
      this.airSetting = this.add(new BooleanSetting("Air", true));
      this.inventorySetting = this.add(new BooleanSetting("Inventory", false));
      this.stopSetting = this.add(new BooleanSetting("Stop", true).markVisibilityDependency());
      Predicate<Object> stopAtVisibility = unused -> {
         return this.stopSetting.isExpanded();
      };
      this.stopAtSetting = this.add(new NumberSetting<>("StopAt", 100, 80, 100, stopAtVisibility).withUnit("%"));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false).dependsOn(this.stopSetting));
      this.toolsSetting = this.add(new BooleanSetting("Tools", false).dependsOn(this.stopSetting));
      this.throwTimer = new ElapsedTimer();
      this.stopped = false;
   }

   public enum ModeChoice implements NamedEntry {
      NORMAL("Normal"),
      SILENT("Silent");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
