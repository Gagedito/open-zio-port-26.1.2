/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import dev.zio.client.ClientContext;
import dev.zio.client.managers.ServerStateManager;

import java.awt.Color;

public final class LagNotification extends HudNotification {
   private boolean finished;
   private float lagSeconds;
   private static final String lagFormat;

   public LagNotification() {
      super("", 3600000L, createColor());
   }

   @Override
   public void update() {
      long nowMillis = System.currentTimeMillis();
      ServerStateManager networkActivity = ClientContext.serverStateManager;
      long idleMillis = nowMillis - networkActivity.getLastPacketTime();
      if (idleMillis < 1000L && !this.finished) {
         long completionTimeMillis = System.currentTimeMillis();
         this.endTimeMillis = completionTimeMillis + 500L;
         this.finished = true;
      }

      if (!this.finished) {
         float idleMillisAsFloat = (float)idleMillis;
         this.lagSeconds = idleMillisAsFloat / 1000.0F;
      }

      super.text = lagFormat.formatted(this.lagSeconds);
   }

   public void finish() {
      if (!this.finished) {
         long nowMillis = System.currentTimeMillis();
         this.endTimeMillis = nowMillis + 500L;
         this.finished = true;
      }
   }

   static {
      lagFormat = "The server is not responding for %.1fs";
   }

   private static Color createColor() {
      return new Color(128, 128, 128, 255);
   }

}
