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
import dev.zio.client.ClientContext;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.utils.FloatAnimation;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.modules.client.HUDModule;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;

public final class HudMarginController implements ClientAccess {
   private static final HUDModule hud;
   private boolean draggingFromRight;
   private boolean draggingFromBottom;
   private boolean draggingHorizontalMargin;
   private boolean draggingVerticalMargin;
   private final FloatAnimation horizontalOverlayOpacity = new FloatAnimation();
   private final FloatAnimation verticalOverlayOpacity = new FloatAnimation();

   static {
      hud = (HUDModule)ClientContext.getModule(HUDModule.class);
   }

   private boolean isOverHorizontalMargin(double mouseX) {
      return mouseX
            < hud.getSafeX()
               + 1
         || mouseX
            > ClientAccess.zioMinecraft.getWindow().getGuiScaledWidth()
               - hud.getSafeX()
               - 1;
   }

   public void render(
      PoseStack poseStack, int mouseX, int mouseY
   ) {
      int screenWidth = ClientAccess.zioMinecraft.getWindow().getGuiScaledWidth();
      int screenHeight = ClientAccess.zioMinecraft.getWindow().getGuiScaledHeight();
      boolean showHorizontalMargin = this.isOverHorizontalMargin(mouseX)
         || this.draggingHorizontalMargin;
      boolean showVerticalMargin = this.isOverVerticalMargin(mouseY)
         || this.draggingVerticalMargin;
      this.horizontalOverlayOpacity
         .setTarget(
            showHorizontalMargin
               ? 1.4e+02F
               : 0.0F,
            250L
         );
      this.verticalOverlayOpacity
         .setTarget(
            showVerticalMargin
                  && this.horizontalOverlayOpacity
                        .getValue()
                     == 0.0F
               ? 1.4e+02F
               : 0.0F,
            250L
         );
      if (this.horizontalOverlayOpacity
            .getValue()
         > 0.0F) {
         if (this.draggingHorizontalMargin) {
            hud.setSafeX(this.draggingFromRight ? Math.abs(mouseX - screenWidth) : mouseX);
         }

         int horizontalOverlayColor = ColorUtils.toArgb(
            Color.black,
            (int)this.horizontalOverlayOpacity
               .getValue()
         );
         GuiDrawUtils.fillRect(
            poseStack,
            0.0F,
            0.0F,
            hud.getSafeX(),
            screenHeight,
            horizontalOverlayColor
         );
         GuiDrawUtils.fillRect(
            poseStack,
            screenWidth
               - hud.getSafeX(),
            0.0F,
            screenWidth,
            screenHeight,
            horizontalOverlayColor
         );
      } else if (this.verticalOverlayOpacity
            .getValue()
         > 0.0F) {
         if (this.draggingVerticalMargin) {
            hud.setSafeY(
               this.draggingFromBottom ? Math.abs(mouseY - screenHeight) : mouseY
            );
         }

         int verticalOverlayColor = ColorUtils.toArgb(
            Color.black,
            (int)this.verticalOverlayOpacity
               .getValue()
         );
         GuiDrawUtils.fillRect(
            poseStack,
            0.0F,
            0.0F,
            screenWidth,
            hud.getSafeY(),
            verticalOverlayColor
         );
         GuiDrawUtils.fillRect(
            poseStack,
            0.0F,
            screenHeight
               - hud.getSafeY(),
            screenWidth,
            screenHeight,
            verticalOverlayColor
         );
      }
   }

   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         this.draggingHorizontalMargin = this.isOverHorizontalMargin(mouseX);
         if (!this.draggingHorizontalMargin) {
            this.draggingVerticalMargin = this.isOverVerticalMargin(
               mouseY
            );
         }

         this.draggingFromRight = mouseX
            > ClientAccess.zioMinecraft.getWindow().getGuiScaledWidth()
               / 2.0F;
         this.draggingFromBottom = mouseY
            > ClientAccess.zioMinecraft.getWindow().getGuiScaledHeight()
               / 2.0F;
      }
   }

   public void mouseReleased(int button) {
      if (button == 0) {
         this.draggingHorizontalMargin = false;
         this.draggingVerticalMargin = false;
      }
   }

   private boolean isOverVerticalMargin(double mouseY) {
      return mouseY
            < hud.getSafeY()
               + 1
         || mouseY
            > ClientAccess.zioMinecraft.getWindow().getGuiScaledHeight()
               - hud.getSafeY()
               - 1;
   }

}
