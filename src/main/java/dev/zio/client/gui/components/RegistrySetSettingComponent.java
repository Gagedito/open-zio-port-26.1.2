/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.render.FontRenderer;
import dev.zio.client.render.GuiDrawUtils;

import dev.zio.client.gui.components.SettingComponent;
import dev.zio.client.gui.components.ModuleComponent;
import dev.zio.client.ClientAccess;
import dev.zio.client.gui.panels.Panel;
import dev.zio.client.gui.registry.RegistrySetScreen;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.Set;

public class RegistrySetSettingComponent<E> extends SettingComponent<java.util.Set<E>> {

   private final RegistrySetSetting<E> registrySetting;

   public RegistrySetSettingComponent(Panel panel, ModuleComponent moduleComponent, RegistrySetSetting<E> setting) {
      super(panel, moduleComponent, setting);
      this.registrySetting = setting;
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (!this.isUnavailable()) {
         super.render(poseStack, mouseX, mouseY);
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
         String selectionCount = "[%d]"
            .formatted((this.setting.getValue()).size());
         float textY = this.panel
               .getY()
            + this.getTextYOffset()
            - this.getHoverAnimationValue()
            + this.yOffset;
         FontRenderer.INSTANCE
            .drawWithShadow(
               poseStack,
               settingName,
               this.panel
                     .getX()
                  + 4,
               textY,
               this.getUiModule().textColorSetting.getValue()
            );
         FontRenderer.INSTANCE
            .drawWithShadow(
               poseStack,
               selectionCount,
               this.panel
                        .getX()
                     + this.panel
                        .getWidth()
                  - FontRenderer.INSTANCE
                     .getWidth(selectionCount)
                  - 2.5F,
               textY,
               this.getUiModule().textColorSetting.getValue()
            );
      }
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isUnavailable()) {
         super.mouseClicked(mouseX, mouseY, button);
         if (this.isMouseOver(mouseX, mouseY) && (button == 0 || button == 1)) {
            new RegistrySetScreen<>(
                  ClientAccess.zioMinecraft.screen,
                  this.registrySetting
               )
               .open();
         }
      }
   }

}
