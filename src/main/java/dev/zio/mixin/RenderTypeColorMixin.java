/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.modules.render.ChamsModule;
import dev.zio.client.ClientContext;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.modules.render.GlintModule;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.render.ClientRenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderType.class)
public class RenderTypeColorMixin {
   private static GlintModule glintModule = ClientContext.getModule(GlintModule.class);
   private static ChamsModule chamsGlintModule = ClientContext.getModule(ChamsModule.class);
   @Shadow
   @Final
   public RenderSetup state;

   @ModifyArg(
      method = "draw",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
      ),
      index = 1
   )
   private Vector4fc applyRenderColorModifiers(Vector4fc originalColor) {
      Vector4f mutableColor = new Vector4f(originalColor);
      if (ClientRenderPipelines.isGlint(this.state.pipeline)
         && glintModule.isToggled()) {
         mutableColor.mul(glintModule.getGlintColor());
      }

      if (ClientRenderPipelines.isGlint(this.state.pipeline)
         && ChamsModule.renderingGlintBatch) {
         mutableColor.set(
            chamsGlintModule.getGlintColor(),
            1.0F
         );
      }

      if (RenderUtils.getRenderColor()[3]
         != 255) {
         mutableColor.mul(
            1.0F,
            1.0F,
            1.0F,
            Mth.clamp(
               RenderUtils.getRenderColor()[3]
                  / 255.0F,
               0.0F,
               1.0F
            )
         );
      }

      return mutableColor;
   }

   @Unique
   private static void zio$bindRenderTypeColorModules(ModuleRegistry moduleRegistry) {
      glintModule = (GlintModule)moduleRegistry.getModule(
         GlintModule.class
      );
      chamsGlintModule = (ChamsModule)moduleRegistry.getModule(
         ChamsModule.class
      );
   }
}
