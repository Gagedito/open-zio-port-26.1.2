/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import dev.zio.client.events.EventStage;

public final class InventoryActionEvent extends CancellableEvent {
   private final EventStage stage;

   public InventoryActionEvent(EventStage stage) {
      this.stage = stage;
   }

   public EventStage getStage() {
      return this.stage;
   }
}
