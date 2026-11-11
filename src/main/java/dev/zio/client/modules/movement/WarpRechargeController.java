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
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.settings.options.WarpRechargeChoice;
import java.util.LinkedList;
import java.util.Queue;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class WarpRechargeController implements ClientAccess {
   protected final Queue<ServerboundPongPacket> pendingPongs = new LinkedList<>();
   private final WarpModule warp;

   public WarpRechargeController(WarpModule warp) {
      ClientAccess.eventBus.subscribe(this);
      this.warp = warp;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (this.warp.hasDisplacement() || this.warp.modeSetting.getValue() != WarpModule.ModeChoice.PLAIN) {
         return;
      }
      int maximumCharge = this.warp.chargeSetting.getValue();
      int increasedCharge = this.warp.plainCharge + this.warp.chargeSpeedSetting.getValue();
      this.warp.plainCharge = Math.min(maximumCharge, increasedCharge);
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      if (this.isWorldUnavailable()) {
         return;
      }

      if (this.warp.isToggled()
         && this.warp.modeSetting.getValue() == WarpModule.ModeChoice.ALTERNATIVE
         && event.getPacket() instanceof ServerboundPongPacket pong) {
         if (!this.warp.hasMovementInputOrDisplacement()) {
            int pendingCount = this.pendingPongs.size();
            Integer chargeLimit = this.warp.chargeSetting.getValue();
            if (pendingCount < chargeLimit) {
               event.cancel();
               this.pendingPongs.add(pong);
            }
         }

         if (!this.pendingPongs.isEmpty()) {
            event.cancel();
         }
      }

      if (this.warp.modeSetting.getValue() != WarpModule.ModeChoice.PLAIN
         || !(event.getPacket() instanceof ServerboundMovePlayerPacket)) {
         return;
      }

      if (!this.warp.hasDisplacement() || this.warp.plainCharge == 0) {
         return;
      }
      if (!this.warp.isToggled() && this.warp.rechargeSetting.getValue() == WarpRechargeChoice.HOLD) {
         return;
      }

      int decreasedCharge = this.warp.plainCharge - 1;
      this.warp.plainCharge = Math.max(0, decreasedCharge);
   }
}
