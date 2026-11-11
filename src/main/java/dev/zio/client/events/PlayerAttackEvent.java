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

import net.minecraft.world.entity.Entity;

public final class PlayerAttackEvent extends CancellableEvent {
   private final EventStage stage;
   private final Entity target;

   public PlayerAttackEvent(EventStage stage, Entity target) {
      this.stage = stage;
      this.target = target;
   }

   public Entity getTarget() {
      return this.target;
   }

   public EventStage getStage() {
      return this.stage;
   }
}
