/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.utils.movement.MovementPhysics;


public final class VanillaSpeedHandler extends SpeedModeHandler {
   public VanillaSpeedHandler(SpeedModule speedModule) {
      super(speedModule);
   }

   @Override
   public void onMove(LocalPlayerMoveEvent event) {
      if (this.speedModule.shouldPauseInBlocks() || this.speedModule.shouldPauseInLiquid()
         || ClientAccess.zioMinecraft.player.isFallFlying() || this.speedModule.shouldYieldToObstaclePasser()) {
         return;
      }
      double speed = this.speedModule.speedSetting.getValue();
      MovementPhysics.applyHorizontalMotion(event, MovementPhysics.applySpeedEffects(false, speed));
   }
}
