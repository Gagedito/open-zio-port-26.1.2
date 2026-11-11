/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.managers;

import dev.zio.client.gui.components.ColorSubmitNodeStorage;
import dev.zio.client.render.BaseShaderPass;
import dev.zio.client.render.BloomMaskPipeline;
import dev.zio.client.render.BloomShaderPass;
import dev.zio.client.render.BlurMaskPipeline;
import dev.zio.client.render.ColorOutlineBufferSource;
import dev.zio.client.render.CustomShaderPass;
import dev.zio.client.render.DepthOfFieldPipeline;
import dev.zio.client.render.DiscardingBufferSource;
import dev.zio.client.render.ESPOutlineShaderPass;
import dev.zio.client.render.GradientShaderPass;
import dev.zio.client.render.GlowyShaderPipeline;
import dev.zio.client.render.NoOpOutlineBufferSource;
import dev.zio.client.render.RainbowShaderPass;
import dev.zio.client.render.ReloadableShaderPass;
import dev.zio.client.render.ShaderPass;
import dev.zio.client.render.SkyboxShaderPass;
import dev.zio.client.render.SolidShaderPass;

import dev.zio.client.ClientAccess;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.resources.model.sprite.AtlasManager;

public final class ShaderRegistry implements ClientAccess {
   private static final ColorSubmitNodeStorage submitNodes;
   private static FeatureRenderDispatcher featureDispatcher;
   private static ColorOutlineBufferSource bufferSource;
   private final Object2ObjectOpenHashMap<String, ShaderPass> passes = new Object2ObjectOpenHashMap<>();
   public boolean renderingIntoTarget;
   public boolean renderingHands;

   private static FeatureRenderDispatcher getFeatureDispatcher() {
      if (featureDispatcher == null) {
         featureDispatcher = new FeatureRenderDispatcher(
            submitNodes,
            readModelManager(),
            new ColorOutlineBufferSource.SuppliedBufferSource(ShaderRegistry::getBufferSource),
            readAtlasManager(),
            NoOpOutlineBufferSource.INSTANCE,
            DiscardingBufferSource.INSTANCE,
            ClientAccess.zioMinecraft.font,
            ClientAccess.zioMinecraft.gameRenderer.getGameRenderState()
         );
      }

      return featureDispatcher;
   }

   private static ModelManager readModelManager() {
      return ClientAccess.zioMinecraft.getModelManager();
   }

   private static AtlasManager readAtlasManager() {
      return ClientAccess.zioMinecraft.getAtlasManager();
   }

   public static ColorSubmitNodeStorage getSubmitNodes() {
      return submitNodes;
   }

   public static ColorOutlineBufferSource getBufferSource() {
      if (bufferSource == null) {
         bufferSource = new ColorOutlineBufferSource();
      }

      return bufferSource;
   }

   public ShaderRegistry() {

      this.register("outline", new ESPOutlineShaderPass());

      this.register("solid", new SolidShaderPass());

      this.register("rainbow", new RainbowShaderPass());

      this.register("gradient", new GradientShaderPass());

      this.register("bloom", new BloomShaderPass());

      this.register("custom", new CustomShaderPass());

      this.register("sky", new SkyboxShaderPass());

      this.register("dof", new DepthOfFieldPipeline());

      this.register("blur_mask", new BlurMaskPipeline());

      this.register("bloom_mask", new BloomMaskPipeline());

      this.register("glowy", new GlowyShaderPipeline());
   }

   static {
      submitNodes = new ColorSubmitNodeStorage();
   }

   public ShaderPass getPass(String id) {
      return this.passes.get(id);
   }

   public void reloadAll() {
      for (ShaderPass pass : this.passes.values()) {
         if (pass instanceof ReloadableShaderPass reloadable) {
            reloadable.reload((BaseShaderPass)pass);
         }
      }
   }

   private void register(String id, ShaderPass pass) {
      this.passes.put(id, pass);
   }

   public void render(String id, Runnable draw) {
      ShaderPass pass = this.getPass(id);
      pass.render(draw);
   }

   public static void finishFeatureRendering() {

      FeatureRenderDispatcher dispatcher = getFeatureDispatcher();
      dispatcher.renderAllFeatures();

      ColorSubmitNodeStorage nodes = getSubmitNodes();
      nodes.endFrame();
   }

   public static void setRenderColor(int color) {

      getBufferSource()
         .setColorArgb(color);
      getSubmitNodes()
         .setColorArgb(color);
   }

}
