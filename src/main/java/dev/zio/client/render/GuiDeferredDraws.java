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
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.vertex.MeshData;
import dev.zio.client.ClientAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Deferred client draws.
 */
public final class GuiDeferredDraws {
   private static final class DrawAction {
      private RenderType type;
      private final MeshData mesh;
      private final boolean scissorEnabled;
      private final int scissorX;
      private final int scissorY;
      private final int scissorWidth;
      private final int scissorHeight;

      private DrawAction(RenderType type, MeshData mesh, ScissorState scissor) {
         this.type = type;
         this.mesh = mesh;
         this.scissorEnabled = scissor.enabled();
         this.scissorX = scissor.x();
         this.scissorY = scissor.y();
         this.scissorWidth = scissor.width();
         this.scissorHeight = scissor.height();
      }
   }

   /** Tarea GPU arbitraria (efecto shader, blit) que corre en el drenaje. */
   private record TaskAction(Runnable task) {
   }

   /** Secuencia вариан mascara: quads capturados + setup + effecto. */
   private static final class MaskAction {
      private final List<DrawAction> maskDraws;
      private final Runnable setup;
      private final Runnable effect;

      private MaskAction(List<DrawAction> maskDraws, Runnable setup, Runnable effect) {
         this.maskDraws = maskDraws;
         this.setup = setup;
         this.effect = effect;
      }
   }

   private static final List<Object> QUEUE = new ArrayList<>();
   private static List<DrawAction> maskCapture;
   private static int drainCounter;
   /** Active mientras GameRendererMixin recorre extractGui. */
   public static boolean deferEnabled;

   private GuiDeferredDraws() {
   }

   public static void submit(RenderType type, MeshData mesh) {
      if (type == null || mesh == null) {
         return;
      }

      DrawAction action = new DrawAction(type, mesh, RenderSystem.getScissorStateForRenderTypeDraws());
      if (maskCapture != null) {
         maskCapture.add(action);
      } else if (deferEnabled) {
         QUEUE.add(action);
      } else {
         // fase no diferible (mundo/otros): submit inmediato como antes de
         // la deferral 26.1.2.
         type.draw(mesh);
      }
   }

   /**
    * Difiere la tarea cuando el drenaje esta activo barren la fase extract;
    * fuera de esa fase la ejecuta inmediato (rutas de mundo como ChamsModule
    * dependen de orden GPU inmediato).
    */
   public static void submitTask(Runnable task) {
      if (task != null) {
         if (deferEnabled) {
            QUEUE.add(new TaskAction(task));
         } else {
            task.run();
         }
      }
   }

   /** Comienza a capturar quads para una mascara; el caller debe cerrarla. */
   public static void beginMaskCapture() {
      maskCapture = new ArrayList<>();
   }

   /**
    * Cierra la captura de mascara y graba la secuencia completa: setup (clear
    * + swap de main target), draw de los quads capturados y effecto final.
    */
   public static void endMaskCapture(Runnable setup, Runnable effect) {
      List<DrawAction> captured = maskCapture;
      maskCapture = null;
      if (captured == null || captured.isEmpty()) {
         return;
      }

      QUEUE.add(new MaskAction(captured, setup, effect));
   }

   /** Queue-first-thing each frame drain. */
   public static boolean hasPending() {
      return !QUEUE.isEmpty();
   }

   public static void drawAll() {
      List<Object> queue = new ArrayList<>(QUEUE);
      QUEUE.clear();
      // diagnostico temporal 26.1.2: cada 300 drenes imprime el tamaño de la
      // cola (quads/texto/tasks) para confirmar si los meshes de texto
      // llegan al dren; borrar al cerrar el bug del HUD text.
      drainCounter++;
      if (drainCounter % 300 == 0) {
         int tasks = 0;
         int masks = 0;
         for (Object action : queue) {
            if (action instanceof TaskAction) {
               tasks++;
            } else if (action instanceof MaskAction) {
               masks++;
            }
         }
         System.err.println(
            "[zio-drain] drains=" + drainCounter + " actions=" + queue.size()
               + " tasks=" + tasks + " masks=" + masks
         );
      }
      ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
      for (int i = 0; i < queue.size(); i++) {
         Object action = queue.get(i);
         if (action instanceof DrawAction draw) {
            applyScissor(scissor, draw);
            draw.type.draw(draw.mesh);
            scissor.disable();
         } else if (action instanceof TaskAction task) {
            task.task().run();
         } else if (action instanceof MaskAction mask) {
            RenderTarget savedMain = ClientAccess.zioMinecraft.getMainRenderTarget();
            if (mask.setup != null) {
               mask.setup.run();
            }
            for (int j = 0; j < mask.maskDraws.size(); j++) {
               DrawAction draw = mask.maskDraws.get(j);
               applyScissor(scissor, draw);
               draw.type.draw(draw.mesh);
               scissor.disable();
            }
            RenderTargetUtils.setMainRenderTarget(savedMain);
            if (mask.effect != null) {
               mask.effect.run();
            }
         }
      }
      scissor.disable();
   }

   private static void applyScissor(ScissorState scissor, DrawAction draw) {
      if (draw.scissorEnabled) {
         scissor.enable(draw.scissorX, draw.scissorY, draw.scissorWidth, draw.scissorHeight);
      } else {
         scissor.disable();
      }
   }

   public static void clear() {
      QUEUE.clear();
      maskCapture = null;
   }
}
