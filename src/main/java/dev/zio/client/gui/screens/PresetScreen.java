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
import dev.zio.client.gui.components.PresetListPanel;
import dev.zio.client.gui.components.PresetPanel;
import dev.zio.client.utils.FloatAnimation;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.settings.options.PresetCategory;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PresetScreen extends ClientScreen {
   private final PresetListPanel listPanel = new PresetListPanel(this);
   private final PresetPanel presetPanel = new PresetPanel(this);
   private PresetCategory selectedCategory = PresetCategory.MODULES;
   private final FloatAnimation fileDropHintAnimation = new FloatAnimation();

   public PresetScreen() {
      this.panels
         .add(this.listPanel);
      this.presetPanel
         .setX(
            this.listPanel
                  .getX()
               + this.listPanel
                  .getWidth()
               + (3)
         );
      this.panels
         .add(this.presetPanel);
   }

   @Override
   public void renderContents(
      GuiGraphicsExtractor graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick
   ) {
      this.fileDropHintAnimation
         .setTarget(
            !ClientAccess.zioMinecraft.isWindowActive(), 250L
         );
      float hintOpacity = this.fileDropHintAnimation
         .getValue();
      if (hintOpacity != 0.0F) {
         RenderUtils.setColorAlpha(
            0.75F
               * hintOpacity
         );
         String hintText = "Drop preset files here";
         float hintWidth = FontRenderer.INSTANCE
            .getWidth(hintText);
         FontRenderer.INSTANCE
            .drawWithShadow(
               poseStack,
               hintText,
               (ClientAccess.zioMinecraft.getWindow().getGuiScaledWidth() - hintWidth)
                  / 2.0F,
               ClientAccess.zioMinecraft.getWindow().getGuiScaledHeight()
                  / 2.0F,
               Color.white
            );
         RenderUtils.setColorAlpha(
            1.0F
         );
      }
   }

   @Override
   public void onFilesDrop(List files) {
      PresetImportScreen.openForDroppedFiles(this, files);
   }

   public PresetListPanel getListPanel() {
      return this.listPanel;
   }

   public PresetPanel getPresetPanel() {
      return this.presetPanel;
   }

   public PresetCategory getSelectedCategory() {
      return this.selectedCategory;
   }

   public void setSelectedCategory(PresetCategory category) {
      this.selectedCategory = category;
   }

}
