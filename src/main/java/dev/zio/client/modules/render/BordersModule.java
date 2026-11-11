/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.render.WorldBoxRenderer;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

public class BordersModule extends Module {
   private final Setting<Color> chunkSetting;
   private final Setting<Color> mapSetting;
   private final Setting<Float> lineWidthSetting;
   private final Setting<Integer> mapSizeSetting;
   private final Setting<Integer> levelSetting;

   private static int getChunkRegionSize() {
      return 16;
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      Color chunkColor = this.chunkSetting.getValue();
      if (chunkColor.getAlpha() != 0) {

         BordersModule.Region chunkRegion = this.createChunkRegion();
         PoseStack chunkPoseStack = event.getPoseStack();
         Color chunkDrawColor = this.chunkSetting.getValue();
         int chunkLevel = (this.levelSetting.getValue());
         float chunkDrawLineWidth = (this.lineWidthSetting.getValue());
         chunkRegion.render(chunkPoseStack, chunkDrawColor, chunkLevel, chunkDrawLineWidth);
      }

      Color mapColor = this.mapSetting.getValue();
      if (mapColor.getAlpha() != 0) {

         BordersModule.Region mapRegion = this.createMapRegion();
         PoseStack mapPoseStack = event.getPoseStack();
         Color mapDrawColor = this.mapSetting.getValue();
         int mapDrawLevel = (this.levelSetting.getValue());
         float mapDrawLineWidth = (this.lineWidthSetting.getValue());
         mapRegion.render(mapPoseStack, mapDrawColor, mapDrawLevel, mapDrawLineWidth);
      }
   }

   public BordersModule() {
      super("Borders", "Shows region borders on your screen.", ModuleCategory.RENDER, new String[0]);
      this.chunkSetting = this.add(new ColorSetting("Chunk", new Color(-65536, true)));
      this.mapSetting = this.add(new ColorSetting("Map", new Color(-65281, true)));
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F));
      this.mapSizeSetting = this.add(new NumberSetting<>("MapSize", 0, 0, 4));
      this.levelSetting = this.add(new NumberSetting<>("Level", -1, -1, 320));
      SettingLabelCondition minimumBoundary = SettingLabelCondition.MIN;
      this.levelSetting.setBoundaryLabel("~", minimumBoundary);
   }

   private Region createChunkRegion() {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      ChunkPos chunk = player.chunkPosition();
      return new BordersModule.Region(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), getChunkRegionSize());
   }

   private Region createMapRegion() {
      int mapSize = 128 * (1 << this.mapSizeSetting.getValue());
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      int mapColumn = Mth.floor((player.getBlockX() + 64.0D) / mapSize);
      int mapRow = Mth.floor((player.getBlockZ() + 64.0D) / mapSize);
      int centerX = mapColumn * mapSize + mapSize / 2 - 64;
      int centerZ = mapRow * mapSize + mapSize / 2 - 64;
      return new BordersModule.Region(centerX, centerZ, mapSize);
   }

   private record Region(
      int centerX,
      int centerZ,
      int size
   ) {

      public void render(
         PoseStack poseStack, Color color, int level, float lineWidth
      ) {
         double renderY = level;
         if (level == -1) {
            LocalPlayer player = ClientAccess.zioMinecraft.player;
            float partialTick = RenderUtils.getPartialTick();
            renderY = player.getPosition(partialTick).y;
         }

         AABB bounds = this.getBounds(renderY);
         WorldBoxRenderer.outlineBox(poseStack, bounds, color, lineWidth);
      }

      public AABB getBounds(double level) {
         int regionSize = this.size;

         int halfSize = regionSize / 2;
         return new AABB(
            this.centerX - halfSize,
            level,
            this.centerZ - halfSize,
            this.centerX + halfSize,
            level,
            this.centerZ + halfSize
         );
      }

   }
}
