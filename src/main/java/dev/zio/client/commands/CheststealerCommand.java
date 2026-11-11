/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.InventoryStateManager;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.WorldStateManager;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CheststealerCommand extends Command {

   private BlockPos findOpenableEnderChest() {
      WorldStateManager worldState = ClientContext.worldStateManager;
      List blockEntityList = worldState.getBlockEntities();
      Iterator blockEntities = blockEntityList.iterator();
      while (blockEntities.hasNext()) {
         BlockEntity blockEntity = (BlockEntity)blockEntities.next();
         if (blockEntity instanceof EnderChestBlockEntity && this.hasReachAndClearance(blockEntity.getBlockPos())) {
            return blockEntity.getBlockPos();
         }
      }
      return null;
   }

   private BlockPos findPlacementPosition() {
      LocalPlayer playerForEyePosition = ClientAccess.zioMinecraft.player;
      Vec3 eyePosition = playerForEyePosition.getEyePosition();
      Stream<BlockPos> positions = WorldUtils.streamSphereBlocks((eyePosition), 4.0F);
      Predicate<BlockPos> reachable = candidatePosition ->
         WorldUtils.isReplaceable(candidatePosition) && this.hasReachAndClearance(candidatePosition);
      Stream<BlockPos> reachablePositions = positions.filter(reachable);
      Predicate<BlockPos> placeable = this::canPlaceEnderChest;
      Stream<BlockPos> placeablePositions = reachablePositions.filter(placeable);
      Function<BlockPos, Double> distanceFromPlayer = positionForDistance -> {
         LocalPlayer playerForDistance = ClientAccess.zioMinecraft.player;
         Vec3 playerEyePosition = playerForDistance.getEyePosition();
         return (double)(positionForDistance.distToCenterSqr(playerEyePosition));
      };
      Comparator<BlockPos> distanceComparator = Comparator.comparing(distanceFromPlayer);
      Optional<BlockPos> nearestCandidate = placeablePositions.min(distanceComparator);
      return nearestCandidate.orElse(null);
   }

   public CheststealerCommand() {
      super("cheststealer");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      LiteralArgumentBuilder openBuilder = Command.literal("open");
      com.mojang.brigadier.Command<Object> openCommand = context -> {
         var scheduler = ClientContext.taskScheduler;
         Runnable openTask = () -> {
            boolean allowPlacement = true;
            CheststealerCommand command = this;
            command.openEnderChest(allowPlacement);
         };
         scheduler.scheduleRotationTask(openTask, 0);
         return 1;
      };
      ArgumentBuilder openBranch = openBuilder.executes(openCommand);
      builder.then(openBranch);
   }

   private void openEnderChest(boolean allowPlacement) {
      BlockPos existingChest = this.findOpenableEnderChest();
      if (existingChest != null) {
         Direction fallbackDirection = Direction.UP;
         List<Direction> interactionDirections = BlockInteractionUtils.getStrictDirectionsOrFallback(existingChest, fallbackDirection);
         Direction interactionDirection = interactionDirections.getFirst();
         Vec3 chestCenter = existingChest.getCenter();
         Vec3 interactionPoint = chestCenter.relative((interactionDirection), 0.5D);
         InteractionHand handBeforeHitResult = InteractionHand.MAIN_HAND;
         BlockHitResult hitResult = new BlockHitResult(interactionPoint, interactionDirection, existingChest, false);
         PacketSender.sendUseItemOn(handBeforeHitResult, hitResult);
         InteractionHand swingHand = InteractionHand.MAIN_HAND;
         PacketSender.sendSwing(swingHand);
         RotationManager rotationManager = ClientContext.rotationManager;
         float[] openRotations = RotationManager.getRotationsToPosition(interactionPoint);
         float[] rotationsForOpenRequest = openRotations;
         rotationManager.requestRotation(rotationsForOpenRequest, 5000);
      } else {
         if (allowPlacement) {
            int originalSlot = InventoryUtils.getSelectedSlot();
            boolean includeInventory = !InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 1);
            Item enderChestItem = Items.ENDER_CHEST;
            InventoryUtils.SwapSlot enderChestSlot = InventoryUtils.findSwapSlot(
               enderChestItem, includeInventory
            );
            BlockPos placementPosition = this.findPlacementPosition();
            if (placementPosition != null) {
               if (enderChestSlot.isFound()) {
                  boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();
                  BlockPos positionForSupport = placementPosition;
                  Direction supportDirection = BlockInteractionUtils.findSupportDirection(
                     positionForSupport, strictDirection
                  );
                  BlockPos supportPosition = placementPosition.relative((supportDirection));
                  Vec3 supportCenter = supportPosition.getCenter();
                  Direction faceForHitPoint = supportDirection.getOpposite();
                  Vec3 placementHitPoint = supportCenter.relative(faceForHitPoint, 0.5D);
                  InventoryUtils.swapTo(enderChestSlot);
                  InteractionHand placementHand = InteractionHand.MAIN_HAND;
                  boolean airPlace = false;
                  Direction directionForPlacement = supportDirection;
                  Object unspecifiedHitPoint = null;
                  BlockPos positionForPlacement = placementPosition;
                  BlockInteractionUtils.placeBlock(
                     positionForPlacement, (Vec3)unspecifiedHitPoint, directionForPlacement, airPlace, placementHand
                  );
                  InventoryUtils.swapBack((originalSlot), (enderChestSlot));
                  RotationManager rotationManager = ClientContext.rotationManager;
                  Vec3 pointForPlacementRotation = placementHitPoint;
                  float[] placementRotations = RotationManager.getRotationsToPosition(
                     pointForPlacementRotation
                  );
                  float[] rotationsForPlacementRequest = placementRotations;
                  rotationManager.requestRotation(rotationsForPlacementRequest, 5000);
                  var scheduler = ClientContext.taskScheduler;
                  Runnable reopenTask = () -> {
                     boolean allowPlacementOnRetry = false;
                     CheststealerCommand commandForReopen = this;
                     commandForReopen.openEnderChest(allowPlacementOnRetry);
                  };
                  scheduler.scheduleRotationTask(reopenTask, 0);
               }
            }
         }
      }
   }

   private boolean hasReachAndClearance(BlockPos position) {
      LocalPlayer playerForEyePosition = ClientAccess.zioMinecraft.player;
      Vec3 eyePosition = playerForEyePosition.getEyePosition();
      Vec3 blockCenter = position.getCenter();
      double distance = eyePosition.distanceTo(blockCenter);
      if (distance > 4.0D) {
         return false;
      }
      BlockPos abovePosition = position.above();
      BlockPos positionForBlockState = abovePosition;
      ClientLevel levelForBlockState = ClientAccess.zioMinecraft.level;
      var aboveBlockState = levelForBlockState.getBlockState(positionForBlockState);
      ClientLevel levelForCollision = ClientAccess.zioMinecraft.level;
      VoxelShape aboveCollision = aboveBlockState.getCollisionShape(levelForCollision, abovePosition);
      return aboveCollision.isEmpty();
   }

   private boolean canPlaceEnderChest(BlockPos position) {
      boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();
      BlockPos positionForSupport = position;
      if (BlockInteractionUtils.findSupportDirection(positionForSupport, strictDirection) == null) {
         return false;
      }
      return BlockInteractionUtils.canPlaceAt((position), (true), (false));
   }
}
