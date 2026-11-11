/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.combat;

import net.minecraft.world.damagesource.DamageSource;

public interface RemotePlayerDamageAccess {
   default boolean damageSuper(DamageSource source, float amount) {
      return true;
   }
}
