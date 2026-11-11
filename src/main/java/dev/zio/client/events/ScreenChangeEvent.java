/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import net.minecraft.client.gui.screens.Screen;

public class ScreenChangeEvent extends CancellableEvent {
   private final Screen previousScreen;
   private final Screen newScreen;

   public Screen getPreviousScreen() {
      return this.previousScreen;
   }

   public Screen getNewScreen() {
      return this.newScreen;
   }

   public ScreenChangeEvent(Screen previousScreen, Screen newScreen) {
      this.previousScreen = previousScreen;
      this.newScreen = newScreen;
   }

   public static final class Before extends ScreenChangeEvent {

      public Before(Screen previousScreen, Screen newScreen) {
         super(previousScreen, newScreen);
      }
   }

   public static final class AfterInit extends ScreenChangeEvent {

      public AfterInit(Screen previousScreen, Screen newScreen) {
         super(previousScreen, newScreen);
      }
   }
}
