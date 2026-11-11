/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import net.minecraft.world.entity.Entity;

public final class ClientLevelAddEntityEvent extends CancellableEvent {
   private final Entity entity;

   public Entity getEntity() {
      return this.entity;
   }

   public ClientLevelAddEntityEvent(Entity entity) {
      this.entity = entity;
   }
}
