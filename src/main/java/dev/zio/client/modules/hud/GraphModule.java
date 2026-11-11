/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import dev.zio.client.ClientContext;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.render.MeshBuffer;
import dev.zio.client.render.MeshDrawContext;
import dev.zio.client.render.VertexGeometry;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.render.ClientRenderTypes;
import dev.zio.client.render.ResizableHudWidgetRenderer;
import dev.zio.client.modules.HudWidgetModule;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public class GraphModule extends HudWidgetModule {
   private final Setting<Float> ceilSetting;
   private final Setting<Integer> heightSetting;
   private final Setting<Integer> widthSetting;
   private final List<Double> speedSamples;

   @Override
   public void onEnable() {
      this.speedSamples.clear();
      for (int sampleIndex = 0; sampleIndex < 150; sampleIndex++) {
         this.speedSamples.add(0.0D);
      }
   }

   @Override
   public float[] calculateDimensions() {

      float[] dimensions = new float[2];
      Integer width = this.widthSetting.getValue();
      dimensions[0] = width.intValue();
      Integer height = this.heightSetting.getValue();
      dimensions[1] = height.intValue();
      return dimensions;
   }

   @Override
   public void renderContent(PoseStack poses) {
      if (this.speedSamples.size() < 2) {
         return;
      }
      MeshBuffer buffer = MeshDrawContext.getBuffer();
      buffer.begin(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
      for (int sampleIndex = 1; sampleIndex < this.speedSamples.size(); sampleIndex++) {
         float previousY = this.getSampleY(this.speedSamples.get(sampleIndex - 1));
         float currentY = this.getSampleY(this.speedSamples.get(sampleIndex));
         VertexGeometry.appendLine(
            buffer, poses.last(), sampleIndex - 1, previousY, 0.0F, sampleIndex, currentY, 0.0F,
            1.0F, this.getSampleColor(previousY, sampleIndex - 1), this.getSampleColor(currentY, sampleIndex)
         );
      }
      buffer.draw(ClientRenderTypes.worldLines());
   }

   private float getSampleY(double sample) {
      int height = this.heightSetting.getValue();
      double clippedSample = Math.min(sample, this.ceilSetting.getValue());
      return 1.0F + (float)((height - 2) * (1.0D - clippedSample / this.ceilSetting.getValue()));
   }

   private int getSampleColor(float sampleY, int sampleIndex) {
      Color baseColor = this.getHudColor(sampleY + this.renderer.getY());
      return ColorUtils.withAlpha(baseColor, this.getSampleAlpha(sampleIndex)).hashCode();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent ignoredEvent) {
      List<Double> samplesForAppend = this.speedSamples;
      double speed = ClientContext.movementSpeedTracker.getDisplaySpeed();
      samplesForAppend.add(speed);

      while (this.speedSamples.size() > this.widthSetting.getValue()) {
         this.speedSamples.remove(0);
      }
   }

   private int getSampleAlpha(int sampleIndex) {

      if (sampleIndex <= 10) {
         float leadingAlpha = sampleIndex;

         leadingAlpha /= 10.0F;

         leadingAlpha *= 255.0F;

         float leadingMaximum = 255.0F;
         return (int)Mth.clamp(leadingAlpha, 0.0F, leadingMaximum);
      } else {
         Integer widthForThreshold = this.widthSetting.getValue();
         int fadePositionOperandScratch = widthForThreshold;

         if (sampleIndex >= fadePositionOperandScratch - 10) {

            float trailingStart = 1.0F;
            Integer widthForRamp = this.widthSetting.getValue();
            int relativePosition = sampleIndex - widthForRamp;

            float trailingPosition = relativePosition
               + 10;

            float trailingProgress = trailingStart - trailingPosition / 10.0F;

            float trailingAlpha = trailingProgress * 255.0F;

            float trailingMaximum = 255.0F;
            return (int)Mth.clamp(trailingAlpha, 0.0F, trailingMaximum);
         } else {

            return 255;
         }
      }
   }

   public GraphModule() {
      super("Graph", new String[0]);

      this.ceilSetting = this.add(new NumberSetting<>(
         "Ceil",
         26.0F,
         10.0F,
         100.0F
      ));
      this.heightSetting = this.add(new NumberSetting<>(
         "Height",
         25,
         10,
         30
      ));
      this.widthSetting = this.add(new NumberSetting<>(
         "Width",
         75,
         50,
         100
      ));
      this.speedSamples = new ArrayList<>();
      this.setRenderer(new ResizableHudWidgetRenderer(this));
   }

}
