/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.gui.panels.Panel;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.function.Supplier;

public class ActionButtonComponent extends ButtonComponent {
   private final Runnable action;

   public ActionButtonComponent(Panel parentPanel, String label, Runnable action) {
      this(parentPanel, () -> label, action);
   }

   public ActionButtonComponent(Panel parentPanel, Supplier<String> labelSupplier, Runnable action) {
      super(parentPanel, labelSupplier);
      this.action = action;
      this.setHoverOffsetEnabled(true);
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (this.isHighlighted()) {
         GuiDrawUtils.fillRect(
            poseStack,
            this.panel
                  .getX()
               + 1,
            this.panel
                     .getY()
                  + this.yOffset
               + 0.5F,
            this.panel
                  .getX()
               + this.panel
                  .getWidth()
               - 1,
            this.panel
                     .getY()
                  + this.yOffset
                  + this.getHeight()
               - 0.5F,
            this.getUiModule().colorSetting.getValue()
         );
      }

      super.render(poseStack, mouseX, mouseY);
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      super.mouseClicked(mouseX, mouseY, button);
      if (this.isMouseOver(mouseX, mouseY) && button == 0) {
         this.action.run();
      }
   }

   public boolean isHighlighted() {
      return true;
   }

}
