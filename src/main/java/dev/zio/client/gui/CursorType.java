/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui;

import com.mojang.blaze3d.platform.Window;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public enum CursorType {
   STANDARD(() -> GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)),
   POINTER(() -> GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR)),
   INPUT(() -> GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR));

   private static CursorType currentCursor = STANDARD;
   private final Supplier cursorSupplier;
   private long cursorHandle;

   public long getHandle() {
      long cachedHandle = this.cursorHandle;

      if (cachedHandle == -1L) {
         Long createdHandle = (Long)this.cursorSupplier.get();
         this.cursorHandle = createdHandle;
      }

      return this.cursorHandle;
   }

   public void apply() {
      if (this != currentCursor) {
         Minecraft minecraft = Minecraft.getInstance();
         Window window = minecraft.getWindow();
         long windowHandle = window.handle();
         long newCursorHandle = this.getHandle();
         long targetWindowHandle = windowHandle;
         GLFW.glfwSetCursor(targetWindowHandle, newCursorHandle);
         currentCursor = this;
      }
   }

   CursorType(Supplier cursorSupplier) {

      this.cursorHandle = -1L;
      this.cursorSupplier = cursorSupplier;
   }

}
