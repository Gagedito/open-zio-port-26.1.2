/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.world.WorldUtils;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.combat.AutoMineMiningTracker;
import dev.zio.client.modules.combat.AutoMineModule;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.BlockSampleMode;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public abstract class AutoMineProviderBase implements ClientAccess, AutoMineProvider {
   private static SpeedMineModule speedMine;
   protected final AutoMineModule autoMine;

   protected boolean hasNoCollisionBelow(Entity entity) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      AABB collisionBounds = entity.getBoundingBox().expandTowards(0.0, -0.1D, 0.0);
      return level.noBlockCollision(entity, collisionBounds);
   }

   public AutoMineProviderBase(AutoMineModule module) {
      this.autoMine = module;
   }

   static {
      speedMine = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
   }

   protected boolean canMinePosition(BlockPos position) {
      AutoMineMiningTracker miningTracker = this.autoMine.miningTracker;
      if (position.equals(miningTracker.getRecentPosition())) {
         return false;
      }
      if (!this.canStartMiningPosition(position)) {
         return false;
      }
      Setting<Boolean> strictDirectionSetting = this.autoMine.strictDirectionSetting;
      if (strictDirectionSetting.getValue()) {
         if (BlockInteractionUtils.getStrictDirections(position).isEmpty()) {
            return false;
         }
      }
      Setting<Boolean> raytraceSetting = this.autoMine.raytraceSetting;
      if (raytraceSetting.getValue()) {
         List<Vec3> raytracePoints = BlockSampleMode.X8.sampleBlock(position);
         if (!WorldUtils.canSeeAny(raytracePoints)) {
            return false;
         }
      }
      if (this.autoMine.hasAssistantBreakProgress(position)) {
         return false;
      }
      return WorldUtils.canMineBlock(position);
   }

   protected boolean canStartMiningPosition(BlockPos position) {
      return speedMine.isInMiningRange(position) && speedMine.isBlockAllowed(position);
   }

   protected boolean isAlreadyMining(BlockPos position) {
      return position != null && (position.equals(speedMine.getMiningPos()) || position.equals(speedMine.getRebreakPos()));
   }

   @Override
   public boolean isEnabled() {
      return false;
   }

   public static void bindModules(ModuleRegistry registry) {
      speedMine = (SpeedMineModule)registry.getModule(SpeedMineModule.class);
   }
}
