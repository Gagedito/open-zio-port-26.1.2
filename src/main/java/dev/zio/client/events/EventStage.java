/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public enum EventStage {
   Pre,
   Post;

   public boolean isPre() {
      return this == Pre;
   }

   public boolean isPost() {
      return this == Post;
   }

}
