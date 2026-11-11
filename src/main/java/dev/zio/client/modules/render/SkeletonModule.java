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
import dev.zio.client.render.RenderTargetUtils;
import dev.zio.client.render.LineMeshRenderer;
import dev.zio.client.render.MaskShaderRenderer;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.player.FreecamModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.awt.Color;
import java.util.Iterator;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

public class SkeletonModule extends Module {
   private static FreecamModule freecamModule;
   private final Setting<Boolean> bloomSetting;
   private final Setting<Boolean> selfSetting;
   private final Setting<Float> lineWidthSetting;
   private final Setting<Color> colorSetting;

   static {

      freecamModule = (FreecamModule)ClientContext.getModule(FreecamModule.class);
   }

   private void applyModelPartRotation(PoseStack poseStack, ModelPart part) {
      if (part.zRot != 0.0F) {
         poseStack.mulPose(Axis.ZP.rotation(part.zRot));
      }

      if (part.yRot != 0.0F) {
         poseStack.mulPose(Axis.YN.rotation(part.yRot));
      }

      if (part.xRot != 0.0F) {
         poseStack.mulPose(Axis.XN.rotation(part.xRot));
      }
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      PoseStack poseStack = event.getPoseStack();
      float partialTick = event.getPartialTick();
      LineMeshRenderer.begin(poseStack, this.lineWidthSetting.getValue());
      ClientLevel level = ClientAccess.zioMinecraft.level;
      Iterator<AbstractClientPlayer> players = level.players().iterator();
      while (players.hasNext()) {
         AbstractClientPlayer player = players.next();
         FreecamModule freecam = freecamModule;
         boolean selfVisible = true;
         if (!freecam.isToggled()) {
            Options options = ClientAccess.zioMinecraft.options;
            selfVisible = options.getCameraType() != CameraType.FIRST_PERSON;
         }
         if (player == ClientAccess.zioMinecraft.player) {
            if (!selfVisible) {
               continue;
            }
            if (!(this.selfSetting.getValue())) {
               continue;
            }
         }
         if (player.isDeadOrDying()) {
            continue;
         }
         Color color = this.colorSetting.getValue();
         Vec3 relativePosition = this.getCameraRelativePosition(player, partialTick);
         EntityRenderDispatcher selectedDispatcher = ClientAccess.zioMinecraft.getEntityRenderDispatcher();
         AvatarRenderer renderer = (AvatarRenderer)selectedDispatcher.<AbstractClientPlayer>getRenderer(player);
         PlayerModel model = (PlayerModel)renderer.getModel();
         float statePartialTick = event.getPartialTick();
         AvatarRenderState renderState = (AvatarRenderState)renderer.createRenderState(player, statePartialTick);
         model.setupAnim(renderState);
         float bodyYaw = renderState.bodyRot;
         float targetSwimPitch;
         if (renderState.isInWater) {
            targetSwimPitch = -9e+01F - renderState.xRot;
         } else {
            targetSwimPitch = -9e+01F;
         }
         float swimAmount = renderState.swimAmount;
         float swimPitch = Mth.lerp(swimAmount, 0.0F, targetSwimPitch);
         boolean visuallySwimming = player.isVisuallySwimming();
         boolean crouching = player.isShiftKeyDown();
         boolean fallFlying = player.isFallFlying();
         ModelPart head = model.head;
         ModelPart leftArm = model.leftArm;
         ModelPart rightArm = model.rightArm;
         ModelPart leftLeg = model.leftLeg;
         ModelPart rightLeg = model.rightLeg;
         poseStack.pushPose();
         poseStack.translate(relativePosition.x, relativePosition.y, relativePosition.z);
         Quaternionf bodyRotation = new Quaternionf();
         double renderScalarScratch = bodyYaw + 1.8e+02F;
         renderScalarScratch *= 3.141592653589793D;
         renderScalarScratch /= 180.0D;
         Quaternionfc bodyYawRotation = bodyRotation.setAngleAxis(renderScalarScratch, 0.0, -1.0D, 0.0);
         poseStack.mulPose(bodyYawRotation);
         if (fallFlying) {
            float fallFlyingScale = renderState.fallFlyingScale();
            if (!renderState.isAutoSpinAttack) {
               Axis flyingPitchAxis = Axis.XP;
               float flyingPitchAngle = fallFlyingScale * (-9e+01F - renderState.xRot);
               Quaternionfc flyingPitchRotation = flyingPitchAxis.rotationDegrees(flyingPitchAngle);
               poseStack.mulPose(flyingPitchRotation);
            }
            if (renderState.shouldApplyFlyingYRot) {
               Axis flyingYawRotationAxis = Axis.YP;
               float flyingYawAngle = renderState.flyingYRot;
               Quaternionfc flyingYawRotation = flyingYawRotationAxis.rotation(flyingYawAngle);
               poseStack.mulPose(flyingYawRotation);
            }
         } else if (renderState.swimAmount > 0.0F) {
            Axis swimPitchRotationAxis = Axis.XP;
            Quaternionfc swimPitchRotation = swimPitchRotationAxis.rotationDegrees(swimPitch);
            poseStack.mulPose(swimPitchRotation);
            if (visuallySwimming) {
               poseStack.translate(0.0F, -1.0F, 0.3F);
            }
         }
         double torsoStartY = crouching ? 0.6D : 0.699999988079071D;
         if (crouching) {
            renderScalarScratch = 0.23000000417232513D;
         } else {
            renderScalarScratch = 0.0;
         }
         LineMeshRenderer.addVertex(0.0, torsoStartY, renderScalarScratch, color);
         double torsoEndY = crouching ? 1.05D : 1.399999976158142D;
         LineMeshRenderer.addVertex(0.0, torsoEndY, 0.0, color);
         double shoulderStartY = crouching ? 1.0499999523162842D : 1.350000023841858D;
         LineMeshRenderer.addVertex(-0.3700000047683716D, shoulderStartY, 0.0, color);
         double shoulderEndY = crouching ? 1.0499999523162842D : 1.350000023841858D;
         LineMeshRenderer.addVertex(0.3700000047683716D, shoulderEndY, 0.0, color);
         double hipStartY = crouching ? 0.6000000238418579D : 0.699999988079071D;
         if (crouching) {
            renderScalarScratch = 0.23000000417232513D;
         } else {
            renderScalarScratch = 0.0;
         }
         LineMeshRenderer.addVertex(-0.15000000596046448D, hipStartY, renderScalarScratch, color);
         double hipEndY = crouching ? 0.6000000238418579D : 0.699999988079071D;
         if (crouching) {
            renderScalarScratch = 0.23000000417232513D;
         } else {
            renderScalarScratch = 0.0;
         }
         LineMeshRenderer.addVertex(0.15000000596046448D, hipEndY, renderScalarScratch, color);
         poseStack.pushPose();
         float headOffsetY;
         if (crouching) {
            headOffsetY = 1.05F;
         } else {
            headOffsetY = 1.4F;
         }
         poseStack.translate(0.0F, headOffsetY, 0.0F);
         this.applyModelPartRotation(poseStack, head);
         LineMeshRenderer.addVertex(0.0, 0.0, 0.0, color);
         LineMeshRenderer.addVertex(
            0.0, 0.15000000596046448D, 0.0, color
         );
         poseStack.popPose();
         poseStack.pushPose();
         float rightLegOffsetY;
         if (crouching) {
            rightLegOffsetY = 0.6F;
         } else {
            rightLegOffsetY = 0.7F;
         }
         float rightLegOffsetZ;
         if (crouching) {
            rightLegOffsetZ = 0.23F;
         } else {
            rightLegOffsetZ = 0.0F;
         }
         poseStack.translate(0.15F, rightLegOffsetY, rightLegOffsetZ);
         this.applyModelPartRotation(poseStack, rightLeg);
         LineMeshRenderer.addVertex(0.0, 0.0, 0.0, color);
         LineMeshRenderer.addVertex(
            0.0, -0.6000000238418579D, 0.0, color
         );
         poseStack.popPose();
         poseStack.pushPose();
         float leftLegOffsetY;
         if (crouching) {
            leftLegOffsetY = 0.6F;
         } else {
            leftLegOffsetY = 0.7F;
         }
         float leftLegOffsetZ;
         if (crouching) {
            leftLegOffsetZ = 0.23F;
         } else {
            leftLegOffsetZ = 0.0F;
         }
         poseStack.translate(-0.15F, leftLegOffsetY, leftLegOffsetZ);
         this.applyModelPartRotation(poseStack, leftLeg);
         LineMeshRenderer.addVertex(0.0, 0.0, 0.0, color);
         LineMeshRenderer.addVertex(
            0.0, -0.6000000238418579D, 0.0, color
         );
         poseStack.popPose();
         poseStack.pushPose();
         float rightArmOffsetY;
         if (crouching) {
            rightArmOffsetY = 1.05F;
         } else {
            rightArmOffsetY = 1.35F;
         }
         poseStack.translate(0.37F, rightArmOffsetY, 0.0F);
         this.applyModelPartRotation(poseStack, rightArm);
         LineMeshRenderer.addVertex(0.0, 0.0, 0.0, color);
         LineMeshRenderer.addVertex(
            0.0, -0.550000011920929D, 0.0, color
         );
         poseStack.popPose();
         poseStack.pushPose();
         float leftArmOffsetY;
         if (crouching) {
            leftArmOffsetY = 1.05F;
         } else {
            leftArmOffsetY = 1.35F;
         }
         poseStack.translate(-0.37F, leftArmOffsetY, 0.0F);
         this.applyModelPartRotation(poseStack, leftArm);
         LineMeshRenderer.addVertex(0.0, 0.0, 0.0, color);
         LineMeshRenderer.addVertex(
            0.0, -0.550000011920929D, 0.0, color
         );
         poseStack.popPose();
         poseStack.popPose();
      }
      RenderTarget mainTarget = ClientAccess.zioMinecraft.getMainRenderTarget();
      RenderTarget maskTarget = MaskShaderRenderer.MASK_TARGET;
      RenderUtils.clearColor(maskTarget, this.colorSetting.getValue());
      RenderUtils.clearDepth(
         MaskShaderRenderer.MASK_TARGET
      );
      RenderTargetUtils.setMainRenderTarget(
         MaskShaderRenderer.MASK_TARGET
      );
      LineMeshRenderer.draw();
      RenderTargetUtils.setMainRenderTarget(mainTarget);
      RenderUtils.blitToMain(
         MaskShaderRenderer.MASK_TARGET
      );
      if (this.bloomSetting.getValue()) {
         Float bloomLineWidth = this.lineWidthSetting.getValue();
         float bloomWidth = 6.0F + bloomLineWidth;
         MaskShaderRenderer.setParameters(
            bloomWidth, 1.5F, 1.0F,2.0F);
         MaskShaderRenderer.renderEffect("bloom_mask");
      }
   }

   private Vec3 getCameraRelativePosition(Entity entity, float partialTick) {
      Vec3 entityPosition = entity.getPosition(partialTick);
      GameRenderer gameRenderer = ClientAccess.zioMinecraft.gameRenderer;
      Vec3 cameraPosition = (gameRenderer.getMainCamera()).position();
      return entityPosition.subtract(cameraPosition);
   }

   public static void bindModuleDependencies(ModuleRegistry registry) {
      freecamModule = (FreecamModule)registry.getModule(
         FreecamModule.class
      );
   }

   public SkeletonModule() {
      super("Skeleton", "Draws skeletons for players.", ModuleCategory.RENDER, new String[0]);
      this.bloomSetting = this.add(new BooleanSetting("Bloom", false));
      this.selfSetting = this.add(new BooleanSetting("Self", true));
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F));
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-4351489, true)));

      this.setDrawn(false);
   }

}
