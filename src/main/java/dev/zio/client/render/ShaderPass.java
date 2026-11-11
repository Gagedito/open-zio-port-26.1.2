/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

public interface ShaderPass {
   long START_TIME_MILLIS = System.currentTimeMillis();

   CompiledShaderProgram getProgram();

   void updateUniforms();

   void render(Runnable draw);

}
