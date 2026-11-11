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
import dev.zio.client.ClientContext;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.BlockOutlineRenderEvent;
import dev.zio.client.render.WorldBoxRenderer;
import dev.zio.client.render.VertexGeometry;
import dev.zio.client.render.MeshBuffer;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.render.ClientRenderTypes;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.exploit.ReachModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CoralFanBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

public class HighlightModule extends Module {
   private static final Set EXCLUDED_AIR_PLACE_BLOCKS;
   private static final MeshBuffer FILL_BUFFER;
   private static final MeshBuffer OUTLINE_BUFFER;
   private static ReachModule reach;
   private final Setting<HighlightMode> modeSetting;
   private final Setting<Boolean> depthSetting;
   private final Setting<Boolean> unreachableSetting;
   private final Setting<Float> lineWidthSetting;
   private final Setting<Color> colorSetting;
   private final Setting<Color> fillColorSetting;

   private BlockHitResult getHighlightHitResult() {
      ReachModule reachModule = reach;
      if (reachModule.isToggled()) {
         Setting<Boolean> ghostHandSetting = reach.ghostHandSetting;
         Boolean ghostHandEnabled = ghostHandSetting.getValue();
         if (ghostHandEnabled) {
            ReachModule ghostHandReceiver = reach;

            BlockHitResult ghostHandHit = ghostHandReceiver.getGhostHandHitResult();
            if (ghostHandHit != null) {
               if (ghostHandHit.getType() != Type.MISS) {
                  if (ghostHandHit.getBlockPos() != null) {
                     ghostHandReceiver = reach;

                     return ghostHandReceiver.getGhostHandHitResult();
                  }
               }
            }
         }
      }

      if (!this.isAirPlaceHighlight()) {
         if (ClientAccess.zioMinecraft.hitResult instanceof BlockHitResult crosshairHit) {
            if (crosshairHit.getType() != Type.MISS) {
               return crosshairHit;
            }
         }

         return null;
      } else {

         return ReachModule.raycastForPlacement() instanceof BlockHitResult placementHit
            ? placementHit
            : null;
      }
   }

   private boolean isAirPlaceHighlight() {
      ReachModule reachModule = reach;
      if (!reachModule.isToggled()) {
         return false;
      }
      Setting<Boolean> airPlaceSetting = reach.airPlaceSetting;
      Boolean airPlaceEnabled = airPlaceSetting.getValue();
      if (!airPlaceEnabled) {
         return false;
      }
      Item heldItem = (ClientAccess.zioMinecraft.player.getMainHandItem()).getItem();
      if (this.isAirPlaceBlockItem(heldItem)) {
         return true;
      }
      heldItem = (ClientAccess.zioMinecraft.player.getOffhandItem()).getItem();
      return this.isAirPlaceBlockItem(heldItem);
   }

   private boolean shouldKeepVanillaOutline() {
      return (this.unreachableSetting.getValue()) && this.unreachableSetting.isVisible();
   }

   static {

      Block cactus = Blocks.CACTUS;
      Block chorusFlower = Blocks.CHORUS_FLOWER;
      Block snow = Blocks.SNOW;
      Block mossCarpet = Blocks.MOSS_CARPET;
      Block pointedDripstone = Blocks.POINTED_DRIPSTONE;
      Block bamboo = Blocks.BAMBOO;
      EXCLUDED_AIR_PLACE_BLOCKS = Set.of(cactus, chorusFlower, snow, mossCarpet, pointedDripstone, bamboo);

      FILL_BUFFER = MeshBuffer.create(
         8192
      );

      OUTLINE_BUFFER = MeshBuffer.create(
         8192
      );
      reach = (ReachModule)ClientContext.getModule(ReachModule.class);
   }

   private boolean isAirPlaceBlockItem(Item item) {
      if (!(item instanceof BlockItem blockItem)) {
         return false;
      }
      Block block = blockItem.getBlock();
      return !(block instanceof VegetationBlock || block instanceof GrowingPlantBlock
         || block instanceof CarpetBlock || block instanceof CoralFanBlock || block instanceof StandingSignBlock)
         && !EXCLUDED_AIR_PLACE_BLOCKS.contains(block);
   }

   @EventHandler
   private void onBlockOutlineRender(BlockOutlineRenderEvent event) {

      if (!this.shouldKeepVanillaOutline()) {
         event.cancel();
      }
   }

   private void renderHighlightBoxes(
      PoseStack poseStack, HighlightModule.BoxRenderer renderer
   ) {
      if (this.isWorldUnavailable()) {
         return;
      }

      BlockHitResult hitResult = this.getHighlightHitResult();
      if (hitResult == null) {
         return;
      }
      BlockPos blockPos = hitResult.getBlockPos();
      ClientLevel level = ClientAccess.zioMinecraft.level;
      BlockState blockState = level.getBlockState(blockPos);

      if (!this.isAirPlaceHighlight()) {
         if (blockState.getBlock() instanceof AirBlock) {
            return;
         }
      }

      ClientLevel shapeLevel = ClientAccess.zioMinecraft.level;
      VoxelShape shape = blockState.getShape(shapeLevel, blockPos);
      if (shape.isEmpty()) {

         if (this.isAirPlaceHighlight()) {
            VoxelShape fullBlockShape = Shapes.block();
            AABB airPlaceBox = fullBlockShape.bounds();
            renderer.submit(poseStack, airPlaceBox, blockPos);
         }
         return;
      }

      if (this.shouldKeepVanillaOutline()) {
         return;
      }
      Setting<HighlightMode> modeForComplex = this.modeSetting;
      if (modeForComplex.getValue() == HighlightModule.HighlightMode.COMPLEX) {
         List<AABB> shapeBoxes = shape.toAabbs();
         for (AABB box : shapeBoxes) {
            renderer.submit(poseStack, box, blockPos);
         }
      } else {
         Setting<HighlightMode> modeForSide = this.modeSetting;
         if (modeForSide.getValue() == HighlightModule.HighlightMode.SIDE) {
            AABB sideBox = shape.bounds();
            Direction face = hitResult.getDirection();
            Vec3 boxSize = new Vec3(sideBox.getXsize(), sideBox.getYsize(), sideBox.getZsize());
            Vec3 halfExtents = new Vec3(
               face.getStepX() == 0 ? 0.5D : 0.0D,
               face.getStepY() == 0 ? 0.5D : 0.0D,
               face.getStepZ() == 0 ? 0.5D : 0.0D
            );
            Vec3 faceExtents = halfExtents.multiply(boxSize);
            AABB centerBox = new AABB(sideBox.getCenter(), sideBox.getCenter());
            Vec3 faceOffset = ((face.getUnitVec3()).multiply(boxSize)).scale(0.5D);
            AABB movedFaceBox = centerBox.move(faceOffset);
            double extentX = faceExtents.x;
            double extentY = faceExtents.y;
            double extentZ = faceExtents.z;
            sideBox = movedFaceBox.inflate(extentX, extentY, extentZ);
            renderer.submit(poseStack, sideBox, blockPos);
         } else {
            AABB simpleBox = shape.bounds();
            renderer.submit(poseStack, simpleBox, blockPos);
         }
      }
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      Setting<Boolean> depthSetting = this.depthSetting;
      Boolean depthEnabled = depthSetting.getValue();
      if (!depthEnabled) {
         PoseStack highlightPoseStack = event.getPoseStack();
         HighlightModule.BoxRenderer renderer = (poseStack, box, blockPos) -> {
            AABB worldBox = box.move(blockPos);
            Setting<Color> fillColorSetting = this.fillColorSetting;
            WorldBoxRenderer.fillBox(
               poseStack, worldBox, fillColorSetting.getValue()
            );
            worldBox = box.move(blockPos);
            Color outlineColor = this.colorSetting.getValue();
            Float lineWidth = this.lineWidthSetting.getValue();
            WorldBoxRenderer.outlineBox(
               poseStack, worldBox, outlineColor, lineWidth
            );
         };
         this.renderHighlightBoxes(highlightPoseStack, renderer);
      }
   }

   @EventHandler
   private void onBeforeBufferSource(WorldRenderEvent.BeforeBufferSource event) {
      Setting<Boolean> depthSetting = this.depthSetting;
      Boolean depthEnabled = depthSetting.getValue();
      if (depthEnabled) {
         FILL_BUFFER.begin(
            Mode.QUADS, DefaultVertexFormat.POSITION_COLOR
         );
         OUTLINE_BUFFER.begin(
            Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH
         );
         PoseStack highlightPoseStack = event.getPoseStack();
         HighlightModule.BoxRenderer renderer = (poseStack, box, blockPos) -> {

            float inflation = 0.002F;
            Setting<HighlightMode> modeSetting = this.modeSetting;
            if (modeSetting.getValue() == HighlightModule.HighlightMode.SIDE) {

               inflation = -0.0003F;
            } else {
               ClientLevel emptyBlockLevel = ClientAccess.zioMinecraft.level;
               if (emptyBlockLevel.isEmptyBlock(blockPos)) {

                  inflation = -0.005F;
               }
            }

            AABB worldBox = box.move(blockPos);
            double inflationAmount = inflation;
            AABB expandedBox = worldBox.inflate(inflationAmount);
            MeshBuffer fillBuffer = FILL_BUFFER;
            Pose fillPose = poseStack.last();
            Matrix4f poseMatrix = fillPose.pose();
            Setting<Color> fillColorSetting = this.fillColorSetting;
            Color fillColor = fillColorSetting.getValue();
            VertexGeometry.appendFilledBox(
               fillBuffer, poseMatrix, expandedBox, fillColor.hashCode()
            );
            MeshBuffer outlineBuffer = OUTLINE_BUFFER;
            Pose outlinePose = poseStack.last();
            Color outlineColor = this.colorSetting.getValue();
            VertexGeometry.appendBoxOutline(
               outlineBuffer, outlinePose, expandedBox, this.lineWidthSetting.getValue(), outlineColor.hashCode()
            );
         };
         this.renderHighlightBoxes(highlightPoseStack, renderer);
         FILL_BUFFER.draw(
            ClientRenderTypes.worldFillDepth()
         );
         OUTLINE_BUFFER.draw(
            ClientRenderTypes.worldLinesDepth()
         );

      }
   }

   public static void bindModuleDependencies(ModuleRegistry registry) {
      reach = (ReachModule)registry.getModule(ReachModule.class);
   }

   public HighlightModule() {
      super("Highlight", "Highlights the block you're looking at.", ModuleCategory.RENDER, new String[]{"blockhighlight"});
      this.modeSetting = this.add(new EnumSetting<>("Mode", HighlightMode.SIMPLE));
      this.depthSetting = this.add(new BooleanSetting("Depth", false));
      this.unreachableSetting = this.add(new BooleanSetting("Unreachable", false));
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F));
      this.colorSetting = this.add(new ColorSetting("Color", new Color(1992137215, true)));
      this.fillColorSetting = this.add(new ColorSetting("FillColor", new Color(12425727, true)));
      this.setDrawn(false);
      this.unreachableSetting.addVisibilityPredicate(ignoredValue -> {
         ReachModule reachModule = reach;
         if (reachModule.isToggled()) {
            Setting<Boolean> liquidPlaceSetting = reach.liquidPlaceSetting;
            Boolean liquidPlaceEnabled = liquidPlaceSetting.getValue();
            if (liquidPlaceEnabled) {
               return true;
            }
            Setting<Boolean> airPlaceSetting = reach.airPlaceSetting;
            Boolean airPlaceEnabled = airPlaceSetting.getValue();
            if (airPlaceEnabled) {
               return true;
            }
         }
         return false;
      });
   }

   private enum HighlightMode implements NamedEntry {
      SIMPLE("Simple"),
      SIDE("Side"),
      COMPLEX("Complex");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      HighlightMode(String displayName) {
         this.displayName = displayName;
      }

   }

   private interface BoxRenderer {
      void submit(PoseStack poseStack, AABB box, BlockPos blockPos);
   }

}
