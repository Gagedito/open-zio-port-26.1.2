/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.screens;

import dev.zio.client.ClientAccess;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public class DisconnectConfirmationScreen extends ConfirmScreen {
   public DisconnectConfirmationScreen(Runnable confirmationAction) {
      super(
         new ConfirmationCallback(confirmationAction),
         Component.literal("Confirm Disconnect"),
         Component.literal("Are you sure that you want to disconnect?")
      );
   }

   private record ConfirmationCallback(Runnable confirmationAction)
      implements BooleanConsumer,
      ClientAccess {
      @Override
      public void accept(boolean confirmed) {
         ClientAccess.zioMinecraft.setScreen(null);
         if (confirmed) {
            this.confirmationAction.run();
         }
      }
   }
}
