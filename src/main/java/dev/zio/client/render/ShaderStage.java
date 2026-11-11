/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;

public interface ShaderStage {
   CompiledShaderProgram getProgram();

   void updateUniforms();

   RenderTarget getOutputTarget();

   void apply(RenderTarget inputTarget);
}
