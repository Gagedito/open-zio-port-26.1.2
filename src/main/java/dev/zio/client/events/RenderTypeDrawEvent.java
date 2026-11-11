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

import com.mojang.blaze3d.textures.GpuTextureView;

public final class RenderTypeDrawEvent extends CancellableEvent {
   private final EventStage stage;
   private GpuTextureView override;

   public EventStage getStage() {
      return this.stage;
   }

   public void setOverride(GpuTextureView override) {
      this.override = override;
   }

   public RenderTypeDrawEvent(EventStage stage) {
      this.stage = stage;
   }

   public GpuTextureView getOverride() {
      return this.override;
   }
}
