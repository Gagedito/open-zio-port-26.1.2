/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.zio.client.ClientAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix3x2fStack;

public record MapTooltipComponent(int mapId, MapItemSavedData mapData)
   implements ClientAccess, ClientTooltipComponent {
   private static final int MAP_SIZE = 96;
   private static final int MAP_TEXTURE_SIZE = 128;
   private static final int BACKGROUND_TEXTURE_SIZE = 64;
   private static final int BORDER = 7;
   private static final int TOOLTIP_SIZE = MAP_SIZE + BORDER * 2;
   private static final Identifier BACKGROUND_TEXTURE =
      Identifier.parse("textures/map/map_background.png");
   private static final MapRenderState MAP_RENDER_STATE = new MapRenderState();

   @Override
   public int getHeight(Font font) {
      return TOOLTIP_SIZE;
   }

   @Override
   public int getWidth(Font font) {
      return TOOLTIP_SIZE;
   }

   @Override
   public void extractImage(Font font, int x, int y, int tooltipWidth, int tooltipHeight, GuiGraphicsExtractor graphics) {
      Matrix3x2fStack poses = graphics.pose();
      poses.pushMatrix();
      try {
         poses.translate(x, y);
         RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
         graphics.blit(pipeline, BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F,
            TOOLTIP_SIZE, TOOLTIP_SIZE, BACKGROUND_TEXTURE_SIZE, BACKGROUND_TEXTURE_SIZE, 0xFFFFFFFF);
         poses.translate(BORDER, BORDER);
         poses.scale((float)MAP_SIZE / MAP_TEXTURE_SIZE, (float)MAP_SIZE / MAP_TEXTURE_SIZE);
         MapRenderer renderer = zioMinecraft.getMapRenderer();
         renderer.extractRenderState(new MapId(this.mapId), this.mapData, MAP_RENDER_STATE);
         graphics.map(MAP_RENDER_STATE);
         (zioMinecraft.renderBuffers().bufferSource()).endBatch();
      } finally {
         poses.popMatrix();
      }
   }
}
