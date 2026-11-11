/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import dev.zio.client.modules.Module;

public final class ModuleToggleEvent {
   private final Module module;

   public ModuleToggleEvent(Module module) {
      this.module = module;
   }

   public Module getModule() {
      return this.module;
   }
}
