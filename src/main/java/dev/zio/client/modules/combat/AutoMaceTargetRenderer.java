/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.render.VertexGeometry;
import dev.zio.client.render.MeshBuffer;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.render.MeshDrawContext;
import dev.zio.client.render.ClientRenderTypes;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class AutoMaceTargetRenderer implements ClientAccess {
   private static final long MARKER_LIFETIME_MILLIS = 1500L;
   private final List<TargetMarker> markers = new ArrayList<>();
   private final ElapsedTimer markerTimer = new ElapsedTimer();
   private final AutoMaceModule module;

   public void addTargetMarker(Entity target) {
      if (target != null) {
         if (this.markerTimer.hasElapsedAndReset(MARKER_LIFETIME_MILLIS)) {
            Supplier<Vec3> positionSupplier = () -> {
               float tickDelta = RenderUtils.getPartialTick();

               return target.getPosition(tickDelta);
            };

            float targetWidth = target.getBbWidth();

            float radius = targetWidth * 2.0F;
            AutoMaceTargetRenderer.TargetMarker marker = new AutoMaceTargetRenderer.TargetMarker(positionSupplier, radius, System.currentTimeMillis() + MARKER_LIFETIME_MILLIS);
            this.markers.add(marker);
         }
      }
   }

   public AutoMaceTargetRenderer(AutoMaceModule module) {
      this.module = module;
   }

   public void reset() {
      this.markers.clear();

      this.markerTimer.clear();
   }

   private void drawMarker(
      PoseStack poseStack, MeshBuffer vertices, AutoMaceTargetRenderer.TargetMarker marker, int segments
   ) {
      Pose pose = poseStack.last();
      Matrix4f matrix = pose.pose();

      Camera camera = ClientAccess.zioMinecraft.getEntityRenderDispatcher().camera;

      Vec3 cameraPosition = camera.position();
      Supplier<Vec3> positionSupplier = marker.positionSupplier();
      Vec3 markerPosition = positionSupplier.get();
      Vec3 relativeVector = markerPosition.subtract(cameraPosition);
      Vector3f relativePosition = relativeVector.toVector3f();

      long expiresAtMillis = marker.expiresAtMillis();

      float remainingRatio = ((float)(expiresAtMillis - System.currentTimeMillis())) / 1500.0F;

      float capturedRemainingRatio = remainingRatio;
      float progress = 1.0F - Mth.clamp(capturedRemainingRatio, 0.0F, 1.0F);
      float radius = progress * marker.radius();

      int configuredArgb = (this.module.colorSetting.getValue()).hashCode();

      float alpha = 1.0F - progress;

      int color = ARGB.multiplyAlpha(configuredArgb, alpha);
      VertexGeometry.forEachCircleSegment(radius, segments, (firstPoint, secondPoint) -> {
         float firstX = relativePosition.x + firstPoint.x;
         float firstY = relativePosition.y;
         float firstZ = relativePosition.z + firstPoint.z;
         float secondX = relativePosition.x + secondPoint.x;
         float secondY = relativePosition.y;
         float secondZ = relativePosition.z + secondPoint.z;
         VertexGeometry.appendLine(vertices, pose, firstX, firstY, firstZ, secondX, secondY, secondZ, 1.0F, color);
      });
   }

   @EventHandler
   public void renderMarkers(WorldRenderEvent.Main event) {
      this.markers.removeIf(expiredMarker -> System.currentTimeMillis() >= expiredMarker.expiresAtMillis());
      if (!this.markers.isEmpty()) {
         MeshDrawContext.getBuffer()
            .begin(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
         for (TargetMarker marker : this.markers) {
            PoseStack poseStack = event.getPoseStack();
            MeshBuffer vertices = MeshDrawContext.getBuffer();

            this.drawMarker(poseStack, vertices, marker, 32);
         }

         MeshDrawContext.getBuffer()
            .draw(
               ClientRenderTypes.worldLines()
            );
      }
   }

   private record TargetMarker(
      Supplier<Vec3> positionSupplier,
      float radius,
      long expiresAtMillis
   ) {}
}
