/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.entity;

import dev.zio.client.managers.ServerStateManager;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.inventory.EquipmentUtils;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.utils.world.BlockInteractionUtils;

import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.settings.options.BlockerTargetOffsets;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PlayerStateUtils implements ClientAccess {
   private static final float[] BODY_SAMPLE_HEIGHTS;
   private static final ItemStack ENDER_PEARL_STACK;

   public static Direction getMovementDirection() {
      ClientInput inputSnapshot = ClientAccess.zioMinecraft.player.input;
      float yaw = ClientAccess.zioMinecraft.player.getYRot();

      double[] motion = MovementPhysics.getHorizontalMotion(yaw, inputSnapshot, 1.0D);
      double motionZ = motion[1];
      double motionX = motion[0];
      double angleRadians = Math.atan2(motionZ, motionX);
      double directionYaw = Math.toDegrees(angleRadians)
         - MathConstants.NINETY_DEGREES;
      return Direction.fromYRot(directionYaw);
   }

   public static Vec3 getMovementVector() {
      ClientInput inputSnapshot = ClientAccess.zioMinecraft.player.input;
      float yaw = ClientAccess.zioMinecraft.player.getYRot();
      double[] motion = MovementPhysics.getHorizontalMotion(yaw, inputSnapshot, 1.0D);
      return new Vec3(motion[0], 0.0D, motion[1]);
   }

   public static boolean isLocalHeightBelowOne() {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      AABB bounds = player.getBoundingBox();
      double height = bounds.getYsize();

      return height < 1.0D;
   }

   public static float[] getBodySampleHeights() {
      return BODY_SAMPLE_HEIGHTS;
   }

   public static boolean isHungerLimited() {
      LocalPlayer creativePlayer = ClientAccess.zioMinecraft.player;
      if (creativePlayer.isCreative()) {
         return false;
      }
      LocalPlayer spectatorPlayer = ClientAccess.zioMinecraft.player;
      if (spectatorPlayer.isSpectator()) {
         return false;
      }
      FoodData foodData = ClientAccess.zioMinecraft.player.getFoodData();
      int foodLevel = foodData.getFoodLevel();

      return foodLevel <= 6;
   }

   public static Stream<Vec3> streamBodySamplePositions() {
      Builder<Vec3> positions = Stream.builder();

      for (float height : BODY_SAMPLE_HEIGHTS) {
         LocalPlayer scalePlayer = ClientAccess.zioMinecraft.player;
         Holder<Attribute> scaleAttribute = Attributes.SCALE;
         double scale = scalePlayer.getAttributeValue(scaleAttribute);
         Vec3 positionSnapshot = ClientAccess.zioMinecraft.player.position();
         double scaledHeight = height * scale;
         positions.add(positionSnapshot.add(0.0, scaledHeight, 0.0));
      }

      return positions.build();
   }

   public static BlockPos getLocalRoundedBlockPosition() {
      LocalPlayer playerSnapshot = ClientAccess.zioMinecraft.player;

      return getRoundedBlockPosition(playerSnapshot);
   }

   public static float getTrackedFallDistance() {
      PlayerStateTracker trackerSnapshot = ClientContext.playerStateTracker;

      return trackerSnapshot.getFallDistance();
   }

   private PlayerStateUtils() {
   }

   static {

      float[] heights = new float[3];

      heights[0] = 1.62F;

      heights[1] = 1.27F;

      heights[2] = 0.4F;
      BODY_SAMPLE_HEIGHTS = heights;
      ENDER_PEARL_STACK = new ItemStack(Items.ENDER_PEARL);
   }

   public static boolean hasArmor(LivingEntity entity) {
      if (entity instanceof Player player) {
         for (ItemStack stack : EquipmentUtils.getArmorStacks(player)) {
            if (!stack.isEmpty()) {
               return true;
            }
         }
      }

      return false;
   }

   public static boolean isWearingElytra(Player player) {
      Inventory inventorySnapshot = player.getInventory();
      EquipmentSlot chestSlotSnapshot = EquipmentSlot.CHEST;

      int inventoryIndex = chestSlotSnapshot.getIndex(36);
      ItemStack chestStackSnapshot = inventorySnapshot.getItem(inventoryIndex);
      Item elytra = Items.ELYTRA;
      return chestStackSnapshot.is(elytra);
   }

   public static BlockPos getRoundedBlockPosition(LivingEntity entity) {
      double xSnapshot = entity.getX();
      double y = entity.getY();
      double roundedY = Math.round(y);
      double z = entity.getZ();
      return BlockPos.containing(xSnapshot, roundedY, z);
   }

   public static boolean isInCobweb(Player player) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      AABB bounds = player.getBoundingBox();
      return level.getBlockStates(bounds).anyMatch(state -> {
         Block cobweb = Blocks.COBWEB;
         return state.is(cobweb);
      });
   }

   public static boolean isLocalWearingElytra() {
      LocalPlayer playerSnapshot = ClientAccess.zioMinecraft.player;
      return isWearingElytra(playerSnapshot);
   }

   public static boolean isFallFlying() {
      boolean hookWasActive = ElytraFlyModule.hook;
      if (!hookWasActive) {
         ElytraFlyModule.hook = true;
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      boolean fallFlying = player.isFallFlying();
      if (!hookWasActive) {
         ElytraFlyModule.hook = false;
      }
      return fallFlying;
   }

   public static boolean isLocalLowProfile() {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      return isLowProfile(player);
   }

   public static boolean isEnderPearlOnCooldown() {
      ServerStateManager counterSnapshot = ClientContext.serverStateManager;

      int partialTickCount = counterSnapshot.getLatencyTicks();
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      ItemCooldowns cooldownsSnapshot = player.getCooldowns();
      float partialTick = partialTickCount;
      ItemStack pearlStack = ENDER_PEARL_STACK;
      return cooldownsSnapshot.getCooldownPercent(pearlStack, partialTick) > 0.0;
   }

   public static Set<BlockPos> getOffsetBlockPositions(LivingEntity entity, List<? extends Vec3i> offsets) {
      HashSet<BlockPos> result = new HashSet<>();
      Set<BlockPos> occupiedPositions = getOccupiedBlockPositions(entity);
      for (BlockPos position : occupiedPositions) {
         for (Vec3i offset : offsets) {
            BlockPos candidate = position.offset(offset);
            if (!occupiedPositions.contains(candidate)) {
               result.add(candidate);
            }
         }
      }

      return result;
   }

   public static Set<BlockPos> getSurroundPositions(LivingEntity entity) {
      BlockerTargetOffsets surroundOffsets = BlockerTargetOffsets.SURROUND;
      List<? extends Vec3i> offsets = surroundOffsets.getOffsets();
      return getOffsetBlockPositions(entity, offsets);
   }

   public static boolean isInWater(Player player) {
      float heightOffset;
      if (player.onGround()) {
         heightOffset = 0.0F;
      } else {

         heightOffset = 0.08F;
      }

      double xSnapshot = player.getX();
      double ySnapshot = player.getBoundingBox().minY - heightOffset;
      double z = player.getZ();
      BlockPos blockPositionSnapshot = BlockPos.containing(xSnapshot, ySnapshot, z);

      return BlockInteractionUtils.getBlock(blockPositionSnapshot) == Blocks.WATER || player.isInWater();
   }

   public static boolean isInLiquid(Player player) {

      return isInWater(player) || player.isInLava();
   }

   public static Set<BlockPos> getOccupiedBlockPositions(LivingEntity entity) {

      BlockPos roundedPosition = getRoundedBlockPosition(entity);
      HashSet<BlockPos> positions = new HashSet<>();

      AABB bounds = BoundingBoxUtils.getInterpolationBounds(entity);
      double shrinkAmount = -BoundingBoxUtils.blockCornerInset;
      bounds = bounds.inflate(shrinkAmount);
      double xSnapshot = bounds.minX;
      double ySnapshot = roundedPosition.getY();
      double minZ = bounds.minZ;
      positions.add(BlockPos.containing(xSnapshot, ySnapshot, minZ));
      xSnapshot = bounds.minX;
      ySnapshot = roundedPosition.getY();
      double maxZ = bounds.maxZ;
      positions.add(BlockPos.containing(xSnapshot, ySnapshot, maxZ));
      xSnapshot = bounds.maxX;
      ySnapshot = roundedPosition.getY();
      double secondMinZ = bounds.minZ;
      positions.add(BlockPos.containing(xSnapshot, ySnapshot, secondMinZ));
      xSnapshot = bounds.maxX;
      ySnapshot = roundedPosition.getY();
      double secondMaxZ = bounds.maxZ;
      positions.add(BlockPos.containing(xSnapshot, ySnapshot, secondMaxZ));
      return positions;
   }

   public static boolean isLowProfile(Player player) {
      AABB bounds = player.getBoundingBox();
      double height = bounds.getYsize();

      return height <= 1.0D;
   }

   public static InteractionHand oppositeHand(InteractionHand hand) {
      return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
   }

}
