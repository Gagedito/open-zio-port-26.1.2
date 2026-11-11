/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.utils.inventory.AutoMineProviderBase;
import dev.zio.client.utils.inventory.AutoMineCandidates;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.settings.Setting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public final class AutoMineFishProvider extends AutoMineProviderBase {
   public AutoMineFishProvider(AutoMineModule module) {
      super(module);
   }

   @Override
   public void collectCandidates(AutoMineCandidates candidates) {
      AutoMineTargetSelector targetSelector = this.autoMine.targetSelector;

      Player target = targetSelector.selectTarget();
      if (target != null) {
         AutoMineTargetSelector boundsSelector = this.autoMine.targetSelector;

         long occupiedCount = BlockPos.betweenClosedStream(boundsSelector.getTargetFootprintBounds(target)).count();
         if (occupiedCount == 1L) {
            BlockPos targetPosition = PlayerStateUtils.getRoundedBlockPosition(target);

            if (!WorldUtils.isAir(targetPosition)) {
               if (!WorldUtils.canMineBlock(targetPosition)) {
                  BlockPos supportPosition = targetPosition.below();

                  if (WorldUtils.canMineBlock(supportPosition)) {
                     candidates.offer(400, output -> {
                        AutoMineModule module = this.autoMine;

                        if (module.isExtraBreakEnabled()) {
                           BlockPos secondaryPosition = targetPosition.below();

                           output.setSecondaryPosition(secondaryPosition);
                        } else {
                           BlockPos primaryPosition = targetPosition.below();

                           output.setPrimaryPosition(primaryPosition);
                        }
                     });
                     return;
                  }
               }
            }
         }
      }
   }

   @Override
   public boolean isEnabled() {
      Setting<Boolean> enabledSetting = this.autoMine.fishSetting;

      return enabledSetting.getValue();
   }
}
