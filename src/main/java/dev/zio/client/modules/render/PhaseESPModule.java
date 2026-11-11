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
import dev.zio.client.render.ColorUtils;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PhaseESPModule extends Module {
   private final Setting<Boolean> crawlingSetting;
   private final Setting<Boolean> inBlocksSetting;
   private final Setting<Boolean> insideSetting;
   private final Setting<Color> safeSetting;
   private final Setting<Color> semiSafeSetting;
   private final Setting<Color> unsafeSetting;
   private final Setting<Boolean> outlineSetting;
   private final Setting<Float> alphaSetting;
   private final Map<BlockPos, BlockAnimation> blockAnimations;

   private boolean isNotBedrock(BlockPos position) {

      return BlockInteractionUtils.getBlock(position)
         != Blocks.BEDROCK;
   }

   private boolean shouldHighlightPosition(BlockPos position) {
      Boolean allowInBlocks = this.inBlocksSetting.getValue();
      if (allowInBlocks) {
         Boolean allowInside = this.insideSetting.getValue();
         if (allowInside) {
            return true;
         }
      }

      AABB blockBounds = new AABB(position);
      ClientLevel level = ClientAccess.zioMinecraft.level;
      BlockState shapeState = level.getBlockState(position);
      ClientLevel shapeLevel = ClientAccess.zioMinecraft.level;
      VoxelShape collisionShape = shapeState.getCollisionShape(shapeLevel, position);
      if (!collisionShape.isEmpty()) {
         AABB localBounds = collisionShape.bounds();
         blockBounds = localBounds.move(position);
      }

      LocalPlayer player = ClientAccess.zioMinecraft.player;
      AABB playerBounds = player.getBoundingBox();

      AABB insetBounds = blockBounds.inflate(-0.05D);
      return !playerBounds.intersects(insetBounds);
   }

   private boolean isPhaseCandidate(BlockPos position) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      BlockState state = level.getBlockState(position);
      Block block = state.getBlock();
      float explosionResistance = block.getExplosionResistance();

      if (explosionResistance >= 600.0F) {

         if (BlockInteractionUtils.isCollidable(position)) {
            if (!state.canBeReplaced()) {
               return true;
            }
         }
      }

      return false;
   }

   private PhaseESPModule.Safety classifySafetyForRender(BlockPos position) {
      return this.classifySafety(position);
   }

   private PhaseESPModule.Safety classifySafety(BlockPos position) {

      Block block = WorldUtils.getBlock(position);
      float explosionResistance = block.getExplosionResistance();

      boolean resistantBlock = explosionResistance >= 600.0F;
      BlockPos collisionFloorPosition = position.below();

      if (BlockInteractionUtils.isCollidable(collisionFloorPosition) && resistantBlock) {
         BlockPos resistanceFloorPosition = position.below();

         Block floorBlock = WorldUtils.getBlock(resistanceFloorPosition);
         explosionResistance = floorBlock.getExplosionResistance();

         if (!(explosionResistance < 600.0F)) {

            if (!this.isNotBedrock(position)) {
               BlockPos bedrockFloorPosition = position.below();

               if (!this.isNotBedrock(bedrockFloorPosition)) {
                  return PhaseESPModule.Safety.SAFE;
               }
            }

            return PhaseESPModule.Safety.SEMI;
         }
      }

      return PhaseESPModule.Safety.UNSAFE;
   }

   private void refreshPosition(BlockPos position) {
      Map<BlockPos, BlockAnimation> animations = this.blockAnimations;
      PhaseESPModule.BlockAnimation animation = animations.getOrDefault(
         position, PhaseESPModule.BlockAnimation.create()
      );
      animations = this.blockAnimations;
      animations.put(position, animation.refresh());
   }

   private AABB getBlockBounds(BlockPos position) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      BlockState shapeState = level.getBlockState(position);
      ClientLevel shapeLevel = ClientAccess.zioMinecraft.level;
      VoxelShape shape = shapeState.getShape(shapeLevel, position);
      if (shape.isEmpty()) {
         shape = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
      }

      AABB localBounds = shape.bounds();
      return localBounds.move(position);
   }

   private static boolean isPlayerInCollidableBlock() {
      BlockPos playerPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      return BlockInteractionUtils.isCollidable(playerPosition);
   }

   private void removeExpiredPositions() {
      this.blockAnimations.entrySet().removeIf(
         entry -> {
            long nowMillis = System.currentTimeMillis();
            PhaseESPModule.BlockAnimation animation = entry.getValue();
            return nowMillis > animation.expiresAtMillis();
         }
      );
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      this.removeExpiredPositions();
      double playerMinY = ClientAccess.zioMinecraft.player.getBoundingBox().minY;
      float roundedPlayerY = (float)Math.round(playerMinY);
      AABB playerBounds = ClientAccess.zioMinecraft.player.getBoundingBox();
      AABB minimumYBounds = playerBounds.setMinY(roundedPlayerY);
      AABB flatBounds = minimumYBounds.setMaxY(roundedPlayerY);
      AABB searchBounds = flatBounds.inflate(0.01D, 0.0D, 0.01D);

      boolean crawlingBlocked = PlayerStateUtils.isLocalHeightBelowOne()
         && !(this.crawlingSetting.getValue());
      boolean inBlockBlocked = isPlayerInCollidableBlock()
         && !(this.inBlocksSetting.getValue());
      if (!inBlockBlocked && !crawlingBlocked) {
         BlockPos.betweenClosedStream(searchBounds)
            .map(BlockPos::immutable)
            .filter(this::isPhaseCandidate)
            .filter(this::shouldHighlightPosition)
            .forEach(this::refreshPosition);
      }

      for (Entry<BlockPos, BlockAnimation> entry : this.blockAnimations.entrySet()) {
         BlockPos position = entry.getKey();
         PhaseESPModule.BlockAnimation animation = entry.getValue();

         PhaseESPModule.Safety safety = classifySafetyForRender(position);
         Color fillColor = safety.getFadedColor(this, animation);

         AABB blockBounds = this.getBlockBounds(position);
         PoseStack fillPoses = event.getPoseStack();
         BlockPos fillPosition = position;
         double fillMaxY = fillPosition.getY();
         WorldBoxRenderer.fillBox(
            fillPoses, blockBounds.setMaxY(fillMaxY), fillColor
         );
         Boolean drawOutline = this.outlineSetting.getValue();
         if (drawOutline) {
            float animationAlpha = animation.getAlpha();
            Float configuredAlpha = this.alphaSetting.getValue();
            float outlineAlpha = animationAlpha * configuredAlpha;
            PoseStack outlinePoses = event.getPoseStack();
            BlockPos outlinePosition = position;
            double outlineMaxY = outlinePosition.getY();
            AABB outlineBounds = blockBounds.setMaxY(outlineMaxY);
            Color outlineColor = ColorUtils.withAlpha(fillColor, outlineAlpha);

            WorldBoxRenderer.outlineBox(
               outlinePoses, outlineBounds, outlineColor, 1.0F);
         }
      }
   }

   public PhaseESPModule() {
      super("PhaseESP", "Highlights safe blocks to phase into.", ModuleCategory.RENDER, new String[0]);
      this.crawlingSetting = this.add(new BooleanSetting(new String("Crawling"), false));
      this.inBlocksSetting = this.add(new BooleanSetting(new String("InBlocks"), false));
      this.insideSetting = this.add(new BooleanSetting(new String("Inside"), false).dependsOn(this.inBlocksSetting));
      this.safeSetting = this.add(new ColorSetting(new String("Safe"), new Color(540373280, true)));
      this.semiSafeSetting = this.add(new ColorSetting(new String("SemiSafe"), new Color(637525248, true)));
      this.unsafeSetting = this.add(new ColorSetting(new String("Unsafe"), new Color(634257408, true)));
      this.outlineSetting = this.add(new BooleanSetting(new String("Outline"), false).markVisibilityDependency());
      this.alphaSetting = this.add(new NumberSetting<>(new String("Alpha"), 1.0F, 0.0F, 1.0F).dependsOn(this.outlineSetting));
      this.blockAnimations = new HashMap<>();
   }

   enum Safety {
      SAFE,
      SEMI,
      UNSAFE;

      Color getFadedColor(
         PhaseESPModule module, PhaseESPModule.BlockAnimation animation
      ) {
         Setting<Color> colorSetting = switch (this) {
            case SAFE -> module.safeSetting;
            case SEMI -> module.semiSafeSetting;
            case UNSAFE -> module.unsafeSetting;
         };
         Color baseColor = colorSetting.getValue();
         float baseAlpha = baseColor.getAlpha();
         return ColorUtils.withAlpha(
            baseColor, (int)(baseAlpha * animation.getAlpha())
         );
      }

   }

   private record BlockAnimation(
      long createdAtMillis,
      long expiresAtMillis
   ) {

      public static PhaseESPModule.BlockAnimation create() {
         PhaseESPModule.BlockAnimation animation = new PhaseESPModule.BlockAnimation(System.currentTimeMillis(), 0L);
         return animation.refresh();
      }

      public PhaseESPModule.BlockAnimation refresh() {
         return new PhaseESPModule.BlockAnimation(this.createdAtMillis, System.currentTimeMillis() + 200L);
      }

      public float getAlpha() {
         long ageMillis = System.currentTimeMillis() - this.createdAtMillis;
         float alphaValue = (float)ageMillis;
         if (alphaValue <= 75.0F) {
            alphaValue = (float)ageMillis;
            return alphaValue / 75.0F;
         }

         alphaValue = (float)ageMillis;
         float lifetimeMillis = (float)(this.expiresAtMillis - this.createdAtMillis);
         if (alphaValue >= lifetimeMillis - 75.0F) {
            alphaValue = (float)(this.expiresAtMillis - this.createdAtMillis - ageMillis);
            alphaValue /= 75.0F;
            return Mth.clamp(alphaValue, 0.0F, 1.0F);
         }

         return 1.0F;
      }

   }

}
