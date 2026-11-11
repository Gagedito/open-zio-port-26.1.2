/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.TaskScheduler;
import dev.zio.client.settings.options.ChestStealerModeChoice;

import dev.zio.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;

class ChestStealerScreenInitializer implements ClientAccess, AfterInit {
   private final ChestStealerModule module;

   ChestStealerScreenInitializer(ChestStealerModule module) {
      this.module = module;
   }

   @Override
   public void afterInit(Minecraft minecraft, Screen screen, int screenWidth, int screenHeight) {
      if (screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen) {
         if (this.module.isToggled()) {
            if (this.module.onlyButtonsSetting.getValue()) {
               this.module.requestMode(null);
               int buttonWidth = 45;
               int buttonX = (screen.width - ((AbstractContainerScreenAccessor)screen).getBgWidth()) / (2) - buttonWidth;
               buttonX -= 2;
               int buttonY = (screen.height - ((AbstractContainerScreenAccessor)screen).getBgHeight()) / (2);

               for (ChestStealerModeChoice mode : ChestStealerModeChoice.values()) {
                  Builder buttonBuilder = new Builder(
                     Component.literal(mode.getName()),
                     button -> {
                        if (mode == this.module.getRequestedMode()) {
                           TaskScheduler scheduler = ClientContext.taskScheduler;
                           Runnable clearFocus = () -> {
                              ((AbstractContainerEventHandler)screen).setFocused(null);
                           };
                           scheduler.scheduleRotationTask(clearFocus, 0);
                           this.module.requestMode(null);
                        } else {
                           this.module.requestMode(mode);
                        }
                     }
                  );

                  buttonBuilder.bounds(buttonX, buttonY, buttonWidth, 15);
                  buttonY += 17;
                  Screens.getWidgets(screen).add(buttonBuilder.build());
               }

               return;
            }
         }
      }
   }

}
