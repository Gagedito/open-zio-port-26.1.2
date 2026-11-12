/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui;

import dev.zio.client.render.GuiScaleUtils;

import dev.zio.client.ClientAccess;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Vector4d;
import org.joml.Vector4i;

public final class ScissorStack implements ClientAccess {
   private static final Deque<ScissorRegion> regions = new ArrayDeque<>();

   private ScissorStack() {
   }

   public static ScreenRectangle currentGuiBounds() {
      ScissorStack.ScissorRegion region = regions.peekLast();
      if (region == null) {
         return null;
      }

      Vector4i guiBounds = region.guiBounds();
      return new ScreenRectangle(guiBounds.x, guiBounds.y, guiBounds.z, guiBounds.w);
   }

   public static void pop() {

      getScissorState().disable();
      regions.removeLast();
      if (!regions.isEmpty()) {
         ScissorStack.ScissorRegion previousRegion = regions.getLast();
         Vector4i framebufferBounds = previousRegion.framebufferBounds();
         enableFramebufferScissor(framebufferBounds.x, framebufferBounds.y, framebufferBounds.z, framebufferBounds.w);
      }
   }

   public static void push(
      int x, int y, int width, int height
   ) {
      ScissorStack.ScissorRegion region = ScissorStack.ScissorRegion.create(
         x, y, width, height
      );
      Vector4i framebufferBounds = region.framebufferBounds();
      enableFramebufferScissor(framebufferBounds.x, framebufferBounds.y, framebufferBounds.z, framebufferBounds.w);
      regions.addLast(region);
   }

   private static ScissorState getScissorState() {
      return RenderSystem.getScissorStateForRenderTypeDraws();
   }

   public static void pushIntersection(
      int x, int y, int width, int height
   ) {
      ScissorStack.ScissorRegion previousRegion = regions.getLast();
      if (previousRegion != null) {
         Vector4i previousBounds = previousRegion.guiBounds();
         int intersectionX = Math.max(previousBounds.x, x);
         int intersectionY = Math.max(previousBounds.y, y);
         int intersectionWidth = Math.min(previousBounds.x + previousBounds.z, x + width) - intersectionX;
         int intersectionHeight = Math.min(previousBounds.y + previousBounds.w, y + height) - intersectionY;
         push(intersectionX, intersectionY, intersectionWidth, intersectionHeight);
      } else {
         push(x, y, width, height);
      }
   }

   public static void enableFramebufferScissor(
      int x, int y, int width, int height
   ) {
      getScissorState()
         .enable(x, y, Math.max(width, 0), Math.max(height, 0));
   }

   record ScissorRegion(
      Vector4i guiBounds,
      Vector4i framebufferBounds
   ) {
      private static final Vector4d framebufferBoundsScratch = new Vector4d();

      public static ScissorStack.ScissorRegion create(
         int x, int y, int width, int height
      ) {
         double framebufferScale = ClientAccess.zioMinecraft.getWindow().getGuiScale() * GuiScaleUtils.getScaleMultiplier();
         float guiHeight = (float)(
            ClientAccess.zioMinecraft.getWindow().getHeight()
               / framebufferScale
         );
         framebufferBoundsScratch.set(x, guiHeight - y - height, width, height)
            .mul(framebufferScale)
            .sub(0.0, 0.0, 0.0, 1.0);
         Vector4i guiBounds = new Vector4i(x, y, width, height);
         Vector4i framebufferBounds = new Vector4i(framebufferBoundsScratch, 0);
         return new ScissorStack.ScissorRegion(guiBounds, framebufferBounds);
      }
   }
}
