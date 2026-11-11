/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.movement;

import dev.zio.client.events.EventStage;
import dev.zio.client.utils.world.WorldUtils;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.commands.FakeplayerCommand;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.movement.SpeedModeHandler;
import dev.zio.client.modules.movement.SpeedModule;

import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GrimSpeedHandler extends SpeedModeHandler {
   private static AntiCheatModule antiCheatModule;
   private AABB lastPlayerBounds;

   public GrimSpeedHandler(SpeedModule speedModule) {
      super(speedModule);
   }

   static {
      antiCheatModule = ClientContext.getModule(AntiCheatModule.class);
   }

   private boolean intersectsNearbyEntity(Entity entity) {
      boolean strictBounds = antiCheatModule.is2b2tEnabled();
      AABB entityBounds = entity.getBoundingBox();
      AABB playerBounds = this.lastPlayerBounds;
      AABB expandedPlayerBounds = strictBounds
         ? playerBounds.inflate((double)0.4F, (double)0.2F, (double)0.4F)
         : playerBounds.inflate(1.0D);
      return entityBounds.intersects(expandedPlayerBounds);
   }

   @Override
   public void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (EventStage.Post == event.getStage()) {
         this.lastPlayerBounds = ClientAccess.zioMinecraft.player.getBoundingBox();
      }
   }

   @Override
   public void onMove(LocalPlayerMoveEvent event) {
      if (this.lastPlayerBounds == null || !MovementPhysics.isLocalPlayerMoving()
         || !ClientContext.serverStateManager.hasElapsedSinceSetback(100L)) {
         return;
      }
      int collisionWeight = 0;
      for (BlockEntity blockEntity : WorldUtils.getLoadedBlockEntities()) {
         if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBox) {
            AABB expandedPlayerBounds = this.lastPlayerBounds.inflate(0.5D);
            AABB shulkerBounds = shulkerBox.getBoundingBox(Blocks.SHULKER_BOX.defaultBlockState())
               .move(blockEntity.getBlockPos());
            if (expandedPlayerBounds.intersects(shulkerBounds)
               && shulkerBox.getAnimationStatus() != AnimationStatus.CLOSED
               && !antiCheatModule.is2b2tEnabled()) {
               collisionWeight += 4;
            }
         }
      }
      float collisionBoost = antiCheatModule.is2b2tEnabled() ? 0.06F : 0.08F;
      for (Entity entity : ClientAccess.zioMinecraft.level.entitiesForRendering()) {
         if (!(entity instanceof FakeplayerCommand.SimulatedPlayer)
            && entity != ClientAccess.zioMinecraft.player
            && entity.isPushable() && this.intersectsNearbyEntity(entity)) {
            if (entity instanceof Boat) {
               collisionWeight += 4;
            }
            collisionWeight++;
         }
      }
      if (collisionWeight != 0) {

         Vec3 horizontalDirection = event.getVelocity().normalize().with(Axis.Y, 0.0D);
         Vec3 velocity = event.getVelocity();
         float boost = collisionBoost * collisionWeight;
         event.setVelocity(velocity.add(horizontalDirection.scale(boost)));
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      antiCheatModule = (AntiCheatModule)modules.getModule(
         AntiCheatModule.class
      );
   }
}
