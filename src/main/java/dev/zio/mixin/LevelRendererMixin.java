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
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.zio.client.modules.render.AmbienceModule;
import dev.zio.client.modules.render.ChamsModule;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.render.LevelRendererFramebufferAccess;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.RenderTargetRegistry;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.render.IrisShaderBridge;
import dev.zio.client.render.EntityRenderStateAccess;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.modules.player.FreecamModule;
import dev.zio.client.events.BlockOutlineRenderEvent;
import dev.zio.client.modules.render.NoRenderModule;
import dev.zio.client.modules.render.SkyboxModule;
import dev.zio.client.modules.render.ViewClipModule;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin implements ClientAccess, LevelRendererFramebufferAccess {
   private static final ViewClipModule viewClip = ClientContext.getModule(ViewClipModule.class);
   private static final AmbienceModule ambienceModule = ClientContext.getModule(AmbienceModule.class);
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final FreecamModule freecam = ClientContext.getModule(FreecamModule.class);
   private static final SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);
   private static final ChamsModule chamsModule = ClientContext.getModule(ChamsModule.class);
   @Unique
   private PoseStack worldPoseStack;
   @Unique
   private AtomicBoolean savedHideGui;
   @Shadow
   @Nullable
   private ClientLevel level;
   @Shadow
   @Nullable
   private RenderTarget entityOutlineTarget;
   @Shadow
   @Final
   private LevelTargetBundle targets;
   @Unique
   private Stack<RenderTarget> entityOutlineTargetStack;
   @Unique
   private Stack<ResourceHandle<RenderTarget>> entityOutlineHandleStack;

   // The weather render pass executor is the lambda body registered on addWeatherPass.
   @WrapWithCondition(
      method = "lambda$addWeatherPass$0(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;I)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V"
      )
   )
   private boolean shouldRenderWeather(
      WeatherEffectRenderer weatherRenderer, Vec3 cameraPosition, WeatherRenderState weatherState
   ) {
      return !ambienceModule.isToggled()
         || !ambienceModule.worldWeatherSetting.getValue()
         || ambienceModule.weatherSetting.getValue()
            != AmbienceModule.WeatherChoice.CLEAR;
   }

   @Inject(method = "renderLevel", at = @At("RETURN"))
   private void renderWorldEvents(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      CameraRenderState cameraState,
      Matrix4fc inputMatrix,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      ChunkSectionsToRender chunkSections,
      CallbackInfo callback
   ) {
      PoseStack poses = new PoseStack();
      float partialTick = RenderUtils.getPartialTick();
      GpuBufferSlice previousFog = RenderSystem.getShaderFog();
      RenderSystem.setShaderFog(
         ClientAccess.zioMinecraft.gameRenderer
            .fogRenderer
            .getBuffer(FogMode.NONE)
      );
      poses.pushPose();
      RenderUtils.applyCameraRotation(poses);
      WorldRenderEvent.BeforeDepthClear beforeDepthEvent = WorldRenderEvent.BeforeDepthClear.create(
         poses, partialTick
      );
      ClientAccess.eventBus.post(
         beforeDepthEvent
      );
      RenderUtils.clearDepth(
         ClientAccess.zioMinecraft.getMainRenderTarget()
      );
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
      WorldRenderEvent.Main mainEvent = WorldRenderEvent.Main.create(
         poses, partialTick
      );
      ClientAccess.eventBus.post(
         mainEvent
      );
      RenderUtils.clearDepth(
         ClientAccess.zioMinecraft.getMainRenderTarget()
      );
      WorldRenderEvent.Overlay overlayEvent = WorldRenderEvent.Overlay.create(
         poses, partialTick
      );
      ClientAccess.eventBus.post(
         overlayEvent
      );
      poses.popPose();
      RenderSystem.setShaderFog(previousFog);
   }

   // The world render pass executor is the lambda body registered on addMainPass.
   @Inject(
      method = "lambda$addMainPass$0(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;ZLorg/joml/Matrix4fc;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBuffers;bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;", shift = Shift.BEFORE)
   )
   private void postBeforeBufferSourceEvent(
      GpuBufferSlice inputBuffer,
      LevelRenderState renderState,
      ProfilerFiller profiler,
      ChunkSectionsToRender chunkSections,
      ResourceHandle firstTarget,
      ResourceHandle secondTarget,
      ResourceHandle thirdTarget,
      ResourceHandle fourthTarget,
      ResourceHandle fifthTarget,
      boolean renderFlag,
      Matrix4fc inputMatrix,
      CallbackInfo callback,
      @Local PoseStack poses
   ) {
      WorldRenderEvent.BeforeBufferSource event = WorldRenderEvent.BeforeBufferSource.create(
         poses, RenderUtils.getPartialTick()
      );
      ClientAccess.eventBus.post(
         event
      );
   }

   @Inject(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/culling/Frustum;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZLnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
         shift = Shift.BEFORE
      )
   )
   private void addSkyShaderPass(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      CameraRenderState cameraState,
      Matrix4fc inputMatrix,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      ChunkSectionsToRender chunkSections,
      CallbackInfo callback,
      @Local FrameGraphBuilder frameGraph
   ) {
      if (skyboxModule.isToggled()
         && skyboxModule.shaderSetting.getValue()) {
         FramePass skyFramePass = frameGraph.addPass("sky_zio");
         this.targets.main = skyFramePass.readsAndWrites(this.targets.main);
         skyFramePass.executes(
            () -> {
               ClientContext.shaderRegistry.render("sky", skyboxModule::drawShaderQuad);
               RenderUtils.INSTANCE
                  .fillRect(
                     this.worldPoseStack, 0.0F, 0.0F, 1.0F, 1.0F, 0
                  );
            }
         );
      }
   }

   // The world render pass executor is the lambda body registered on addMainPass.
   @WrapWithCondition(
      method = "lambda$addMainPass$0(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;ZLorg/joml/Matrix4fc;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;renderBlockOutline(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;ZLnet/minecraft/client/renderer/state/level/LevelRenderState;)V"
      )
   )
   private boolean shouldRenderBlockOutline(
      LevelRenderer renderer, BufferSource bufferSource, PoseStack poseStack, boolean translucent, LevelRenderState renderState
   ) {
      if (!(ClientAccess.zioMinecraft.hitResult instanceof BlockHitResult)) {
         return true;
      }

      BlockPos blockPos = ((BlockHitResult)ClientAccess.zioMinecraft.hitResult)
         .getBlockPos();
      BlockOutlineRenderEvent event = new BlockOutlineRenderEvent(
         poseStack, null, blockPos, ClientAccess.zioMinecraft.level.getBlockState(blockPos)
      );
      ClientAccess.eventBus.post(
         event
      );
      return !event.isCancelled();
   }


   // 26.1.2: la instruccion LocalPlayer.isSpectator()Z ya no ocurre en renderLevel;
   // el cuerpo migro a update(Camera) que decide el culling de terreno.
   @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"))
   private boolean applyCameraSpectatorOverride(boolean originalSpectator) {
      return freecam.isToggled()
         || (viewClip.isToggled() && ClientAccess.zioMinecraft.gameRenderer.getMainCamera().isDetached())
         || originalSpectator;
   }

   @Inject(method = "renderLevel", at = @At("HEAD"))
   private void captureWorldRenderState(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      CameraRenderState cameraState,
      Matrix4fc inputMatrix,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      ChunkSectionsToRender chunkSections,
      CallbackInfo callback
   ) {
      RenderUtils.setPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
      this.worldPoseStack = new PoseStack();
   }

   // The clear background render pass executor is the lambda body registered in renderLevel.
   @ModifyExpressionValue(method = "lambda$renderLevel$0(Lorg/joml/Vector4f;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;colorFromFloat(FFFF)I"))
   private int applySkyboxBackgroundColor(int originalColor) {
      return skyboxModule.isToggled()
            && skyboxModule.isCurrentDimensionEnabled()
         ? ARGB.color(
            0,
            (skyboxModule.backgroundSetting.getValue())
               .hashCode()
         )
         : originalColor;
   }

   // The sky render pass executor is the lambda body registered on addSkyPass.
   @ModifyArgs(
      method = "lambda$addSkyPass$0(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;Lnet/minecraft/client/renderer/SkyRenderer;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFog(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V")
   )
   private static void applySkyFogSlice(Args arguments) {
      if (skyboxModule.isToggled()
         && skyboxModule.splitFogSetting.getValue()
         && skyboxModule.isCurrentDimensionEnabled()) {
         arguments.set(
            0,
            skyboxModule.getSkySlice()
         );
      }
   }

   @WrapWithCondition(
      method = "submitEntities",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
      )
   )
   private boolean shouldSubmitVisibleEntity(
      EntityRenderDispatcher dispatcher, EntityRenderState renderState, CameraRenderState cameraState, double x, double y, double z, PoseStack poses, SubmitNodeCollector collector
   ) {
      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      return (
            !noRenderModule.entitiesSetting.getValue()
               || entity == null
               || !noRenderModule.shouldHideEntity(entity)
         )
         && !chamsModule.shouldRenderThroughWalls(
            entity
         );
   }

   @Inject(method = "resize", at = @At("HEAD"))
   private void resizeClientRenderTargets(int width, int height, CallbackInfo callback) {
      RenderTargetRegistry.resizeRenderTargets(width, height);
   }

   @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
   private boolean applyFreecamDetachedCamera(boolean originalDetached) {
      return freecam.isToggled() || originalDetached;
   }

   @Inject(method = "addWeatherPass", at = @At("HEAD"))
   private void beginWeatherPassColor(
      FrameGraphBuilder frameGraph, GpuBufferSlice fogBuffer, CallbackInfo callback
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 0.25F);
   }

   @Inject(method = "addWeatherPass", at = @At("TAIL"))
   private void resetWeatherPassColor(
      FrameGraphBuilder frameGraph, GpuBufferSlice fogBuffer, CallbackInfo callback
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   // 26.1.2: las lecturas de EnvironmentAttributeProbe.getValue migraron de renderLevel
   // a extractLevel (CLOUD_COLOR ordinal 0, CLOUD_HEIGHT ordinal 1).
   @ModifyExpressionValue(
      method = "extractLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;", ordinal = 1)
   )
   private Object applyCloudHeight(Object originalHeight) {
      return skyboxModule.getCloudHeight((Float)originalHeight);
   }

   @ModifyExpressionValue(
      method = "extractLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;", ordinal = 0)
   )
   private Object applyCloudColor(Object originalColor) {
      return skyboxModule.isToggled()
            && skyboxModule.cloudsSetting.getValue()
         ? (skyboxModule.cloudsColorSetting.getValue())
            .hashCode()
         : originalColor;
   }

   @Inject(method = "renderLevel", at = @At("HEAD"))
   private void hideGuiForFreecamHands(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      CameraRenderState cameraState,
      Matrix4fc inputMatrix,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      ChunkSectionsToRender chunkSections,
      CallbackInfo callback
   ) {
      if (freecam.shouldHideHands()
         && IrisShaderBridge.isHandRendererAvailable()) {
         this.savedHideGui = new AtomicBoolean();
         this.savedHideGui
            .setPlain(ClientAccess.zioMinecraft.options.hideGui);
         ClientAccess.zioMinecraft.options.hideGui = true;
      }
   }

   @Inject(method = "renderLevel", at = @At("RETURN"))
   private void restoreGuiAfterFreecamHands(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      CameraRenderState cameraState,
      Matrix4fc inputMatrix,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      ChunkSectionsToRender chunkSections,
      CallbackInfo callback
   ) {
      if (freecam.shouldHideHands()
         && this.savedHideGui != null) {
         ClientAccess.zioMinecraft.options.hideGui = this.savedHideGui
            .getPlain();
         this.savedHideGui = null;
      }
   }

   @Inject(
      method = "renderLevel",
      at = @At("HEAD")
   )
   private void captureCullFrustum(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      CameraRenderState cameraState,
      Matrix4fc inputMatrix,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      ChunkSectionsToRender chunkSections,
      CallbackInfo callback
   ) {
      if (cameraState.cullFrustum != null) {
         RotationManager.setFrustum(cameraState.cullFrustum);
      }
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void initializeFramebufferStacks(CallbackInfo callback) {
      this.entityOutlineTargetStack = new ObjectArrayList<>();
      this.entityOutlineHandleStack = new ObjectArrayList<>();
   }

   @Override
   public void zio$pushFramebuffer(RenderTarget renderTarget) {
      this.entityOutlineTargetStack.push(this.entityOutlineTarget);
      this.entityOutlineTarget = renderTarget;
      this.entityOutlineHandleStack.push(this.targets.entityOutline);
      this.targets.entityOutline = () -> renderTarget;
   }

   @Override
   public void zio$popFramebuffer() {
      this.entityOutlineTarget = this.entityOutlineTargetStack.pop();
      this.targets.entityOutline = this.entityOutlineHandleStack.pop();
   }
}
