/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import java.util.function.Predicate;

public class TriggerPlayersVisibilityPredicate implements Predicate<Object> {
   private TriggerModule trigger;

   public TriggerPlayersVisibilityPredicate(TriggerModule trigger) {
      this.trigger = trigger;
   }

   @Override
   public boolean test(Object ignoredValue) {
      return this.trigger.targetsSetting.isExpanded();
   }
}
