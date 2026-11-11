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

public final class AuraAttackEvent extends CancellableEvent {
   private final EventStage stage;
   private final int entityId;

   public AuraAttackEvent(EventStage stage, int entityId) {
      this.stage = stage;
      this.entityId = entityId;
   }

   public int getEntityId() {
      return this.entityId;
   }

   public EventStage getStage() {
      return this.stage;
   }
}
