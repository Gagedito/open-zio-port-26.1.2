/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class ColorSubmitNodeStorage extends SubmitNodeStorage {
   private int colorArgb;
   private int[] itemTintColors;

   @Override
   public SubmitNodeCollection order(int order) {
      return this.getSubmitsPerOrder().computeIfAbsent(order, ignoredOrder -> new ColorSubmitNodeStorage.ColoredSubmitNodeCollection(this));
   }

   public void setColorArgb(int argb) {
      this.colorArgb = argb;
   }

   private class ColoredSubmitNodeCollection extends SubmitNodeCollection {
      public ColoredSubmitNodeCollection(SubmitNodeStorage storage) {
         super(storage);
      }

      @Override
      public void submitShadow(PoseStack poseStack, float shadowRadius, List shadowPieces) {
      }

      @Override
      public void submitNameTag(PoseStack poseStack, Vec3 nameTagPosition, int y, Component nameTag, boolean notSneaking, int packedLight, double distanceToCameraSq, CameraRenderState cameraState) {
      }

      @Override
      public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean dropShadow, DisplayMode displayMode, int packedLight, int color, int backgroundColor, int outlineColor) {
      }

      @Override
      public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
      }

      @Override
      public void submitLeash(PoseStack poseStack, LeashState leashState) {
      }

      @Override
      public void submitModel(
         Model model, Object state, PoseStack poseStack, RenderType renderType, int packedLight, int packedOverlay, int tintColor, TextureAtlasSprite sprite, int outlineColor, CrumblingOverlay crumblingOverlay
      ) {
         super.submitModel(
            model,
            state,
            poseStack,
            renderType,
            packedLight,
            packedOverlay,
            ColorSubmitNodeStorage.this.colorArgb,
            sprite,
            0,
            crumblingOverlay
         );
      }

      @Override
      public void submitModelPart(
         ModelPart part,
         PoseStack poseStack,
         RenderType renderType,
         int packedLight,
         int packedOverlay,
         TextureAtlasSprite sprite,
         boolean sheeted,
         boolean hasFoil,
         int tintColor,
         CrumblingOverlay crumblingOverlay,
         int outlineColor
      ) {
         super.submitModelPart(
            part,
            poseStack,
            renderType,
            packedLight,
            packedOverlay,
            sprite,
            sheeted,
            hasFoil,
            ColorSubmitNodeStorage.this.colorArgb,
            crumblingOverlay,
            outlineColor
         );
      }

      @Override
      public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockState) {
      }

      @Override
      public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> modelParts, int[] tintColors, int packedLight, int packedOverlay, int outlineColor) {
         int colorArgb = ColorSubmitNodeStorage.this.colorArgb;
         int[] coloredTints = new int[]{colorArgb, colorArgb, colorArgb, colorArgb};
         super.submitBlockModel(poseStack, renderType, modelParts, coloredTints, packedLight, packedOverlay, outlineColor);
      }

      @Override
      public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int packedLight, int packedOverlay, int outlineColor, int[] tintColors, List<BakedQuad> quads, FoilType foilType) {
         if (ColorSubmitNodeStorage.this.itemTintColors == null
            || ColorSubmitNodeStorage.this.itemTintColors[0]
               != ColorSubmitNodeStorage.this.colorArgb) {
            ColorSubmitNodeStorage.this.itemTintColors = new int[]{
               ColorSubmitNodeStorage.this.colorArgb, ColorSubmitNodeStorage.this.colorArgb,
               ColorSubmitNodeStorage.this.colorArgb, ColorSubmitNodeStorage.this.colorArgb
            };
         }

         super.submitItem(
            poseStack,
            displayContext,
            packedLight,
            packedOverlay,
            outlineColor,
            ColorSubmitNodeStorage.this.itemTintColors,
            quads,
            foilType
         );
      }

      @Override
      public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
      }

      @Override
      public void submitParticleGroup(ParticleGroupRenderer renderer) {
      }

   }
}
