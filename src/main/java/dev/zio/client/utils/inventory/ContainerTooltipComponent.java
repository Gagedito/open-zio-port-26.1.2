/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import dev.zio.client.utils.KeyUtils;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.AbstractContainerScreenRenderTooltipEvent;
import dev.zio.client.modules.render.TooltipsModule;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix3x2fStack;

public record ContainerTooltipComponent(List<ItemStack> items)
   implements ClientAccess,
   ClientTooltipComponent {
   private static final TooltipsModule tooltips;
   private static final MapRenderState sharedMapRenderState;

   @Override
   public int getHeight(Font font) {
      return 53;
   }

   @Override
   public int getWidth(Font font) {
      return 155;
   }

   @Override
   public void extractImage(Font font, int x, int y, int tooltipWidth, int tooltipHeight, GuiGraphicsExtractor graphics) {
      int column = 0;
      int row = 0;
      int mouseX = AbstractContainerScreenRenderTooltipEvent.mouseX;
      int mouseY = AbstractContainerScreenRenderTooltipEvent.mouseY;
      ItemStack hoveredItem = null;
      for (ItemStack item : this.items) {
         int itemX = x + (17) * column;
         int itemY = y + (17) * row;
         boolean markedCount = item.getCount()
            == 69;
         if (mouseX >= itemX) {
            if (mouseX <= itemX + (17) && mouseY >= itemY) {
               if (mouseY <= itemY + (17) && hoveredItem == null) {
                  hoveredItem = item;
               }
            }
         }

         DataComponentType<MapId> mapIdType = DataComponents.MAP_ID;
         MapId missingMapId = new MapId(-1);
         int mapId = (((DataComponentHolder)item).getOrDefault(mapIdType, missingMapId)).id();
         MapItemSavedData mapData = ClientContext.mapDataCache.getMapData(item, mapId);
         if ((tooltips.mapOverlaySetting.getValue()) && mapId != -1 && mapData != null) {
            float mapX = itemX + 1;
            float mapY = itemY + 1;
            renderMapOverlay(graphics, mapData, mapId, mapX, mapY);
            if (!KeyUtils.isPhysicalKeyDown(342)) {
               int mapDecorationX = itemX + 1;
               int mapDecorationY = itemY + 1;
               graphics.itemDecorations(font, item, mapDecorationX, mapDecorationY);
            }
         } else {
            int renderX = itemX + 1;
            int renderY = itemY + 1;
            graphics.item(item, renderX, renderY);
            int decorationX = itemX + 1;
            int decorationY = itemY + 1;
            String countLabel;
            if (markedCount) {
               ChatFormatting markFormatting = ChatFormatting.RED;
               String markPrefix = String.valueOf(markFormatting);
               countLabel = markPrefix + "0";
            } else {
               countLabel = null;
            }

            graphics.itemDecorations(font, item, decorationX, decorationY, countLabel);
         }

         column++;
         if (column >= (9)) {
            column = 0;
            row++;
         }
      }

      if (hoveredItem != null) {
         Item airItem = Items.AIR;
         if (!hoveredItem.is(airItem)) {
            Font hoverFont = ClientAccess.zioMinecraft.font;
            graphics.setTooltipForNextFrame(hoverFont, hoveredItem, mouseX, mouseY);
         }
      }
   }

   public static void renderMapOverlay(
      GuiGraphicsExtractor graphics, MapItemSavedData data, int id, float x, float y
   ) {
      if (data == null) {
         return;
      }

      Matrix3x2fStack poses = graphics.pose();
      RenderBuffers renderBuffers = ClientAccess.zioMinecraft.renderBuffers();
      BufferSource buffers = renderBuffers.bufferSource();
      poses.pushMatrix();
      poses.translate(x, y);
      float scaleX = 0.125F;
      float scaleY = 0.125F;
      poses.scale(scaleX, scaleY);
      poses.translate(0.0F, 0.0F);
      MapRenderer mapRenderer = ClientAccess.zioMinecraft.getMapRenderer();
      MapId mapId = new MapId(id);
      MapRenderState extractState = sharedMapRenderState;
      mapRenderer.extractRenderState(mapId, data, extractState);
      MapRenderState submitState = sharedMapRenderState;
      graphics.map(submitState);
      buffers.endBatch();
      poses.popMatrix();
   }

   static {
      tooltips = (TooltipsModule)ClientContext.getModule(TooltipsModule.class);
      sharedMapRenderState = new MapRenderState();
   }

}
