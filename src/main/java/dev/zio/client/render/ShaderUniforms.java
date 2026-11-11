/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.awt.Color;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class ShaderUniforms {
   private static final FloatBuffer MATRIX_BUFFER;
   private final Object2IntMap<String> locations = new Object2IntOpenHashMap<>();
   private final int programId;

   public ShaderUniforms(int programId) {
      this.programId = programId;
   }

   static {
      MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);
   }

   public void set(String name, int textureUnit, RenderTarget target) {
      int activeTexture = GL13.GL_TEXTURE0 + textureUnit;
      GL13.glActiveTexture(activeTexture);
      GlTexture colorTexture = (GlTexture)target.getColorTexture();
      int textureId = colorTexture.glId();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      this.set(name, textureUnit);
   }

   public void set(String name, Matrix4f matrix) {
      FloatBuffer matrixBuffer = MATRIX_BUFFER;
      matrix.get(matrixBuffer);
      int location = getLocation(name);
      FloatBuffer uploadBuffer = MATRIX_BUFFER;
      GL20.glUniformMatrix4fv(location, false, uploadBuffer);
   }

   public void set(String name, float value) {
      int location = getLocation(name);
      GL20.glUniform1f(location, value);
   }

   public void set(String name, float x, float y) {
      int location = getLocation(name);
      GL20.glUniform2f(location, x, y);
   }

   public void set(String name, int value) {
      int location = getLocation(name);
      GL20.glUniform1i(location, value);
   }

   public void set(String name, boolean enabled) {
      int location = getLocation(name);
      int booleanValue = enabled ? 1 : 0;
      GL20.glUniform1i(location, booleanValue);
   }

   public void set(String name, Color color) {
      int location = getLocation(name);
      float normalizedRed = color.getRed();
      normalizedRed /= 255.0F;
      float normalizedGreen = color.getGreen() / 255.0F;
      float normalizedBlue = color.getBlue() / 255.0F;
      float alpha = color.getAlpha() / 255.0F;
      GL20.glUniform4f(location, normalizedRed, normalizedGreen, normalizedBlue, alpha);
   }

   private int getLocation(String name) {
      Object2IntMap<String> locationsForCache = this.locations;
      Object2IntFunction<String> resolveLocation = ignored -> {
         int program = this.programId;
         return GL20.glGetUniformLocation(program, name);
      };
      locationsForCache.computeIfAbsent(name, resolveLocation);
      Object2IntMap<String> locationsForRead = this.locations;
      return locationsForRead.getInt(name);
   }
}
