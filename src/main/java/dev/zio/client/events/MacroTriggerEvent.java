/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import dev.zio.client.modules.misc.Macro;

public final class MacroTriggerEvent extends CancellableEvent {
   private final Macro macro;

   public MacroTriggerEvent(Macro macro) {
      this.macro = macro;
   }

   public Macro getMacro() {
      return this.macro;
   }
}
