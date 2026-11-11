/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import dev.zio.client.managers.RenderTargetRegistry;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import net.minecraft.client.Minecraft;

public final class MaskShaderRenderer implements ClientAccess {
   public static final RenderTarget MASK_TARGET = RenderTargetRegistry.createRenderTarget(
      "zio_blur"
   );
   public static final RenderTarget HORIZONTAL_TARGET = RenderTargetRegistry.createRenderTarget(
      "zio_blur_horizontal"
   );
   public static final RenderTarget VERTICAL_TARGET = RenderTargetRegistry.createRenderTarget(
      "zio_blur_vertical"
   );
   private static float blurWidth;
   private static float bloom;
   private static float opacity;
   private static float modulate;

   public static float getBlurWidth() {
      return blurWidth;
   }

   public static float getBloom() {
      return bloom;
   }

   public static float getModulate() {
      return modulate;
   }

   public static void setParameters(
      float width, float bloomStrength, float maskOpacity, float modulation
   ) {
      blurWidth = width;
      bloom = bloomStrength;
      opacity = maskOpacity;
      modulate = modulation;
   }

   public static float getOpacity() {
      return opacity;
   }

   public static void renderBlur(Runnable drawMask, float width) {
      blurWidth = width;
      if (GuiDeferredDraws.deferEnabled) {
         // MC 26.1.2: mientras la fase extract no pinta al main target, la
         // secuencia mascarar->efecto se graba en la cola diferida (quads de
         // la mascara capturados en una sublista) y GameRendererMixin la
         // ejecuta tras el pase vanilla GuiRenderer.render, con el mismo
         // orden del contrato 1.21.11 (clear mask -> swap -> draw -> restore
         // -> composite).
         GuiDeferredDraws.beginMaskCapture();
         try {
            if (drawMask != null) {
               drawMask.run();
            }
         } finally {
            GuiDeferredDraws.endMaskCapture(
               () -> executeMaskCaptureSetup(null, Color.WHITE),
               MaskShaderRenderer::renderBlurEffect
            );
         }
         return;
      }

      captureMask(drawMask);
      renderEffect("blur_mask");
   }

   private static void executeMaskCaptureSetup(Color clearColor, Color fallback) {
      RenderUtils.clearColor(
         MASK_TARGET,
         clearColor == null ? fallback : clearColor
      );
      RenderUtils.clearDepth(
         MASK_TARGET
      );
      RenderTargetUtils.setMainRenderTarget(
         MASK_TARGET
      );
   }

   private static void renderBlurEffect() {
      ClientContext.shaderRegistry
         .render("blur_mask", () -> {});
   }

   public static void captureMask(Runnable drawMask) {
      captureMask(drawMask, Color.WHITE);
   }

   public static void captureMask(Runnable drawMask, Color clearColor) {
      RenderSystem.assertOnRenderThread();
      if (GuiDeferredDraws.deferEnabled) {
         // grabar: los quads de la mascara van a una sublista y el setup
         // (clear + swap) se ejecuta cuando el drenaje alcanza la tarea.
         GuiDeferredDraws.beginMaskCapture();
         try {
            if (drawMask != null) {
               drawMask.run();
            }
         } finally {
            GuiDeferredDraws.endMaskCapture(
               () -> executeMaskCaptureSetup(clearColor, null),
               null
            );
         }
         return;
      }

      RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
      if (drawMask != null) {
         RenderUtils.clearColor(
            MASK_TARGET, clearColor
         );
         RenderUtils.clearDepth(
            MASK_TARGET
         );
         RenderTargetUtils.setMainRenderTarget(
            MASK_TARGET
         );
         drawMask.run();
         RenderTargetUtils.setMainRenderTarget(mainTarget);
      }
   }

   public static void renderEffect(String effectId) {
      // MC 26.1.2: en fase extract el composite difiere con la cola (ver
      // GuiDeferredDraws); fuera de esa fase queda inmediato como en
      // 1.21.11 (rutas de mundo como ChamsModule).
      GuiDeferredDraws.submitTask(() -> renderEffectImmediate(effectId));
   }

   private static void renderEffectImmediate(String effectId) {
      ClientContext.shaderRegistry
         .render(effectId, () -> {});
   }

}
