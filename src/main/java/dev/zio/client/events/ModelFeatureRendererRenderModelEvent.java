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

import net.minecraft.client.model.EntityModel;

public final class ModelFeatureRendererRenderModelEvent extends CancellableEvent {
   private EntityModel model;
   private EventStage stage;

   public EventStage getStage() {
      return this.stage;
   }

   public EntityModel getModel() {
      return this.model;
   }

   public ModelFeatureRendererRenderModelEvent(EntityModel model, EventStage stage) {
      this.model = model;
      this.stage = stage;
   }

   public void setStage(EventStage stage) {
      this.stage = stage;
   }

   public void setModel(EntityModel model) {
      this.model = model;
   }
}
