/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.InventoryActionEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import dev.zio.client.modules.movement.NoSlowModule;

public class NoSlowInventoryMode implements ClientAccess {
   protected final NoSlowModule noSlow;

   public NoSlowInventoryMode(NoSlowModule noSlow) {
      this.noSlow = noSlow;
   }

   public void onPacketSend(
      PacketEvent.Send event
   ) {
   }

   public void onTickStart(LocalPlayerPreTickEvent event) {
   }

   public void onInventoryAction(InventoryActionEvent event) {
   }

   public void beforeMenuClick(
      MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick event
   ) {
   }

   public void onInventoryClickIntercept(
      MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash event
   ) {
   }

   public void onPlayerAiStep() {
   }

   public void onTickBeforePassengerCheck() {
   }
}
