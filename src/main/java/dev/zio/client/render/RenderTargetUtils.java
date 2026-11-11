/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import dev.zio.client.ClientAccess;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.zio.mixin.MinecraftAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.commons.io.IOUtils;

public final class RenderTargetUtils implements ClientAccess {
   private static final Deque<TargetState> savedTargets = new ArrayDeque<>();

   public static void popFramebuffer() {
      ((LevelRendererFramebufferAccess)ClientAccess.zioMinecraft.levelRenderer).zio$popFramebuffer();
   }

   public static void restoreRenderTargets() {
      TargetState previous = savedTargets.pop();
      popFramebuffer();
      setMainRenderTarget(previous.mainTarget());
      RenderSystem.outputColorTextureOverride = previous.colorOverride();
      RenderSystem.outputDepthTextureOverride = previous.depthOverride();
   }

   private RenderTargetUtils() {
      throw new AssertionError();
   }

   public static void pushFramebuffer(RenderTarget target) {
      ((LevelRendererFramebufferAccess)ClientAccess.zioMinecraft.levelRenderer)
         .zio$pushFramebuffer(target);
   }

   public static void overrideRenderTargets(RenderTarget target) {
      TargetState previous = new TargetState(
         ClientAccess.zioMinecraft.getMainRenderTarget(),
         RenderSystem.outputColorTextureOverride,
         RenderSystem.outputDepthTextureOverride
      );
      pushFramebuffer(target);
      savedTargets.push(previous);
      setMainRenderTarget(target);
      // RenderType prefers these overrides over its render target.
      RenderSystem.outputColorTextureOverride = null;
      RenderSystem.outputDepthTextureOverride = null;
   }

   private record TargetState(RenderTarget mainTarget, GpuTextureView colorOverride, GpuTextureView depthOverride) {}

   public static void setMainRenderTarget(RenderTarget target) {
      ((MinecraftAccessor)ClientAccess.zioMinecraft).zio$setFramebuffer(target);
   }

   public static String readShaderSource(String resourcePath) {
      try {
         InputStream stream = BaseShaderPass.class.getResourceAsStream("/assets/zio/shaders/" + resourcePath);
         assert stream != null;
         return IOUtils.toString(stream, StandardCharsets.UTF_8);
      } catch (IOException | AssertionError failure) {
         failure.printStackTrace();
         return "";
      }
   }
}
