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
import dev.zio.client.utils.combat.DamageUtils;
import dev.zio.client.utils.entity.EntityUtils;

import java.util.Iterator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;

public class BedBreakScanner implements ClientAccess {
   private final BedAuraModule module;
   private Vec3 eyePosition;
   private float range;
   private float minimumDamage;
   private float maximumSelfDamage;

   private void refreshSettings() {
      LocalPlayer eyePlayer = ClientAccess.zioMinecraft.player;

      this.eyePosition = eyePlayer.getEyePosition();

      Float rangeValue = this.module.breakRangeSetting.getValue();
      this.range = rangeValue;

      Float minimumValue = this.module.minDamageSetting.getValue();
      this.minimumDamage = minimumValue;

      Float maximumValue = this.module.maxSelfDamageSetting.getValue();
      this.maximumSelfDamage = maximumValue;
   }

   public BlockPos scanBreakPosition(LivingEntity target) {
      this.refreshSettings();
      float bestDamage = 0.0F;
      BlockPos bestPosition = null;

      Iterator blockEntities = ClientContext.worldStateManager.getBlockEntities().iterator();

      while (blockEntities.hasNext()) {
         BlockEntity candidate = (BlockEntity)blockEntities.next();
         if (candidate instanceof BedBlockEntity) {
            BlockState stateSnapshot = candidate.getBlockState();
            EnumProperty partProperty = BedBlock.PART;

            if (((BedPart)stateSnapshot.getValue(partProperty)) != BedPart.FOOT) {
               BlockPos rangePosition = candidate.getBlockPos();
               Vec3 rangeCenterSnapshot = rangePosition.getCenter();

               if (!(rangeCenterSnapshot.distanceTo(this.eyePosition) > this.range)) {
                  BlockPos targetPosition = candidate.getBlockPos();
                  Vec3 targetCenterSnapshot = targetPosition.getCenter();
                  LivingEntity damageTarget = target;

                  float targetDamage = DamageUtils.getBedDamage(targetCenterSnapshot, damageTarget);

                  BlockPos selfPosition = candidate.getBlockPos();
                  Vec3 selfCenterSnapshot = selfPosition.getCenter();
                  LocalPlayer selfPlayer = ClientAccess.zioMinecraft.player;

                  float selfDamage = DamageUtils.getBedDamage(selfCenterSnapshot, selfPlayer);

                  if (selfDamage >= EntityUtils.getPlayerHealthWithAbsorption()) {
                     if (this.module.antiSuicideSetting.getValue()) {
                        continue;
                     }
                  }

                  if (!(selfDamage > this.maximumSelfDamage)
                     && !(targetDamage < this.minimumDamage)
                     && (bestPosition == null || targetDamage > bestDamage)) {
                     bestPosition = candidate.getBlockPos();
                     bestDamage = targetDamage;
                  }
               }
            }
         }
      }

      return bestPosition;
   }

   public BedBreakScanner(BedAuraModule module) {
      this.module = module;
   }
}
