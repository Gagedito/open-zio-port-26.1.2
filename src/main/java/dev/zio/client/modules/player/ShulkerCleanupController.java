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
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.world.WorldUtils;

import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus;
import net.minecraft.world.phys.Vec3;

public class ShulkerCleanupController extends ChestStealerController {
   private boolean wasShulkerScreenOpen;

   public ShulkerCleanupController(ChestStealerModule module) {
      super(module);
   }

   private BlockPos findOpenShulkerToBreak() {
      BlockPos nearestPosition = null;

      double nearestDistance = 999.0D;

      for (Object loadedEntity : WorldUtils.getLoadedBlockEntities()) {
         BlockEntity blockEntity = (BlockEntity)loadedEntity;
         if (blockEntity instanceof ShulkerBoxBlockEntity shulker) {
            if (shulker.getAnimationStatus() != AnimationStatus.CLOSED) {
               Vec3 eyePosition = ClientAccess.zioMinecraft.player.getEyePosition();
               Vec3 shulkerCenter = blockEntity.getBlockPos().getCenter();
               double distance = eyePosition.distanceTo(shulkerCenter);
               if (distance < nearestDistance) {
                  if (distance <= 5.0D) {
                     nearestDistance = distance;
                     nearestPosition = blockEntity.getBlockPos();
                  }
               }
            }
         }
      }

      return nearestPosition;
   }

   public void tick() {
      boolean shulkerScreenOpen = ClientAccess.zioMinecraft.screen instanceof ShulkerBoxScreen;
      if (this.wasShulkerScreenOpen && !shulkerScreenOpen) {
         BlockPos position = this.findOpenShulkerToBreak();
         if (position != null) {
            Direction fallbackDirection = Direction.UP;
            Direction direction = (Direction)BlockInteractionUtils.getStrictDirectionsOrFallback(position, fallbackDirection).getFirst();
            MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
            gameMode.startDestroyBlock(position, direction);
         }
      }

      this.wasShulkerScreenOpen = shulkerScreenOpen;
   }

}
