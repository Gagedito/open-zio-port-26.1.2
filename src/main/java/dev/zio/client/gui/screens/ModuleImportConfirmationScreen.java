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
import dev.zio.client.gui.panels.Panel;
import dev.zio.client.gui.components.ActionButtonComponent;
import dev.zio.client.gui.components.ButtonComponent;
import dev.zio.client.gui.GuiElement;
import dev.zio.client.modules.Module;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

public class ModuleImportConfirmationScreen extends ClientScreen {
   private final Screen returnScreen;
   private final Module module;
   private final JsonObject moduleJson;

   public ModuleImportConfirmationScreen(Screen previousScreen, Module targetModule, JsonObject importedJson) {
      this.returnScreen = previousScreen;
      this.module = targetModule;
      this.moduleJson = importedJson;
      Panel confirmationPanel = new Panel("Confirmation");
      ButtonComponent moduleLabel = new ButtonComponent(
         confirmationPanel,
         "Module: " + targetModule.getName()
      );
      ActionButtonComponent importButton = new ActionButtonComponent(
         confirmationPanel, "Import", this::importModule
      );
      ActionButtonComponent cancelButton = new ActionButtonComponent(
         confirmationPanel, "Cancel", this::closeWithTransition
      );
      GuiElement[] children = {moduleLabel, importButton, cancelButton};
      confirmationPanel.addChildren(children);
      this.getPanels()
         .add(confirmationPanel);
   }

   private void importModule() {
      this.module.fromJson(this.moduleJson);
      this.closeWithTransition();
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (event.key() == 257) {
         this.importModule();
      }

      return super.keyPressed(event);
   }

   @Override
   public void closeWithTransition() {
      if (this.returnScreen instanceof ClientScreen clientScreen) {
         clientScreen.open();
      } else {
         ClientAccess.zioMinecraft.setScreen(
            this.returnScreen
         );
      }
   }

}
