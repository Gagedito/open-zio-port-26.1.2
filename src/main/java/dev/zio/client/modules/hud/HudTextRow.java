/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import dev.zio.client.ClientContext;
import dev.zio.client.utils.InterpolatedFloat;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.modules.client.HUDModule;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.util.Mth;

public class HudTextRow {
   private static final HUDModule hudSettings;
   private final Supplier<? extends Component> textSupplier;
   private final Supplier<?> visibilitySupplier;
   private float visibility;
   private Component cachedText;
   public final InterpolatedFloat widthAnimation;

   public void updateVisibility() {
      int frameRate = ClientContext.frameTiming.getFramesPerSecond();
      float visibilityStep = 1.0F / (frameRate >> 2);
      if (this.isDrawn()) {
         this.visibility += visibilityStep;
      } else {
         this.visibility -= visibilityStep;
      }
      float visibilityForClamp = this.visibility;
      float maximumVisibility = 1.0F;
      this.visibility = Mth.clamp(visibilityForClamp, 0.0F, maximumVisibility);
      if (this.visibility > 0.0F) {
         this.cachedText = this.textSupplier.get();
      }
   }

   public HudTextRow(Supplier<? extends Component> textSupplier, Supplier<?> visibilitySupplier) {
      this.cachedText = Component.empty();
      this.widthAnimation = new InterpolatedFloat(2.0F, true);
      this.textSupplier = textSupplier;
      this.visibilitySupplier = visibilitySupplier;
   }

   static {
      hudSettings = ClientContext.getModule(HUDModule.class);
   }

   public boolean isDrawn() {
      Boolean visible = (Boolean)this.visibilitySupplier.get();
      return visible;
   }

   public void renderRow(
      PoseStack poses, float x, float y, Color color, boolean shadow
   ) {
      if (this.visibility == 0.0F) {
         InterpolatedFloat animationForReset = this.widthAnimation;
         resetWidth(animationForReset);
      } else {
         FontRenderer font = FontRenderer.INSTANCE;
         Component widthText = this.getText();
         float textWidth = font.getWidth(widthText.getString());
         Setting<Boolean> smoothWidthOption = hudSettings.smoothWidthSetting;
         Boolean smoothWidth = smoothWidthOption.getValue();
         if (smoothWidth) {
            this.widthAnimation.setTarget(textWidth);
         } else {
            this.widthAnimation.setValue(textWidth);
         }
         if (this.visibility > 0.0F) {
            double originalAlpha = color.getAlpha();
            float visibilityForAlpha = this.visibility;
            double scaledVisibility = visibilityForAlpha * 2.0F;
            int packedColor = ColorUtils.toArgb(
               color, (int)(originalAlpha * Mth.clamp(scaledVisibility, 0.1D, 1.0D))
            );
            font = FontRenderer.INSTANCE;
            if (shadow) {
               font.drawWithShadow(poses, this.getText(), x, y, packedColor);
            } else {
               font.drawWithoutShadow(poses, this.getText(), x, y, packedColor, DisplayMode.NORMAL);
            }
         }
      }
   }

   private static void resetWidth(InterpolatedFloat animation) {
      animation.setValue(0.0F);
   }

   public Component getText() {
      return this.cachedText;
   }

   public float getVisibility() {
      return this.visibility;
   }

}
