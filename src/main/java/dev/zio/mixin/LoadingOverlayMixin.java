/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.zio.client.ClientContext;
import dev.zio.client.audio.BuiltinSound;
import dev.zio.client.render.LoadingOverlayRenderer;
import dev.zio.client.render.LoadingOverlayTheme;
import dev.zio.client.modules.client.SettingsModule;
import dev.zio.client.utils.collections.NamedIdentifier;
import java.awt.Color;
import java.util.Calendar;
import java.util.Date;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin extends Overlay {
   private static final SettingsModule settingsModule = ClientContext.getModule(SettingsModule.class);
   @Shadow
   private long fadeOutStart;
   @Shadow
   @Final
   private Minecraft minecraft;
   @Unique
   private LoadingOverlayTheme loadingTheme;
   @Unique
   private static boolean startupSoundPlayed;
   @Unique
   private static boolean loadingRenderStarted;
   @Shadow
   private float currentProgress;
   @Shadow
   private long fadeInStart;

   @Inject(method = "<init>", at = @At("TAIL"))
   private void initializeLoadingTheme(
      Minecraft client, ReloadInstance reload, Consumer onFinish, boolean fadeIn, CallbackInfo callback
   ) {
      this.loadingTheme = LoadingOverlayTheme.DEFAULT;

      Calendar calendar = Calendar.getInstance();
      calendar.setTime(new Date());
      int firstCalendarValue = calendar.get(2);
      int secondCalendarValue = calendar.get(5);
      if (firstCalendarValue == 1
            && secondCalendarValue
               == 23
         || firstCalendarValue
               == 4
            && secondCalendarValue
               == 9) {
         this.loadingTheme = LoadingOverlayTheme.GOOD_DAY;
      }
      if (firstCalendarValue == Calendar.OCTOBER && secondCalendarValue >= 26) {
         this.loadingTheme = LoadingOverlayTheme.HALLOWEEN;
      }
   }

   @Shadow
   private static int replaceAlpha(int color, int alpha) {
      return 0;
   }

   @Inject(method = "extractRenderState", at = @At("HEAD"))
   private void handleStartupEffects(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback
   ) {
      if (!startupSoundPlayed
         && loadingRenderStarted) {
         if (Boolean.getBoolean("zio.maximizedWindow")) {
            long windowHandle = this.minecraft.getWindow().handle();
            if (GLFW.glfwGetWindowMonitor(windowHandle) == 0L) {
               GLFW.glfwMaximizeWindow(windowHandle);
            }
         }

         NamedIdentifier startupSound = settingsModule.getStartupSound();
         Calendar calendar = Calendar.getInstance();
         calendar.setTime(new Date());
         if (calendar.get(Calendar.MONTH) == Calendar.OCTOBER
            && calendar.get(Calendar.DAY_OF_MONTH) >= 26) {
            startupSound = BuiltinSound.HORROR.getIdentifier();
         }

         ClientContext.soundManager.getSound(startupSound).play(0.5F);
         startupSoundPlayed = true;
      }

      loadingRenderStarted = true;
   }

   @WrapWithCondition(
      method = "extractRenderState",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorTexture(Lcom/mojang/blaze3d/textures/GpuTexture;I)V")
   )
   private boolean replaceDefaultThemeClear(
      CommandEncoder encoder, GpuTexture texture, int originalColor
   ) {
      if (this.loadingTheme == LoadingOverlayTheme.DEFAULT) {
         encoder.clearColorTexture(
            texture,
            1644825
         );
         return false;
      } else {
         return true;
      }
   }

   @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
   private boolean allowVanillaBackgroundFill(
      GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color
   ) {
      return this.loadingTheme != LoadingOverlayTheme.DEFAULT;
   }

   @WrapWithCondition(
      method = "extractRenderState",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"
      )
   )
   private boolean allowVanillaLogoBlit(
      GuiGraphicsExtractor graphics,
      RenderPipeline pipeline,
      Identifier texture,
      int x,
      int y,
      float u,
      float v,
      int width,
      int height,
      int regionWidth,
      int regionHeight,
      int textureWidth,
      int textureHeight,
      int color
   ) {
      return this.loadingTheme != LoadingOverlayTheme.DEFAULT;
   }

   @Inject(
      method = "extractRenderState",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V",
         ordinal = 0,
         shift = Shift.BEFORE
      )
   )
   private void renderLoadingThemeBackground(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback
   ) {
      int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
      int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
      long fadeInElapsedMillis = Util.getMillis() - this.fadeInStart;
      int opacityByte;
      if (fadeInElapsedMillis <= 500L) {
         opacityByte = (int)((float)fadeInElapsedMillis / 500.0F * 255.0F);
      } else {
         long fadeOutElapsedMillis = Util.getMillis() - this.fadeOutStart;
         opacityByte = Mth.ceil(
            (1.0F - Mth.clamp((this.fadeOutStart > -1L ? (float)fadeOutElapsedMillis / 1000.0F : -1.0F) - 1.0F, 0.0F, 1.0F)) * 255.0F
         );
      }

      if (this.loadingTheme == LoadingOverlayTheme.DEFAULT) {
         LoadingOverlayRenderer.render(graphics, opacityByte / 255.0F, 1.0F, 0.75F);
      } else {
         for (int stripeIndex = 0;
            stripeIndex
               < this.loadingTheme
                  .getColors()
                  .size();
            stripeIndex++
         ) {
            graphics.fill(
               0,
               stripeIndex
                  * (
                     screenHeight
                        / this.loadingTheme
                           .getColors()
                           .size()
                  ),
               screenWidth,
               (stripeIndex + 1)
                  * (
                     screenHeight
                        / this.loadingTheme
                           .getColors()
                           .size()
                  ),
               replaceAlpha(
                  ((Color)this.loadingTheme
                        .getColors()
                        .get(stripeIndex))
                     .hashCode(),
                  opacityByte
               )
            );
         }
      }
   }

   @Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true)
   private void renderDefaultThemeProgressBar(
      GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, float opacity, CallbackInfo callback
   ) {
      if (this.loadingTheme == LoadingOverlayTheme.DEFAULT) {
         int foregroundColor = replaceAlpha(
            -249507,
            (int)(opacity * 255.0F)
         );
         int backgroundColor = replaceAlpha(-1, (int)(opacity * 0.1 * 255.0));
         float progressWidth = graphics.guiWidth() * this.currentProgress * 1.01F;
         graphics.fill(
            0,
            graphics.guiHeight()
               - 3,
            graphics.guiWidth(),
            graphics.guiHeight(),
            backgroundColor
         );
         graphics.fill(
            0,
            graphics.guiHeight()
               - 3,
            (int)progressWidth,
            graphics.guiHeight(),
            foregroundColor
         );
         callback.cancel();
      }
   }
}
