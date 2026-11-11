/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import net.minecraft.client.resources.sounds.SoundInstance;

public final class SoundEnginePlayEvent extends CancellableEvent {
   private final SoundInstance soundInstance;

   public SoundInstance getSoundInstance() {
      return this.soundInstance;
   }

   public SoundEnginePlayEvent(SoundInstance soundInstance) {
      this.soundInstance = soundInstance;
   }
}
