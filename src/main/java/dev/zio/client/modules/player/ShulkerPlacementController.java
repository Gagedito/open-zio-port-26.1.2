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
import dev.zio.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.zio.client.managers.TaskScheduler;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.world.BlockInteractionUtils;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ShulkerPlacementController extends ChestStealerController {
   public ShulkerPlacementController(ChestStealerModule module) {
      super(module);
   }

   public void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      InteractionHand hand = event.getHand();
      ItemStack heldStack = player.getItemInHand(hand);
      TagKey shulkerTag = ItemTags.SHULKER_BOXES;
      if (heldStack.is(shulkerTag)) {
         ClientLevel level = ClientAccess.zioMinecraft.level;
         BlockPos clickedPosition = event.getHitResult().getBlockPos();
         Block clickedBlock = level.getBlockState(clickedPosition).getBlock();
         if (BlockInteractionUtils.isInteractiveBlock(clickedBlock)) {
            LocalPlayer sneakingPlayer = ClientAccess.zioMinecraft.player;
            if (!sneakingPlayer.isShiftKeyDown()) {
               return;
            }
         }

         TaskScheduler scheduler = ClientContext.taskScheduler;
         Runnable openPlacedShulker = () -> {
            BlockHitResult placedHit = event.getHitResult();
            BlockPos clickedBlockPosition = placedHit.getBlockPos();
            Direction placementDirection = placedHit.getDirection();
            BlockPos shulkerPosition = clickedBlockPosition.relative(placementDirection);
            Direction fallbackDirection = Direction.UP;
            Direction openDirection = (Direction)BlockInteractionUtils.getStrictDirectionsOrFallback(shulkerPosition, fallbackDirection).getFirst();

            Vec3 hitPosition = shulkerPosition.getCenter().relative(openDirection, 0.5D);
            BlockHitResult openHit = new BlockHitResult(hitPosition, openDirection, shulkerPosition, false);
            InteractionHand openHand = event.getHand();
            PacketSender.sendUseItemOn(openHand, openHit);
            RotationManager rotationManager = ClientContext.rotationManager;

            float[] rotations = RotationManager.getRotationsToBlock(shulkerPosition);
            rotationManager.requestRotation(rotations, 1000);
         };
         scheduler.scheduleRotationTask(openPlacedShulker, 2);
      }
   }
}
