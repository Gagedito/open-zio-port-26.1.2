/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.gui.panels.Panel;
import dev.zio.client.ClientContext;
import dev.zio.client.gui.screens.PresetScreen;
import dev.zio.client.gui.components.ActionButtonComponent;
import dev.zio.client.config.PresetData;
import dev.zio.client.gui.GuiElement;

import com.mojang.blaze3d.vertex.PoseStack;

public class PresetListPanel extends Panel {
   private final PresetScreen screen;
   private boolean layoutUpdatePending;

   public PresetListPanel(PresetScreen screen) {
      super("List");
      this.screen = screen;
   }

   @Override
   public void render(PoseStack poses, double mouseX, double mouseY) {
      if (this.layoutUpdatePending) {
         for (GuiElement child : this.children) {
            child.update(mouseX, mouseY);
         }

         this.updateLayout();
         this.layoutUpdatePending = false;
      }

      super.render(poses, mouseX, mouseY);
   }

   public void refreshPresets() {
      this.layoutUpdatePending = true;
      this.children.clear();
      ClientContext.configurationManager
         .getPresetRegistry(
            this.screen
               .getSelectedCategory()
         )
         .forEachPreset(
            (java.util.function.Consumer<dev.zio.client.config.PresetData>)(preset -> this.addChild(
               new PresetListPanel.PresetButton(
                  this.screen, this, preset
               )
            ))
         );
   }

   private static final class PresetButton extends ActionButtonComponent {
      private final PresetScreen screen;
      private final PresetData preset;

      public PresetButton(PresetScreen screen, Panel panel, PresetData preset) {
         super(
            panel,
            preset.getName(),
            () -> {
               screen.getPresetPanel()
                  .editPreset(
                     preset
                  );
               screen.getPresetPanel()
                  .updateLayout();
            }
         );
         this.preset = preset;
         this.screen = screen;
      }

      @Override
      public boolean isHighlighted() {
         return this.preset
            .equals(
               this.screen
                  .getPresetPanel()
                  .getSelectedPreset()
            );
      }

   }
}
