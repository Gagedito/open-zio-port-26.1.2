/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.gui.screens.ClientScreen;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.render.GuiDeferredDraws;
import dev.zio.client.utils.math.Vec3Utils;
import dev.zio.client.managers.ShaderRegistry;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.events.HudRenderEvent;
import dev.zio.client.render.GuiProjectionUtils;
import dev.zio.client.utils.LockUtils;
import dev.zio.client.modules.render.FreeLookModule;
import dev.zio.client.modules.player.FreecamModule;
import dev.zio.client.modules.client.HUDModule;
import dev.zio.client.modules.player.NameProtectModule;
import dev.zio.client.modules.render.NoBobModule;
import dev.zio.client.modules.render.NoRenderModule;
import dev.zio.client.modules.render.ShaderModule;
import dev.zio.client.modules.render.ViewModelModule;
import dev.zio.client.modules.render.ZoomModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
   private static ZoomModule zoom = ClientContext.getModule(ZoomModule.class);
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static ShaderModule shader = ClientContext.getModule(ShaderModule.class);
   private static FreecamModule freecam = ClientContext.getModule(FreecamModule.class);
   private static FreeLookModule freelook = ClientContext.getModule(FreeLookModule.class);
   private static ViewModelModule viewmodel = ClientContext.getModule(ViewModelModule.class);
   private static NoBobModule noBob = ClientContext.getModule(NoBobModule.class);
   private static HUDModule hud = ClientContext.getModule(HUDModule.class);
   @Unique
   private GuiGraphicsExtractor hudGraphics;
   @Final
   @Shadow
   private Minecraft minecraft;
   @Shadow
   @Final
   private GuiRenderer guiRenderer;
   @Shadow
   @Final
   private FogRenderer fogRenderer;
   private boolean pickingFromFreecam = false;

   /**
    * MC 26.1.2: GameRenderer.pick(F) ya no existe; el picking se movio a
    * Minecraft.pick(F) y el hook (applyCameraPicking, con pickingFromFreecam)
    * se migro a MinecraftMixin.
    */

   @ModifyExpressionValue(method = "bobView", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
   private float invertViewBobSine(float originalSine) {
      return RenderUtils.isInverseViewBobbing() ? -originalSine : originalSine;
   }

   @ModifyExpressionValue(method = "bobView", at = @At(value = "INVOKE", target = "Ljava/lang/Math;abs(F)F"))
   private float invertViewBobMagnitude(float originalMagnitude) {
      return RenderUtils.isInverseViewBobbing() ? -originalMagnitude : originalMagnitude;
   }

   @Inject(method = "<init>", at = @At("RETURN"))
   public void initializeShaderRegistry(
      Minecraft client, ItemInHandRenderer handRenderer, RenderBuffers renderBuffers, ModelManager modelManager, CallbackInfo callback
   ) {
      ClientContext.shaderRegistry = new ShaderRegistry();
   }

   @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
   private void suppressHurtCamera(
      CameraRenderState ignoredCameraState, PoseStack ignoredPoses, CallbackInfo callback
   ) {
      if (noRenderModule.isToggled() && noRenderModule.hurtCamSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
   private void suppressTotemActivation(ItemStack stack, CallbackInfo callback) {
      if (stack.getItem() == Items.TOTEM_OF_UNDYING && noRenderModule.isToggled() && noRenderModule.totemOverlaySetting.getValue()) {
         callback.cancel();
      }
   }

   @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"), require = 0)
   private float suppressBlindnessInterpolation(
      float fraction, float start, float end, Operation<Float> original
   ) {
      return noRenderModule.isToggled() && noRenderModule.blindnessSetting.getValue() ? 0.0F : Mth.lerp(fraction, start, end);
   }

   // MC 26.1.2: renderLevel ya no tiene la ventana "matrices" con los mismos
   // locals. La matriz de proyeccion con view rotation aplicada es ahora el
   // argumento de ProjectionMatrixBuffer.getBuffer(Matrix4f) (unico overload
   // de ese tipo en renderLevel); capturarla ahi equivale al hook viejo que
   // tomaba el local 0 en el popPush "level".
   @WrapOperation(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
      )
   )
   private GpuBufferSlice captureWorldProjectionMatrix(
      ProjectionMatrixBuffer buffer, Matrix4f projectionMatrix, Operation<GpuBufferSlice> original
   ) {
      RenderUtils.WORLD_PROJECTION_MATRIX.set(projectionMatrix);
      return original.call(buffer, projectionMatrix);
   }

   @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V"))
   private boolean shouldRenderHands(
      GameRenderer renderer, CameraRenderState ignoredCameraState, float ignoredPartialTick, Matrix4fc ignoredProjectionMatrix
   ) {
      return !shader.isToggled() || !shader.handsSetting.getValue();
   }

   @WrapWithCondition(method = "bobView", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
   private boolean applyNoBobTranslation(
      PoseStack poseStack, float x, float y, float z
   ) {
      if (!noBob.isToggled()) {
         return true;
      }

      float multiplier = noBob.multiplierSetting.getValue();
      if (multiplier == 0.0F) {
         return true;
      }

      poseStack.translate(x * multiplier, y * multiplier, z * multiplier);
      return false;
   }

   /**
    * MC 26.1.2: GameRenderer.getFov(Camera, F, Z) ya no existe. El FOV fue
    * dividido en Camera.fov (mundo) y Camera.hudFov (manos/HUD 3D, calculado
    * por Camera.calculateHudFov). El antiguo call-site useFovSetting=false
    * (proyeccion de manos) es ahora Camera.hudFov, asi que el hook se migro
    * a CameraMixin sobre calculateHudFov.
    */

   @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
   private void prepareHandRendering(
      CameraRenderState ignoredCameraState, float ignoredPartialTick, Matrix4fc projectionMatrixBase, CallbackInfo callback, @Share("backupMatrix") LocalRef backupMatrix
   ) {
      Matrix4f projectionMatrix = (Matrix4f)(Object)projectionMatrixBase;
      backupMatrix.set(new Matrix4f(projectionMatrix));
      RenderUtils.setRenderColor(
         1.0F,
         1.0F,
         1.0F,
         noRenderModule.getHandOpacity()
      );
      if (viewmodel.isToggled() && viewmodel.lighterSetting.getValue()) {
         float handYaw = -95.0F;
         float handPitch = 45.0F;
         Quaternionf handRotation = new Quaternionf().rotationYXZ((float)Math.toRadians(180.0F - handYaw), (float)Math.toRadians(-handPitch), 0.0F).conjugate();
         projectionMatrix.set(new Matrix4f().rotation(handRotation));
      }

      if (freecam.shouldHideHands()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderItemInHand", at = @At("RETURN"))
   private void restoreHandRendering(
      CameraRenderState ignoredCameraState, float ignoredPartialTick, Matrix4fc projectionMatrixBase, CallbackInfo callback, @Share("backupMatrix") LocalRef backupMatrix
   ) {
      Matrix4f projectionMatrix = (Matrix4f)(Object)projectionMatrixBase;
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
      if (viewmodel.isToggled() && viewmodel.lighterSetting.getValue()) {
         projectionMatrix.set((Matrix4fc)backupMatrix.get());
      }
   }

   @ModifyExpressionValue(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I"))
   private int applyViewModelHandLight(int originalLight) {
      return viewmodel.isToggled() && viewmodel.lighterSetting.getValue()
         ? 15728864
         : originalLight;
   }

   @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;renderScreenEffect(ZZFLnet/minecraft/client/renderer/SubmitNodeCollector;Z)V"))
   private boolean shouldRenderScreenEffects(
      ScreenEffectRenderer renderer, boolean ignoredExtraFlag, boolean ignoredViewFlag, float ignoredPartialTick, SubmitNodeCollector collector, boolean ignoredDepthFlag
   ) {
      return !ClientContext.shaderRegistry.renderingIntoTarget;
   }

   /**
    * MC 26.1.2: hook de pick migrado a MinecraftMixin (GameRenderer.pick se
    * convirtio en Minecraft.pick). Bloque historico conservado:
    *
    * @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    * private void applyCameraPicking(float partialTick, CallbackInfo callback) { ... }
    */

   @Inject(
      method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
      at = @At("HEAD")
   )
   private void beginClientGuiExtraction(
      DeltaTracker deltaTracker, boolean ignoredWindowFlag, boolean ignoredSecondWindowFlag, CallbackInfo callback
   ) {
      GuiDeferredDraws.deferEnabled = true;
   }

   @Inject(
      method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
      at = @At("TAIL")
   )
   private void endClientGuiExtraction(
      DeltaTracker deltaTracker, boolean ignoredWindowFlag, boolean ignoredSecondWindowFlag, CallbackInfo callback
   ) {
      GuiDeferredDraws.deferEnabled = false;
   }

   @Inject(
      method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", shift = Shift.BEFORE)
   )
   private void beforeScreenRendering(
      DeltaTracker deltaTracker, boolean ignoredWindowFlag, boolean ignoredSecondWindowFlag, CallbackInfo callback, @Local GuiGraphicsExtractor graphics
   ) {
      NameProtectModule.replacementBypassed = true;
      boolean manageProjection;
      if (this.minecraft.screen instanceof ClientScreen clientScreen) {
         if (clientScreen.hasMenuParent()) {
            clientScreen.getParentScreen()
               .extractRenderStateWithTooltipAndSubtitles(
                  graphics,
                  -99,
                  -99,
                  RenderUtils.getPartialTick()
               );
         }
         manageProjection = false;
      } else if (this.minecraft.screen != null) {
         manageProjection = true;
      } else {
         return;
      }
      GuiProjectionUtils.beginGuiProjection();
      this.renderClientHud(graphics, manageProjection);
      if (manageProjection) {
         GuiProjectionUtils.restoreProjection();
      }
   }

   @Inject(
      method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", shift = Shift.AFTER)
   )
   private void afterScreenRendering(
      DeltaTracker deltaTracker, boolean ignoredWindowFlag, boolean ignoredSecondWindowFlag, CallbackInfo callback
   ) {
      NameProtectModule.replacementBypassed = false;
      if (this.minecraft.screen instanceof ClientScreen) {
         GuiProjectionUtils.restoreProjection();
      }
   }

   @ModifyExpressionValue(
      method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0)
   )
   private Overlay captureHudGraphics(
      Overlay overlay, @Local GuiGraphicsExtractor graphics
   ) {
      this.hudGraphics = graphics;
      PoseStack poses = GuiProjectionUtils.toPoseStack(graphics);
      if (ClientAccess.zioMinecraft.level != null
         && !ClientAccess.zioMinecraft.options.hideGui
         && !ClientAccess.zioMinecraft.debugEntries.isOverlayVisible()) {
         GuiProjectionUtils.beginGuiProjection();
         HudRenderEvent.Before event = HudRenderEvent.Before.create(
            poses, graphics, RenderUtils.getPartialTick()
         );
         ClientAccess.eventBus.post(event);
         GuiDrawUtils.flush();
         GuiProjectionUtils.restoreProjection();
      }
      return overlay;
   }

   // MC 26.1.2: el ancla incrementFrameNumber en GameRenderer.render ya no
   // existe; renderHudWithoutScreen se re-ancla al TAIL de extractGui donde el
   // estado de gui completo (hud + overlay + screen) ya fue extraido.
   @Inject(method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At("TAIL"))
   private void renderHudWithoutScreen(
      DeltaTracker deltaTracker, boolean ignoredWindowFlag, boolean ignoredSecondWindowFlag, CallbackInfo callback
   ) {
      if (this.minecraft.screen == null) {
         // 26.1.2: este TAIL corre DESPUES de endClientGuiExtraction, que ya
         // cerro deferEnabled; sin ventana abierta todo el flush del HUD
         // (texto/quads/tasks de HudRenderEvent.Unconditional, ChatInput,
         // Blur, flushEntities...) dibujaba inmediato en la fase extract,
         // que GameRenderer.render borra con el clear inicial del main
         // target (verificado por javap del oraculo: CO26/GR26 993-1004).
         // Forzar la ventana igual que la ruta con pantalla
         // (beforeScreenRendering corre a mitad de extract con defer on y
         // por eso el HUD si se ve en pausa/inventario/clickgui). El dren
         // en el ancla GuiRenderer.endFrame ejecuta la cola mas tarde,
         // despues del pase gui, y sobrevive al clear.
         GuiDeferredDraws.deferEnabled = true;
         try {
            this.renderClientHud(
               this.hudGraphics, true
            );
         } finally {
            GuiDeferredDraws.deferEnabled = false;
         }
      }
   }

   /**
    * MC 26.1.2: extractGui solo construye estado; los submits diferidos de
    * zio (GuiDeferredDraws, alimentados por MeshDrawContext durante la fase
    * extract) se someten aqui, dentro de GameRenderer.render justo despues
    * del pase vanilla GuiRenderer.render y antes de endFrame. Equivalente
    * del hook 1.21.11 anclado a GuiRenderer.render TAIL: clearDepth del
    * main target + proyeccion gui propia (ortho 1000..11000 + translate
    * -11000) y submit de quads/lineas/glyphs/z-buffers acumulados.
    */
   @Inject(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V", shift = Shift.BEFORE)
   )
   private void drawDeferredClientGraphics(
      DeltaTracker deltaTracker, boolean ignoredRenderLevel, CallbackInfo callback
   ) {
      boolean anythingPending = GuiDeferredDraws.hasPending();
      boolean shouldManageProjection = this.minecraft.screen instanceof ClientScreen;
      if (!anythingPending && !shouldManageProjection) {
         return;
      }
      RenderUtils.clearDepth(this.minecraft.getMainRenderTarget());
      GuiProjectionUtils.beginGuiProjection();
      if (anythingPending) {
         GuiDeferredDraws.drawAll();
      }
      GuiProjectionUtils.restoreProjection();
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void updateFrameTimingAndChatHeight(
      DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback
   ) {
      hud.updateChatInputHeight(
         ClientAccess.zioMinecraft.screen instanceof ChatScreen
            ? 12.0F
            : 0.0F
      );
      ClientContext.frameTiming.recordFrame();
   }

   @Unique
   public void renderClientHud(GuiGraphicsExtractor graphics, boolean manageProjection) {
      if (ClientAccess.zioMinecraft.level == null) {
         return;
      }
      boolean nameReplacementWasBypassed = NameProtectModule.replacementBypassed;
      if (nameReplacementWasBypassed) {
         NameProtectModule.replacementBypassed = false;
      }

      if (manageProjection) {
         GuiProjectionUtils.beginGuiProjection();
      }

      PoseStack poses = GuiProjectionUtils.toPoseStack(graphics);
      if (!ClientAccess.zioMinecraft.options.hideGui
         && !ClientAccess.zioMinecraft.debugEntries.isOverlayVisible()) {
         HudRenderEvent hudEvent = HudRenderEvent.create(
            poses, graphics, RenderUtils.getPartialTick()
         );
         ClientAccess.eventBus
            .post(hudEvent);
      }

      HudRenderEvent.Unconditional unconditionalEvent = HudRenderEvent.Unconditional.create(
         poses, graphics, RenderUtils.getPartialTick()
      );
      ClientAccess.eventBus
         .post(unconditionalEvent);
      GuiDrawUtils.flush();
      if (manageProjection) {
         GuiProjectionUtils.restoreProjection();
      }

      if (nameReplacementWasBypassed) {
         NameProtectModule.replacementBypassed = true;
      }
   }
}
