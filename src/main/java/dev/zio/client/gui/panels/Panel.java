/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.panels;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.gui.screens.ClientScreen;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.utils.InterpolatedFloat;
import dev.zio.client.utils.FloatAnimation;
import dev.zio.client.utils.ClampedInterpolatedFloat;
import dev.zio.client.utils.inventory.RegistryContainer;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.gui.GuiElement;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.gui.ScissorStack;
import dev.zio.client.render.GuiScaleUtils;
import dev.zio.client.modules.client.UIModule;
import dev.zio.client.settings.NamedEntry;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public class Panel implements RegistryContainer, GuiElement, NamedEntry {
   public final ArrayList<GuiElement> children = new ArrayList<>();
   protected final ClampedInterpolatedFloat expansionAnimation = new ClampedInterpolatedFloat(
      () -> 2.0F,
      true
   );
   private final InterpolatedFloat scrollAnimation = new InterpolatedFloat(
      () -> this.getUiModule().animSpeedSetting.getValue()
         * 2.0F,
      true
   );
   private final FloatAnimation topScrollFadeAnimation = new FloatAnimation();
   private final FloatAnimation bottomScrollFadeAnimation = new FloatAnimation();
   private final FloatAnimation headerAlphaAnimation = new FloatAnimation();
   private final String name;
   protected final int baseWidth;
   public int dragOffsetX;
   public int dragOffsetY;
   protected boolean expanded;
   protected int y;
   protected int x;
   protected boolean dragging;
   private int height;
   private int contentHeight;
   private boolean heightLimitDisabled;
   private int scrollOffset;
   private int scrollRecoveryCounter;

   private static final float SCROLL_RECOVERY_FPS_BASE = 200.0F;
   private static final float SCROLL_RECOVERY_SPEED = 20.0F;
   private static final int SLOW_FRAME_RATE = 40;
   private static final int MAX_SCROLL_OVERSHOOT = 40;

   public void updateLayout() {
      int animatedScrollOffset = (int)this.scrollAnimation
         .getValue();
      int nextYOffset = this.getHeaderHeight()
         + 1
         + animatedScrollOffset;

      for (GuiElement child : this.children) {
         if (!this.isChildVisible(child)) {
            child.setYOffset(0);
         } else {
            child.setYOffset(nextYOffset);
            nextYOffset += child.getHeight();
         }
      }

      int heightLimit = this.getHeightLimit();
      this.contentHeight = this.expansionAnimation
               .getValue()
            == 0.0F
         ? this.getHeaderHeight()
            + 1
         : nextYOffset - animatedScrollOffset;
      if (this.heightLimitDisabled) {
         this.height = this.contentHeight;
      } else {
         this.height = Math.min(
            this.contentHeight, heightLimit
         );
      }
   }

   private void recoverScrollOffset() {
      if (!this.heightLimitDisabled) {
         int minimumOffset = Math.min(
            -this.contentHeight
               + this.height,
            0
         );
         byte maximumOffset = 0;
         int framesPerSecond = ClientContext.frameTiming.getFramesPerSecond();
         float recoveryDivisor = framesPerSecond / SCROLL_RECOVERY_FPS_BASE
            * SCROLL_RECOVERY_SPEED;
         if (framesPerSecond <= SLOW_FRAME_RATE) {
            recoveryDivisor /= 2.0F;
         }

         if (this.scrollOffset < minimumOffset) {
            this.scrollOffset = this.scrollOffset
               + (int)Math.min(
                  this.scrollRecoveryCounter++ / recoveryDivisor,
                  minimumOffset - this.scrollOffset
               );
         } else if (this.scrollOffset > maximumOffset) {
            this.scrollOffset = this.scrollOffset
               - (int)Math.min(
                  this.scrollRecoveryCounter++ / recoveryDivisor,
                  this.scrollOffset - maximumOffset
               );
         } else {
            this.scrollRecoveryCounter = 0;
         }

         this.scrollOffset = Mth.clamp(
            this.scrollOffset,
            minimumOffset - MAX_SCROLL_OVERSHOOT,
            maximumOffset + MAX_SCROLL_OVERSHOOT
         );
      }
   }

   public void disableHeightLimit() {
      this.heightLimitDisabled = true;
   }

   protected int getHeightLimit() {
      float guiScale = 1.0F;
      if (ClientAccess.zioMinecraft.screen instanceof ClientScreen clientScreen
         )
       {
         guiScale = clientScreen.getGuiScale();
      }

      return (int)((GuiScaleUtils.getBaseScaledHeight() - 50) / guiScale);
   }

   @Override
   public int getHeight() {
      return this.height;
   }

   public ArrayList getChildren() {
      return this.children;
   }

   public void setExpanded(boolean expanded) {
      this.expanded = expanded;
   }

   public void setX(int x) {
      this.x = x;
   }

   public int getWidth() {
      return this.baseWidth
         + (UIModule.INSTANCE
               .styleSetting
               .getValue())
            .getWidthPadding();
   }

   public void setDragging(boolean requestedDragging) {
      if (requestedDragging
         && !ClientContext.getClickGui()
            .isPanelDragging()) {
         ClientContext.getClickGui()
            .setPanelDragging(
               true
            );
         this.dragging = true;
      } else {
         if (ClientContext.getClickGui()
            .isPanelDragging()) {
            ClientContext.getClickGui()
               .setPanelDragging(
                  false
               );
         }

         this.dragging = false;
      }
   }

   public boolean isDragging() {
      return this.dragging;
   }

   private static double getDragScale() {
      return GuiScaleUtils.getScaleMultiplier();
   }

   public void updateDragPosition(int mouseX, int mouseY) {
      if (this.dragging) {
         double guiScale = getDragScale();
         this.setX(
            (int)Math.clamp(
               mouseX - this.dragOffsetX,
               0.0,
               ClientAccess.zioMinecraft.getWindow().getGuiScaledWidth()
                     / guiScale
                  - this.getWidth()
            )
         );
         this.setY(
            (int)Math.clamp(
               mouseY - this.dragOffsetY,
               0.0,
               ClientAccess.zioMinecraft.getWindow().getGuiScaledHeight()
                     / guiScale
                  - this.getHeight()
            )
         );
      }
   }

   public float getExpansionValue() {
      return this.expansionAnimation
         .getValue();
   }

   @Override
   public void keyPressed(int keyCode) {
      if (this.expanded) {
         for (GuiElement child : this.children) {
            child.keyPressed(keyCode);
         }
      }
   }

   public void setY(int y) {
      this.y = y;
   }

   public int getHeaderHeight() {
      return this.getUiModule().styleSetting.getValue()
            == UIModule.StyleChoice.SLIM
         ? (int)(12L)
         : (int)(14L);
   }

   @Override
   public String getName() {
      return this.name;
   }

   public Panel(String name) {
      this.name = name;
      this.baseWidth = 92;
      this.dragOffsetX = 0;
      this.expanded = true;
      this.y = 5;
      this.x = 5;
      this.dragging = false;
      this.expansionAnimation.setValue(1.0F);
      this.headerAlphaAnimation
         .setValue(
            1.0F
         );
   }

   @Override
   public boolean register(Object child) {
      return this.registerChild(
         (GuiElement)child
      );
   }

   @Override
   public void init() {
      if (this.expanded) {
         for (GuiElement child : this.children) {
            child.init();
         }
      }
   }

   @Override
   public boolean unregister(Object child) {
      GuiElement element = (GuiElement)child;

      return this.getRegistry().add(element);
   }

   public int getY() {
      return this.y;
   }

   @Override
   public List<GuiElement> getRegistry() {
      return this.children;
   }

   public int getX() {
      return this.x;
   }

   private void renderScrollFade(PoseStack poseStack, float fadeAmount, boolean top) {
      float expansionValue = this.getExpansionValue();
      if (expansionValue
         < 1.0F) {
         fadeAmount *= expansionValue;
      }

      Color uiColor = UIModule.INSTANCE.colorSetting.getValue();
      int startColor = ColorUtils.toArgb(uiColor, 0);
      int endColor = ColorUtils.toArgb(
         uiColor,
         0.3F
            * fadeAmount
      );
      if (top) {
         startColor = endColor;
         endColor = ColorUtils.toArgb(uiColor, 0);
      }

      if (fadeAmount != 0.0F) {
         GuiDrawUtils.fillVerticalGradient(
            poseStack,
            this.x,
            top
               ? this.y
                  + this.getHeaderHeight()
               : this.y
                  + this.height * expansionValue
                  - 10.0F,
            this.getWidth(),
            10.5F,
            startColor,
            endColor
         );
      }
   }

   public void addChildren(GuiElement... children) {
      for (GuiElement child : children) {
         this.addChild(child);
      }
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      Color uiColor = this.getUiModule().colorSetting.getValue();
      this.expansionAnimation.setTarget(this.expanded ? 1.0F : 0.0F);
      float expansionValue = this.expansionAnimation
         .updateAndGet();
      this.scrollAnimation
         .setTarget(
            this.scrollOffset
         );
      this.scrollAnimation
         .updateAndGet();
      this.headerAlphaAnimation
         .setTarget(
            expansionValue
                  > 0.25F
               || this.isMouseOverHeader(mouseX, mouseY),
            200L
         );
      float headerVerticalOffset = this.getUiModule().lineSetting.getValue()
         ? 0.0F
         : 0.5F;
      GuiDrawUtils.fillRect(
         poseStack,
         this.x,
         this.y,
         this.x
            + this.getWidth(),
         this.y
               + this.getHeaderHeight()
            + headerVerticalOffset,
         this.applyHeaderAlpha(uiColor)
      );
      FontRenderer.INSTANCE
         .drawWithShadow(
            poseStack,
            this.name,
            this.x
               + (2),
            this.y
               + this.getHeaderHeight()
                  / 2.0F
               - FontRenderer.INSTANCE
                     .getHeight()
                  / 2.0F
               + headerVerticalOffset,
            this.applyHeaderAlpha(Color.white)
         );
      if (expansionValue != 0.0F) {
         GuiDrawUtils.flush();
         ScissorStack.push(
            this.x - 1,
            this.y
               + this.getHeaderHeight(),
            this.getWidth() + 1,
            (int)(this.height * expansionValue)
               + 1
               - this.getHeaderHeight()
         );
      }

      Color backgroundColor = UIModule.INSTANCE
         .backgroundSetting
         .getValue();
      if (expansionValue > 0.0F && !this.children.isEmpty()) {
         GuiDrawUtils.fillRect(
            poseStack,
            this.x,
            this.y
               + this.getHeaderHeight(),
            this.x
               + this.getWidth(),
            this.y
                  + this.height
               + 0.5F,
            backgroundColor
         );
         GuiDrawUtils.flush();
         this.children
            .forEach(
               child -> {
                  if (this.isChildVisible(child)) {
                     child.update(mouseX, mouseY);
                     int childYOffset = child.getYOffset();
                     if (childYOffset <= this.height
                        && childYOffset + child.getHeight() >= 0) {
                        child.render(poseStack, mouseX, mouseY);
                     }
                  }
               }
            );
      }

      if (expansionValue != 0.0F) {
         GuiDrawUtils.flush();
         ScissorStack.pop();
      }

      poseStack.pushPose();
      poseStack.translate(
         0.0F,
         0.0F,
         500.0F
      );
      this.bottomScrollFadeAnimation
         .setTarget(
            this.scrollOffset
               > Math.min(
                  -this.contentHeight
                     + this.height,
                  0
               ),
            200L
         );
      this.topScrollFadeAnimation
         .setTarget(
            this.scrollOffset < 0, 200L
         );
      this.renderScrollFade(
         poseStack,
         this.bottomScrollFadeAnimation
            .getValue(),
         false
      );
      this.renderScrollFade(
         poseStack,
         this.topScrollFadeAnimation
            .getValue(),
         true
      );
      if (UIModule.INSTANCE.lineSetting.getValue()) {
         float outlineHeight = (int)(this.height * expansionValue)
            + 0.5F;
         if (outlineHeight
            <= this.getHeaderHeight()
               + (2)) {
            outlineHeight = this.getHeaderHeight();
         }

         GuiDrawUtils.drawRectOutline(
            poseStack,
            this.x - 1,
            this.y - 1,
            this.x
               + this.getWidth(),
            this.y + outlineHeight,
            this.applyHeaderAlpha(uiColor)
         );
      }

      poseStack.popPose();
      this.recoverScrollOffset();
      this.updateLayout();
      GuiDrawUtils.flush();
   }

   protected boolean isChildVisible(GuiElement child) {
      return true;
   }

   public void addChild(GuiElement child) {
      this.children.add(child);
   }

   public boolean isFullyExpanded() {
      return this.expanded && this.expansionAnimation.currentValue >= 1.0F;
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX >= this.x
         && mouseX
            <= this.x
               + this.getWidth()
         && mouseY >= this.y
         && mouseY
            <= this.y
               + this.height;
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (this.isMouseOverHeader(mouseX, mouseY)
         && button == 1) {
         this.setExpanded(
            !this.expanded
         );
      }

      if (this.expanded) {
         for (GuiElement child : this.children) {
            child.mouseClicked(mouseX, mouseY, button);
         }
      }
   }

   public boolean registerChild(GuiElement child) {
      return this.getRegistry()
         .add(child);
   }

   @Override
   public void mouseReleased(double mouseX, double mouseY, int button) {
      if (this.expanded) {
         for (GuiElement child : this.children) {
            child.mouseReleased(mouseX, mouseY, button);
         }
      }
   }

   @Override
   public void mouseScrolled(double mouseX, double mouseY, double scrollAmount) {
      if (this.expanded) {
         if (this.isMouseOver(mouseX, mouseY)
            && !this.heightLimitDisabled) {
            this.scrollOffset = this.scrollOffset
               + (int)(scrollAmount * 20.0D);
         }

         for (GuiElement child : this.children) {
            child.mouseScrolled(mouseX, mouseY, scrollAmount);
         }
      }
   }

   @Override
   public void charTyped(char character) {
      if (this.expanded) {
         for (GuiElement child : this.children) {
            child.charTyped(character);
         }
      }
   }

   protected Color applyHeaderAlpha(Color color) {
      float alphaFactor = this.headerAlphaAnimation
               .getValue()
            * 0.5F
         + 0.5F;
      return ColorUtils.withAlpha(
         color,
         color.getAlpha()
            / 255.0F
            * alphaFactor
      );
   }

   public boolean isMouseOverHeader(double mouseX, double mouseY) {
      return mouseX >= this.x
         && mouseX
            <= this.x
               + this.getWidth()
         && mouseY >= this.y
         && mouseY
            <= this.y
               + this.getHeaderHeight();
   }

}
