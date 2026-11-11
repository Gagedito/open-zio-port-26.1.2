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
import dev.zio.client.managers.ServerStateManager;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.world.WorldUtils;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpeedMineRebreakTracker implements ClientAccess {
   private static final long MINIMUM_REBREAK_DELAY_MILLIS = 750L;
   private final SpeedMineModule speedMineModule;
   private final ElapsedTimer rebreakTimer = new ElapsedTimer();
   private boolean tracking;
   private BlockPos pos;

   public void validateTarget() {
      if (this.tracking && this.pos != null) {
         BlockPos trackedPos = this.pos;
         boolean validTarget = false;
         if (WorldUtils.canMineBlock(trackedPos)) {
            BlockPos currentPos = this.pos;
            validTarget = !WorldUtils.isAir(currentPos);
         }
         if (!validTarget) {
            this.reset();
         }

         if (this.pos != null) {
            BlockPos positionToCompare = this.pos;
            SpeedMineModule module = this.speedMineModule;

            if (!positionToCompare.equals(module.getMiningPos())) {
               this.reset();
            }
         }
      }
   }

   public boolean hasRebreakDelayElapsed() {
      if (this.tracking && this.pos != null) {
         double latencyTicks = (ClientContext.serverStateManager.getLatencyMillis() * 15.0F) / 5e+01F;

         long delayMillisScratch = (long)(Math.ceil(latencyTicks) * 50.0D);

         long minimumDelayMillis = MINIMUM_REBREAK_DELAY_MILLIS;
         long latencyDelayMillis = delayMillisScratch;
         delayMillisScratch = Math.max(latencyDelayMillis, minimumDelayMillis);
         float clampedDelayMillis = (float)delayMillisScratch;
         ServerStateManager tickRateTracker = ClientContext.serverStateManager;

         delayMillisScratch = (long)(clampedDelayMillis / tickRateTracker.getTickRateScale());
         float scaledDelayMillis = (float)delayMillisScratch;
         Float instantDelay = this.speedMineModule.instantDelaySetting.getValue();
         delayMillisScratch = (long)(scaledDelayMillis + instantDelay);
         return this.rebreakTimer.hasElapsed(delayMillisScratch);
      } else {
         return false;
      }
   }

   public SpeedMineRebreakTracker(SpeedMineModule module) {
      this.speedMineModule = module;
   }

   public void reset() {
      this.tracking = false;
      this.pos = null;
   }

   public void handleBlockUpdates(
      PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundBlockUpdatePacket blockUpdate) {
         if (blockUpdate.getBlockState().isAir()) {
            if (blockUpdate.getPos().equals(this.pos)) {
               Minecraft minecraft = ClientAccess.zioMinecraft;
               Runnable resetTask = this::reset;
               minecraft.executeIfPossible(resetTask);
            }
         }
      }

      if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
         BiConsumer<BlockPos, BlockState> resetOnAirUpdate = (updatedPos, updatedState) -> {
            if (updatedPos.equals(this.pos)) {
               if (updatedState.isAir()) {
                  Minecraft minecraft = ClientAccess.zioMinecraft;
                  Runnable resetTask = this::reset;
                  minecraft.executeIfPossible(resetTask);
               }
            }
         };
         sectionUpdate.runUpdates(resetOnAirUpdate);
      }

      if (event.getPacket() instanceof ClientboundAddEntityPacket entitySpawn) {
         BlockPos spawnCheckPosition = this.pos;
         if (spawnCheckPosition == null) {
            return;
         }

         AABB trackedBounds = new AABB(spawnCheckPosition);
         Vec3 spawnPosition = new Vec3(entitySpawn.getX(), entitySpawn.getY(), entitySpawn.getZ());
         if (!trackedBounds.contains(spawnPosition)) {
            return;
         }

         Minecraft minecraft = ClientAccess.zioMinecraft;
         Runnable resetTask = this::reset;
         minecraft.execute(resetTask);
      }
   }

   public void startTracking(BlockPos blockPos) {
      if (!this.tracking) {
         this.tracking = true;
         this.pos = blockPos;

         this.rebreakTimer.reset();
      }
   }

}
