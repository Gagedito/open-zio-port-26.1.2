/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render.text;

import dev.zio.client.utils.inventory.CharacterConsumer;

import java.util.Collection;

public interface CharacterRange {
   default void addTo(Collection characters) {
      this.forEachCharacter(characters::add);
   }

   void forEachCharacter(CharacterConsumer consumer);
}
