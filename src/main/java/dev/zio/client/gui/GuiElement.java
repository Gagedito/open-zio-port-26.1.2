/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui;

import dev.zio.client.render.FontRenderer;
import dev.zio.client.modules.client.UIModule;

import com.mojang.blaze3d.vertex.PoseStack;

public interface GuiElement {
   default UIModule getUiModule() {
      return UIModule.INSTANCE;
   }

   default int getBaseHeight() {
      return UIModule.INSTANCE.styleSetting.getValue().getHeightPadding()
         + FontRenderer.INSTANCE.getHeight();
   }

   default int getYOffset() {
      return 0;
   }

   default void setYOffset(int yOffset) {
   }

   default float getTextYOffset() {
      return (this.getBaseHeight() - FontRenderer.INSTANCE.getHeight() + 1) / 2.0F;
   }

   default int getHeight() {
      return this.getBaseHeight();
   }

   default void keyPressed(int keyCode) {
   }

   default void init() {
   }

   default void render(PoseStack stack, double mouseX, double mouseY) {
   }

   default void mouseClicked(double mouseX, double mouseY, int button) {
   }

   default boolean isMouseOver(double mouseX, double mouseY) {
      return false;
   }

   default void update(double mouseX, double mouseY) {
   }

   default void mouseScrolled(double mouseX, double mouseY, double amount) {
   }

   default void mouseReleased(double mouseX, double mouseY, int button) {
   }

   default void charTyped(char character) {
   }
}
