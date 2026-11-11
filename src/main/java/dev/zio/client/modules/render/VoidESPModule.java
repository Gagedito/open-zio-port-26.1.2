/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import java.util.function.Predicate;
import dev.zio.client.ClientAccess;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.render.WorldBoxRenderer;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class VoidESPModule extends Module {
   private final Setting<Float> lineWidthSetting;
   private final Setting<Integer> heightSetting;
   private final Setting<Integer> radiusSetting;
   final Setting<Boolean> fadeSetting;
   private final Setting<Float> fadeRadiusSetting;
   final Setting<Boolean> colorsSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> lineSetting;
   private final Set voidBlocks;
   private boolean resetPending;

   private boolean shouldRemoveVoidBlock(BlockPos position) {
      GameRenderer gameRenderer = ClientAccess.zioMinecraft.gameRenderer;
      Vec3 cameraPosition = (gameRenderer.getMainCamera()).position();
      Integer radiusValue = this.radiusSetting.getValue();
      if (!position.closerToCenterThan(cameraPosition, radiusValue.intValue())) {
         return true;
      }

      return isBedrock(position);
   }

   private boolean isVoidBlock(BlockPos position) {
      return position.getY() == ClientAccess.zioMinecraft.level.getMinY() && !isBedrock(position);
   }


   private static boolean isBedrock(BlockPos position) {
      return WorldUtils.getBlock(position) == Blocks.BEDROCK;
   }

   private static int getColumnHeight(VoidESPModule owner, BlockPos position) {
      int columnHeight = (owner.heightSetting.getValue());
      if (columnHeight <= 1) {
         return columnHeight;
      }

      for (int offset = 0; offset <= columnHeight; offset++) {
         ClientLevel level = ClientAccess.zioMinecraft.level;
         BlockPos columnPosition = position.above(offset);
         BlockState blockState = level.getBlockState(columnPosition);
         if (!blockState.canBeReplaced()) {
            columnHeight -= columnHeight - offset;
         }
      }

      return Math.max(columnHeight, 1);
   }

   @Override
   public String getInfo() {
      int blockCount = this.voidBlocks.size();
      return String.valueOf(blockCount);
   }

   @Override
   public void onDisable() {
      this.voidBlocks.clear();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (this.resetPending) {
         this.voidBlocks.clear();
         this.resetPending = false;
      }

      this.voidBlocks.removeIf((java.util.function.Predicate<net.minecraft.core.BlockPos>)this::shouldRemoveVoidBlock);
      GameRenderer gameRenderer = ClientAccess.zioMinecraft.gameRenderer;
      Vec3 cameraPosition = (gameRenderer.getMainCamera()).position();
      float scanRadius = (this.radiusSetting.getValue()).intValue();
      Stream voidBlockStream = WorldUtils.streamSphereBlocks(cameraPosition, scanRadius).filter((java.util.function.Predicate<net.minecraft.core.BlockPos>)this::isVoidBlock);
      Set destinationBlocks = this.voidBlocks;
      voidBlockStream.forEach((java.util.function.Consumer<net.minecraft.core.BlockPos>)destinationBlocks::add);
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      Iterator positions = this.voidBlocks.iterator();

      while (positions.hasNext()) {
         BlockPos position = (BlockPos)positions.next();
         AABB blockBox = new AABB(position);
         int baseY = position.getY();

         double maxY = baseY + getColumnHeight(this, position);
         AABB box = blockBox.setMaxY(maxY);

         if (RotationManager.isVisible(box)) {
            GameRenderer gameRenderer = ClientAccess.zioMinecraft.gameRenderer;
            Vec3 cameraPosition = (gameRenderer.getMainCamera()).position();
            Color fillColor = this.fillSetting.getValue();
            Color lineColor = this.lineSetting.getValue();
            Vec3 blockCenter = position.getCenter();
            double distance = cameraPosition.distanceTo(blockCenter);
            Boolean fadeEnabled = this.fadeSetting.getValue();
            if (fadeEnabled) {
               Float fadeRadiusValue = this.fadeRadiusSetting.getValue();
               if (distance >= fadeRadiusValue.floatValue()) {

                  Float fadeStartValue = this.fadeRadiusSetting.getValue();
                  double distancePastFadeStart = distance - fadeStartValue.floatValue();
                  float renderRadius = (this.radiusSetting.getValue()).intValue();
                  Float fadeRangeStartValue = this.fadeRadiusSetting.getValue();
                  double fadeProgress = distancePastFadeStart / (renderRadius - fadeRangeStartValue);

                  float alphaFactor = 1.0F - (float)Mth.clamp(fadeProgress, 0.0D, 1.0D);
                  Color fillAlphaSource = fillColor;
                  fillColor = ColorUtils.withAlpha(
                     fillColor, (int)(alphaFactor * fillAlphaSource.getAlpha())
                  );
                  Color lineAlphaSource = lineColor;
                  lineColor = ColorUtils.withAlpha(
                     lineColor, (int)(alphaFactor * lineAlphaSource.getAlpha())
                  );
               }
            }

            WorldBoxRenderer.fillBox(
               event.getPoseStack(), box, fillColor
            );
            PoseStack poseStack = event.getPoseStack();
            Float lineWidthValue = this.lineWidthSetting.getValue();
            WorldBoxRenderer.outlineBox(poseStack, box, lineColor, lineWidthValue);
         }
      }
   }

   @EventHandler
   private void onPacketReceive(
      PacketEvent.Receive event
   ) {
      if (!(event.getPacket() instanceof ClientboundRespawnPacket)) {
         if (!(event.getPacket() instanceof ClientboundLoginPacket)) {
            return;
         }
      }

      this.resetPending = true;
   }

   public VoidESPModule() {
      super("VoidESP", "Highlights void blocks.", ModuleCategory.RENDER, new String[0]);
      this.lineWidthSetting = this.add(new NumberSetting<>(new String("LineWidth"), 1.0F, 0.1F, 3.0F));
      String heightKey = new String("Height");
      this.heightSetting = this.add(new NumberSetting<>(heightKey, 0, -1, 4).withUnit(new String("m")));
      this.radiusSetting = this.add(new NumberSetting<>(new String("Radius"), 10, 1, 16).withUnit(new String("m")));
      this.fadeSetting = this.add(new BooleanSetting(new String("Fade"), true));
      Predicate<Object> fadeRadiusVisibility = ignored -> {
         return this.fadeSetting.getValue();
      };
      this.fadeRadiusSetting = this.add(new NumberSetting<>(new String("FadeRadius"), 7.0F, 1.0F, 16.0F, fadeRadiusVisibility).withUnit(new String("m")));
      this.colorsSetting = this.add(new BooleanSetting(new String("Colors"), true).markAsGroup().markVisibilityDependency());
      Predicate<Object> fillVisibility = ignored -> {
         Setting<Boolean> colors = this.colorsSetting;
         return colors.isExpanded();
      };
      this.fillSetting = this.add(new ColorSetting(new String("Fill"), new Color(822018048, true), fillVisibility));
      Predicate<Object> lineVisibility = ignored -> {
         Setting<Boolean> colors = this.colorsSetting;
         return colors.isExpanded();
      };
      this.lineSetting = this.add(new ColorSetting(new String("Line"), new Color(-1140916224, true), lineVisibility));
      this.voidBlocks = new HashSet();
   }

}
