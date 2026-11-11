/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.settings.NamedEntry;

public enum SocialRole implements NamedEntry {
   FRIEND("friend", "friends"),
   ENEMY("enemy", "enemies");

   private final String name;
   private final String pluralName;

   public String getPluralName() {
      return this.pluralName;
   }

   @Override
   public String getName() {
      return this.name;
   }

   SocialRole(String name, String pluralName) {
      this.name = name;
      this.pluralName = pluralName;
   }
}
