/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.gui.CursorType;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.utils.collections.NamedIdentifier;
import java.util.regex.Matcher;
import dev.zio.client.gui.CloseState;
import dev.zio.client.gui.panels.Panel;
import dev.zio.client.gui.screens.ClientScreen;
import dev.zio.client.gui.screens.SoundSelectionScreen;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;

public class SoundSettingComponent extends SettingComponent<NamedIdentifier> {

   public SoundSettingComponent(Panel panel, CloseState parentCloseState, Setting<NamedIdentifier> setting) {
      super(panel, parentCloseState, setting);
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (!this.isUnavailable()) {
         super.render(poseStack, mouseX, mouseY);
         if (this.isMouseOver(mouseX, mouseY)) {
            ClientScreen.cursor = CursorType.POINTER;
         }

         GuiDrawUtils.fillRect(
            poseStack,
            this.panel
                  .getX()
               + this.getHorizontalPadding(),
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
         String settingName = this.setting.getName();
         String soundName = (this.setting.getValue()).getName();

         String label = (String.valueOf(settingName) + ": \u0001")
            .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(soundName)));
         this.renderWithTextScroll(
            poseStack,
            label,
            () -> FontRenderer.INSTANCE
               .drawWithShadow(
                  poseStack,
                  label,
                  this.panel
                        .getX()
                     + (4),
                  this.panel
                        .getY()
                     + this.getTextYOffset()
                     - this.getHoverAnimationValue()
                     + this.yOffset,
                  this.getUiModule()
                     .textColorSetting
                     .getValue()
               )
         );
      }
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isUnavailable()
         && this.isMouseOver(mouseX, mouseY)
         && button == 0) {
         new SoundSelectionScreen(
               this.setting,
               this.getClickGui()
            )
            .open();
      }
   }

}
