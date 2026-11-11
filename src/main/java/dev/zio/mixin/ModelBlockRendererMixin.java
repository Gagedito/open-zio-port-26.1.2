/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.render.NoRenderModule;
import dev.zio.client.modules.render.XrayModule;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
   private static final XrayModule xrayModule = ClientContext.getModule(XrayModule.class);
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

   @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
   private void suppressHiddenBlockEntityMesh(
      BlockQuadOutput output,
      float quadOriginX,
      float quadOriginY,
      float quadOriginZ,
      BlockAndTintGetter level,
      BlockPos blockPos,
      BlockState blockState,
      BlockStateModel blockStateModel,
      long seed,
      CallbackInfo callback
   ) {
      if (XrayModule.getInstance()
            .isToggled()
         && !XrayModule.getInstance()
            .shouldRenderBlock(
               blockPos,
               blockState.getBlock()
            )
         && XrayModule.getInstance()
            .shouldHideUnselectedBlocks()) {
         callback.cancel();
      } else {
         if (noRenderModule.shouldHideBlockEntity(blockState)) {
            callback.cancel();
         }
      }
   }


}
