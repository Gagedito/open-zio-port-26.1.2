/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import java.awt.Color;

public final class MusicNotification extends HudNotification {

   public MusicNotification(String text, long durationMillis, Color color) {
      super(text, durationMillis, color);
   }

}
