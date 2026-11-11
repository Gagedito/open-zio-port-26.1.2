/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import dev.zio.client.ClientAccess;
import dev.zio.client.render.RenderTargetUtils;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.render.PostProcessShaderPass;
import dev.zio.client.render.ResizableHudWidgetRenderer;
import dev.zio.client.modules.HudWidgetModule;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PlayerModelModule extends HudWidgetModule {

   public PlayerModelModule() {
      super("PlayerModel");
      ResizableHudWidgetRenderer widgetController = new ResizableHudWidgetRenderer(this);
      widgetController.enableResizing(this);
      this.setRenderer(widgetController);
   }

   @Override
   public float[] calculateDimensions() {
      float[] dimensions = new float[2];
      dimensions[0] = 50.0F;
      dimensions[1] = 80.0F;
      return dimensions;
   }

   @Override
   public void renderContent(PoseStack poses) {
      Camera camera = ClientAccess.zioMinecraft.gameRenderer.getMainCamera();
      if (camera.position() != null) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         Quaternionf quaternion = new Quaternionf();

         float rotationZ = 3.1415927F;
         Quaternionf rotation = quaternion.rotateZ(rotationZ);
         Vector3f modelTranslation = new Vector3f(
            0.0F, player.getBbHeight() / 2.0F, 0.0F
         );
         float x = 25.0F;
         float y = 40.0F;
         float scaleArgument = 35.0F;
         renderEntityPreview(poses, x, y, scaleArgument, modelTranslation, rotation, player);
      }
   }

   public static void renderEntityPreview(
      PoseStack poses, float x, float y, float scale, Vector3f modelTranslation, Quaternionf rotation, LivingEntity entity
   ) {
      RenderUtils.clearColor(
         PostProcessShaderPass.POST_TARGET, Color.black
      );
      RenderUtils.clearDepth(
         PostProcessShaderPass.POST_TARGET
      );
      RenderTargetUtils.overrideRenderTargets(
         PostProcessShaderPass.POST_TARGET
      );
      poses.pushPose();
      poses.translate(x, y, 50.0D);
      float scaleZ = -scale;
      poses.scale(scale, scale, scaleZ);

      float modelZ = modelTranslation.z;
      float modelY = modelTranslation.y;
      float modelX = modelTranslation.x;
      poses.translate(modelX, modelY, modelZ);
      poses.mulPose(rotation);
      Lighting lighting = ClientAccess.zioMinecraft.gameRenderer.getLighting();
      Entry entityLightingEntry = Entry.ENTITY_IN_UI;
      lighting.setupFor(entityLightingEntry);
      SubmitNodeStorage submitNodes = ClientAccess.zioMinecraft.gameRenderer.getSubmitNodeStorage();
      CameraRenderState cameraState = ClientAccess.zioMinecraft.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
      Minecraft clientForEntityRenderer = Minecraft.getInstance();
      EntityRenderDispatcher entityRenderer = clientForEntityRenderer.getEntityRenderDispatcher();
      float partialTick = RenderUtils.getPartialTick();
      EntityRenderState entityState = entityRenderer.extractEntity(entity, partialTick);
      entityState.shadowPieces.clear();
      entityRenderer.submit(entityState, cameraState, 0.0, 0.0, 0.0, poses, submitNodes);
      RenderUtils.INSTANCE
         .flushEntities();
      poses.popPose();
      GameRenderer rendererForItemLighting = ClientAccess.zioMinecraft.gameRenderer;
      lighting = rendererForItemLighting.getLighting();
      Entry itemLightingEntry = Entry.ITEMS_FLAT;
      lighting.setupFor(itemLightingEntry);
      RenderTargetUtils.restoreRenderTargets();
      RenderUtils.clearDepth(
         PostProcessShaderPass.POST_TARGET
      );
      RenderUtils.blitToMain(
         PostProcessShaderPass.POST_TARGET
      );
   }

}
