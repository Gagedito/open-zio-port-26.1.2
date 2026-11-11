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
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.zio.client.managers.SwingInteractionManager;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.entity.SwingAction;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.exploit.MultiTaskModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import dev.zio.mixin.MinecraftAccessor;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class FastPlaceModule extends Module {
   private static SpeedMineModule speedMine = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
   private static MultiTaskModule multiTask = (MultiTaskModule)ClientContext.getModule(MultiTaskModule.class);
   public final Setting<Integer> delaySetting;
   private final Setting<Boolean> autoPlaceSetting;
   private final Setting<Boolean> randomBlockSetting;
   private final Setting<Boolean> itemsSetting;
   public final Setting<Boolean> allSetting;
   private final Setting<Boolean> expSetting;
   private final Setting<Boolean> rocketsSetting;
   public final Setting<Boolean> blocksSetting;
   private final Setting<Boolean> foodSetting;
   private final Setting<Boolean> toolsSetting;
   private final Setting<Boolean> enderChestsSetting;
   private final Setting<Boolean> autoReplaceSetting;
   private final Random random;
   private boolean redirectingEnderChestUse;

   @EventHandler
   private void onAutoPlaceTick(LocalPlayerPreTickEvent event) {
      MinecraftAccessor accessor = (MinecraftAccessor)ClientAccess.zioMinecraft;
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      ItemStack foodStack = player.getMainHandItem();
      DataComponentType<?> foodComponent = DataComponents.FOOD;
      if (!foodStack.has(foodComponent)) {
         if ((this.autoPlaceSetting.getValue()) && accessor.getItemUseCooldown() == 0) {
            accessor.interact();
         }
      }
   }

   @EventHandler
   private void onDelayTick(LocalPlayerTickEvent event) {
      if (!this.isWorldUnavailable()) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         ItemStack stack = player.getMainHandItem();
         Item item = stack.getItem();
         if (this.shouldUseFastDelay(stack, item)) {
            MinecraftAccessor accessor = (MinecraftAccessor)ClientAccess.zioMinecraft;
            int cooldown = ((MinecraftAccessor)ClientAccess.zioMinecraft)
               .getItemUseCooldown();
            int delayTicks = this.delaySetting.getValue();
            int currentCooldown = cooldown;
            accessor.setItemUseCooldown(Math.min(currentCooldown, delayTicks));
         }
      }
   }

   @EventHandler
   private void onSendPacket(
      PacketEvent.Send event
   ) {
      if (event.getPacket() instanceof ServerboundUseItemOnPacket useItemPacket) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         InteractionHand hand = useItemPacket.getHand();
         if (player.getItemInHand(hand).getItem() instanceof BlockItem) {
            var eventLoop = ClientAccess.zioMinecraft;
            Runnable task = this::selectRandomBlock;
            eventLoop.execute(task);
         }
      }
   }

   private boolean isRightClickTool(ItemStack stack) {
      TagKey axes = ItemTags.AXES;
      if (stack.is(axes)) {
         return true;
      }
      TagKey shovels = ItemTags.SHOVELS;
      if (stack.is(shovels)) {
         return true;
      }
      TagKey hoes = ItemTags.HOES;
      return stack.is(hoes);
   }

   private void hideItemSettingsWhenAllEnabled(Setting<?>... settings) {
      for (Setting<?> setting : settings) {
         Predicate<Object> visibility = ignored -> {
            Boolean allEnabled = this.allSetting.getValue();
            return !allEnabled;
         };

         setting.addVisibilityPredicate(visibility);
      }
   }

   @EventHandler
   private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      if (!this.redirectingEnderChestUse) {
         LocalPlayer playerForSneaking = ClientAccess.zioMinecraft.player;
         if (!playerForSneaking.isShiftKeyDown()) {
            BlockHitResult hit = event.getHitResult();
            Boolean enderChestsEnabled = this.enderChestsSetting.getValue();
            if (!enderChestsEnabled) {
               Boolean allEnabled = this.allSetting.getValue();
               if (!allEnabled) {
                  return;
               }
            }

            LocalPlayer playerForHeldItem = ClientAccess.zioMinecraft.player;
            Item enderChestItem = Items.ENDER_CHEST;
            if (!playerForHeldItem.isHolding(enderChestItem)) {
               return;
            }

            ClientLevel level = ClientAccess.zioMinecraft.level;
            BlockPos statePosition = hit.getBlockPos();
            BlockState blockState = level.getBlockState(statePosition);
            Block enderChestBlock = Blocks.ENDER_CHEST;
            BlockState targetState = blockState;
            if (!targetState.is(enderChestBlock)) {
               return;
            }

            BlockPos hitBlockPosition = hit.getBlockPos();

            boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();
            BlockPos targetPosition = hitBlockPosition;
            Direction supportDirection = BlockInteractionUtils.findSupportDirection(targetPosition, strictDirection);
            if (supportDirection == null) {
               return;
            }

            BlockPos relativeOrigin = hit.getBlockPos();
            Direction directionForRelativePosition = supportDirection;
            BlockPos supportPosition = relativeOrigin.relative(directionForRelativePosition);
            Vec3 center = supportPosition.getCenter();
            Direction opposite = supportDirection.getOpposite();

            double halfBlock = 0.5D;
            Direction oppositeDirection = opposite;
            Vec3 hitPosition = center.relative(oppositeDirection, halfBlock);
            Direction directionForRedirectedHit = supportDirection;
            BlockHitResult redirectedHit = new BlockHitResult(hitPosition, directionForRedirectedHit.getOpposite(), supportPosition, false);
            ItemStack offhand = ClientAccess.zioMinecraft.player.getOffhandItem();
            Item offhandEnderChest = Items.ENDER_CHEST;
            InteractionHand hand = offhand.is(offhandEnderChest) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

            if (RotationManager.isMovementSyncEnabled()) {
               if (RotationManager.isSilentRotationMode()) {
                  Vec3 rotationTarget = hitPosition;
                  float[] rotations = RotationManager.getRotationsToPosition(
                     rotationTarget);
                  int tickCount = ClientAccess.zioMinecraft.player.tickCount;

                  float yawOffset = (
                        (tickCount % (6)) - (3)
                     )
                     * AntiCheatModule.ROTATION_JITTER_STEP;
                  double playerX = ClientAccess.zioMinecraft.player.getX();
                  double playerY = ClientAccess.zioMinecraft.player.getY();
                  double playerZ = ClientAccess.zioMinecraft.player.getZ();
                  float yaw = rotations[0] + yawOffset;
                  float pitch = rotations[1];
                  boolean packetOnGround = ClientContext.playerStateTracker.isPacketOnGround();
                  PacketSender.sendPositionRotation(playerX, playerY, playerZ, yaw, pitch, packetOnGround);
               }
            }

            this.redirectingEnderChestUse = true;
            MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
            LocalPlayer playerForUse = ClientAccess.zioMinecraft.player;
            gameMode.useItemOn(playerForUse, hand, redirectedHit);
            event.cancel();
            this.redirectingEnderChestUse = false;
            return;
         }
      }
   }

   private boolean shouldUseFastDelay(ItemStack stack, Item item) {
      if (item instanceof SignItem) {
         return false;
      }

      if (this.allSetting.getValue()) {
         return true;
      }

      if (item instanceof ExperienceBottleItem) {
         if (this.expSetting.getValue()) {
            return true;
         }
      }

      if (item instanceof FireworkRocketItem) {
         if (this.rocketsSetting.getValue()) {
            return true;
         }
      }

      if (item instanceof BlockItem) {
         if (this.blocksSetting.getValue()) {
            return true;
         }
      }

      if (item == Items.ENDER_CHEST) {
         if (this.enderChestsSetting.getValue()) {
            return true;
         }
      }

      if (this.isRightClickTool(stack)) {
         if (this.toolsSetting.getValue()) {
            return true;
         }
      }

      DataComponentType<?> consumableComponent = DataComponents.CONSUMABLE;
      if (stack.has(consumableComponent)) {
         if (this.foodSetting.getValue()) {
            return true;
         }
      }

      return false;
   }

   @EventHandler
   private void onRotationRequest(RotationRequestEvent event) {
      if (this.autoReplaceSetting.getValue()) {
         if (this.enderChestsSetting.getValue()) {
            BlockPos miningPosition = speedMine.getMiningPos();

            boolean miningReady = speedMine.getRenderProgress() >= 1.0F;
            if (miningPosition == null) {
               return;
            }

            BlockPos positionForReplaceable = miningPosition;
            if (!WorldUtils.isReplaceable(positionForReplaceable)) {
               BlockPos positionForBlock = miningPosition;
               if (WorldUtils.getBlock(positionForBlock)
                     != Blocks.ENDER_CHEST
                  || !miningReady) {
                  return;
               }
            }

            BlockPos positionForBounds = miningPosition;
            if (BlockInteractionUtils.isOutsidePlacementBounds(positionForBounds)) {
               return;
            }

            Block collisionBlock = Blocks.ENDER_CHEST;
            BlockPos positionForCollision = miningPosition;
            AABB bounds = BoundingBoxUtils.getBlockCollisionBounds(positionForCollision, collisionBlock);
            boolean ignoreCrystals = true;
            boolean playersOnly = false;
            AABB collisionBounds = bounds;
            if (BlockInteractionUtils.hasBlockingEntity(collisionBounds, playersOnly, ignoreCrystals)) {
               return;
            }

            if (multiTask.isAutoReplaceDisabled()) {
               if (multiTask.isItemUseRestricted()) {
                  return;
               }
            }

            int previousSlot = InventoryUtils.getSelectedSlot();

            Item enderChestItem = Items.ENDER_CHEST;
            int enderChestSlot = InventoryUtils.findHotbarSlot(enderChestItem);

            boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();
            BlockPos positionForSupport = miningPosition;
            Direction supportDirection = BlockInteractionUtils.findSupportDirection(positionForSupport, strictDirection);
            if (supportDirection != null && enderChestSlot != -1) {
               int slotToSelect = enderChestSlot;
               InventoryUtils.selectHotbarSlotForPlacement(slotToSelect);
               InteractionHand mainHand = InteractionHand.MAIN_HAND;

               BlockInteractionUtils.sendPlacement(miningPosition, null, supportDirection, false, mainHand);
               SwingAction swingAction = SwingAction.INTERACT;
               InteractionHand swingHand = InteractionHand.MAIN_HAND;
               SwingInteractionManager swingManager = ClientContext.swingManager;
               swingManager.swingPacket(swingHand, swingAction);

               int slotToRestore = previousSlot;
               InventoryUtils.selectHotbarSlotForPlacement(slotToRestore);
               RotationManager managerForRequest = ClientContext.rotationManager;
               Vec3 miningCenter = miningPosition.getCenter();

               double halfBlock = 0.5D;
               Direction directionForOffset = supportDirection;

               Vec3 rotationTarget = miningCenter.relative(directionForOffset, halfBlock);
               float[] rotations = RotationManager.getRotationsToPosition(
                  rotationTarget);

               managerForRequest.requestRotation(rotations, 200);
               return;
            }

            return;
         }
      }
   }

   private void selectRandomBlock() {
      if (this.randomBlockSetting.getValue()) {
         int[] blockSlots = new int[9];
         int blockCount = 0;
         int slot = 0;

         while (true) {
            if (slot >= (9)) {
               if (blockCount < (2)) {
                  return;
               }

               int randomBound = blockCount;
               Random randomSource = this.random;
               int selectedSlot = blockSlots[randomSource.nextInt(randomBound)];

               InventoryUtils.selectHotbarSlot(selectedSlot);
               return;
            }

            LocalPlayer player = ClientAccess.zioMinecraft.player;
            Inventory inventory = player.getInventory();
            int slotForItem = slot;
            ItemStack stack = inventory.getItem(slotForItem);
            ItemStack stackForEmptyCheck = stack;
            if (!stackForEmptyCheck.isEmpty()) {
               if (stack.getItem() instanceof BlockItem) {
                  blockSlots[blockCount++] = slot;
               }
            }

            slot++;
         }
      }
   }

   public static void bindModules(ModuleRegistry registry) {
      speedMine = (SpeedMineModule)registry.getModule(
         SpeedMineModule.class
      );
      multiTask = (MultiTaskModule)registry.getModule(
         MultiTaskModule.class
      );
   }

   public FastPlaceModule() {
      super("FastPlace", "Removes the right-click delay for some items.", ModuleCategory.PLAYER, new String[0]);
      this.delaySetting = this.add(new NumberSetting<>("Delay", 2, 0, 4));
      this.autoPlaceSetting = this.add(new BooleanSetting("AutoPlace", false));
      this.randomBlockSetting = this.add(new BooleanSetting("RandomBlock", false));
      BooleanSetting items = new BooleanSetting("Items", false);
      items.markAsGroup();
      items.markVisibilityDependency();
      this.itemsSetting = this.add(items);
      this.allSetting = this.add(new BooleanSetting("All", false).dependsOn(this.itemsSetting));
      this.expSetting = this.add(new BooleanSetting("Exp", true).dependsOn(this.itemsSetting));
      this.rocketsSetting = this.add(new BooleanSetting("Rockets", false).dependsOn(this.itemsSetting));
      this.blocksSetting = this.add(new BooleanSetting("Blocks", false).dependsOn(this.itemsSetting));
      this.foodSetting = this.add(new BooleanSetting("Food", true).dependsOn(this.itemsSetting));
      this.toolsSetting = this.add(new BooleanSetting("Tools", false).dependsOn(this.itemsSetting));
      BooleanSetting enderChests = new BooleanSetting("EnderChests", true);
      enderChests.markVisibilityDependency();
      this.enderChestsSetting = this.add(enderChests.dependsOn(this.itemsSetting));
      this.autoReplaceSetting = this.add(new BooleanSetting("AutoReplace", false)
         .dependsOn(this.itemsSetting, this.enderChestsSetting));
      this.random = new Random();
      this.redirectingEnderChestUse = false;

      Setting<?>[] itemSettings = new Setting<?>[6];
      itemSettings[0] = this.expSetting;
      itemSettings[1] = this.rocketsSetting;

      itemSettings[2] = this.blocksSetting;

      itemSettings[3] = this.enderChestsSetting;

      itemSettings[4] = this.foodSetting;

      itemSettings[5] = this.toolsSetting;
      this.hideItemSettingsWhenAllEnabled(itemSettings);
      this.setDrawn(false);
   }
}
