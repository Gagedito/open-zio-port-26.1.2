/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import dev.zio.client.ClientAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

public final class GuiProjectionUtils implements ClientAccess {
   public static final ProjectionMatrixBuffer GUI_PROJECTION_CACHE =
      new ProjectionMatrixBuffer("gui_mio");

   private record SavedGuiProjection(GpuBufferSlice buffer, ProjectionType type, Matrix4f modelView) {
   }

   private static final java.util.ArrayDeque<SavedGuiProjection> PROJECTION_STACK =
      new java.util.ArrayDeque<>();

   public static void restoreProjection() {
      SavedGuiProjection saved = PROJECTION_STACK.pollLast();
      if (saved == null) {
         return;
      }
      RenderSystem.setProjectionMatrix(saved.buffer(), saved.type());
      RenderSystem.getModelViewMatrix().set(saved.modelView());
   }

   /**
    * Drena la pila completa. Se llama al cierre de cada fase de extraccion
    * GUI para que una fuga en un frame no envenene el estado global siguiente
    * (el bake del GuiItemAtlas reusa el modelView vigente).
    */
   public static void forceRestoreAll() {
      while (!PROJECTION_STACK.isEmpty()) {
         restoreProjection();
      }
   }

   public static void beginGuiProjection() {
      if (!PROJECTION_STACK.isEmpty()) {
         return;
      }
      Window window = zioMinecraft.getWindow();
      float guiWidth = (float)window.getWidth() / (float)window.getGuiScale();
      float guiHeight = (float)window.getHeight() / (float)window.getGuiScale();
      Matrix4f modelView = RenderSystem.getModelViewMatrix();
      PROJECTION_STACK.push(new SavedGuiProjection(
         RenderSystem.getProjectionMatrixBuffer(),
         RenderSystem.getProjectionType(),
         new Matrix4f(modelView)
      ));
      Matrix4f projection = new Matrix4f().setOrtho(0.0F, guiWidth, guiHeight, 0.0F, 1000.0F, 11000.0F);
      RenderSystem.setProjectionMatrix(
         GUI_PROJECTION_CACHE.getBuffer(projection), ProjectionType.ORTHOGRAPHIC
      );
      modelView.identity().translate(0.0F, 0.0F, -11000.0F);
   }

   public static PoseStack toPoseStack(Matrix3x2fStack guiPose) {
      PoseStack poseStack = new PoseStack();
      Pose pose = poseStack.last();
      Matrix4f matrix = pose.pose();
      matrix.set(
         guiPose.m00(), guiPose.m01(), 0.0F, 0.0F,
         guiPose.m10(), guiPose.m11(), 0.0F, 0.0F,
         0.0F, 0.0F, 1.0F, 0.0F,
         guiPose.m20(), guiPose.m21(), 0.0F, 1.0F
      );
      return poseStack;
   }

   public static PoseStack toPoseStack(GuiGraphicsExtractor graphics) {
      return toPoseStack(graphics.pose());
   }

   private GuiProjectionUtils() {
   }
}
