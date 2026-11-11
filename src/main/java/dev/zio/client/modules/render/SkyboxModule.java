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
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.mixin.ClientLevelAccessor;
import dev.zio.client.events.EventHandler;
import dev.zio.client.render.ClientRenderTypes;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import java.util.function.Predicate;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;

public class SkyboxModule extends Module {
   public final Setting<SkyType> typeSetting;
   public final Setting<Boolean> splitFogSetting;
   public final Setting<Boolean> fancyStarsSetting;
   public final Setting<Color> starsColorSetting;
   public final Setting<Boolean> shaderSetting;
   public final Setting<Float> parallaxSetting;
   public final Setting<Color> colorSetting;
   public final Setting<Boolean> cloudsSetting;
   public final Setting<Float> heightSetting;
   public final Setting<Color> cloudsColorSetting;
   public final Setting<Boolean> skyColorSetting;
   public final Setting<Boolean> denseSetting;
   public final Setting<Color> fogSetting;
   public final Setting<Color> skySetting;
   public final Setting<Color> backgroundSetting;
   private final Setting<Boolean> dimensionsSetting;
   private final Setting<Boolean> overworldSetting;
   private final Setting<Boolean> netherSetting;
   private final Setting<Boolean> endSetting;
   private MappableRingBuffer skyFogBuffer;
   private volatile boolean environmentRefreshPending;

   @Override
   public void onToggle() {
      this.requestEnvironmentRefresh();
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundRespawnPacket || event.getPacket() instanceof ClientboundLoginPacket) {
         this.requestEnvironmentRefresh();
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (!this.environmentRefreshPending) {
         return;
      }

      ClientLevelAccessor level = (ClientLevelAccessor)ClientAccess.zioMinecraft.level;
      level.zio$setEnvironmentAttributes(
         level.zio$addEnvironmentAttributeLayers(EnvironmentAttributeSystem.builder().addDefaultLayers(ClientAccess.zioMinecraft.level)).build()
      );
      this.environmentRefreshPending = false;
   }

   private void requestEnvironmentRefresh() {
      this.environmentRefreshPending = true;
   }

   public Float getCloudHeight(Float originalHeight) {
      if (this.isToggled()) {
         Boolean cloudsEnabled = this.cloudsSetting.getValue();
         if (cloudsEnabled) {
            return this.heightSetting.getValue();
         }
      }

      return originalHeight;
   }

   public void drawShaderQuad() {
      Tesselator tesselatorCapture = Tesselator.getInstance();
      VertexFormat positionFormat = DefaultVertexFormat.POSITION;
      Mode quadMode = Mode.QUADS;
      BufferBuilder buffer = tesselatorCapture.begin(quadMode, positionFormat);

      buffer.addVertex(-1.0F, -1.0F, 0.0F);

      buffer.addVertex(-1.0F, 1.0F, 0.0F);

      buffer.addVertex(1.0F, 1.0F, 0.0F);

      buffer.addVertex(1.0F, -1.0F, 0.0F);
      RenderType renderTypeCapture = ClientRenderTypes.entityShader();
      MeshData mesh = buffer.buildOrThrow();
      renderTypeCapture.draw(mesh);
   }

   public boolean isCurrentDimensionEnabled() {
      return switch (WorldUtils.getDimension()) {
         case OVERWORLD -> this.overworldSetting.getValue();
         case THE_NETHER -> this.netherSetting.getValue();
         default -> this.endSetting.getValue();
      };
   }

   public float getParallax() {
      return (this.parallaxSetting.getValue());
   }

   public MappableRingBuffer getSkyFogBuffer() {
      if (this.skyFogBuffer == null) {
         this.skyFogBuffer = new MappableRingBuffer(
            () -> "Sky Fog UBO", skyFogBufferUsage(), FogRenderer.FOG_UBO_SIZE
         );
      }

      return this.skyFogBuffer;
   }

   private static int skyFogBufferUsage() {
      return GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE;
   }

   public Color getShaderColor() {
      return this.colorSetting.getValue();
   }

   public GpuBufferSlice getSkySlice() {

      MappableRingBuffer fogBuffer = this.getSkyFogBuffer();
      GpuBuffer bufferCapture = fogBuffer.currentBuffer();
      long sliceLength = FogRenderer.FOG_UBO_SIZE;
      return bufferCapture.slice(0L, sliceLength);
   }

   public SkyboxModule() {
      super("Skybox", "Changes vanilla sky rendering.", ModuleCategory.RENDER, new String[0]);
      this.typeSetting = this.add(new EnumSetting<>("Type", SkyboxModule.SkyType.NONE));
      this.splitFogSetting = this.add(new BooleanSetting("SplitFog", false));
      this.fancyStarsSetting = this.add(new BooleanSetting("FancyStars", false).markVisibilityDependency());
      this.starsColorSetting = this.add(new ColorSetting("Color", new Color(-1, true)).dependsOn(this.fancyStarsSetting));
      this.shaderSetting = this.add(new BooleanSetting("Shader", false).markVisibilityDependency());
      this.parallaxSetting = this.add(new NumberSetting<>("Parallax", 1.0F, 0.0F, 1.5F).dependsOn(this.shaderSetting));
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-2900225, true)).dependsOn(this.shaderSetting));
      this.cloudsSetting = this.add(new BooleanSetting("Clouds", false).markVisibilityDependency());
      this.heightSetting = this.add(new NumberSetting<>("Height", 192.0F, 0.0F, 256.0F).dependsOn(this.cloudsSetting));
      this.cloudsColorSetting = this.add(new ColorSetting("CloudsColor", new Color(-1, true)).dependsOn(this.cloudsSetting));
      this.skyColorSetting = this.add(new BooleanSetting("SkyColor", false).markAsGroup().markVisibilityDependency());
      this.denseSetting = this.add(new BooleanSetting("Dense", false).dependsOn(this.skyColorSetting));
      this.fogSetting = this.add(new ColorSetting("Fog", new Color(-4351489, true)).dependsOn(this.skyColorSetting));
      this.skySetting = this.add(new ColorSetting("Sky", new Color(-4351489, true)).dependsOn(this.skyColorSetting));
      this.backgroundSetting = this.add(new ColorSetting("Background", new Color(-2900225, true)).dependsOn(this.skyColorSetting));
      this.dimensionsSetting = this.add(new BooleanSetting("Dimensions", true).markAsGroup().markVisibilityDependency());
      this.overworldSetting = this.add(new BooleanSetting("Overworld", true).dependsOn(this.dimensionsSetting));
      this.netherSetting = this.add(new BooleanSetting("Nether", true).dependsOn(this.dimensionsSetting));
      this.endSetting = this.add(new BooleanSetting("End", true).dependsOn(this.dimensionsSetting));

      this.setDrawn(false);
      this.typeSetting.setChangeCallback(this::requestEnvironmentRefresh);
      Setting<?> visibilitySetting = this.starsColorSetting;

      visibilitySetting.setConfigName("StarsColor");
      visibilitySetting = this.skySetting;
      Predicate<Object> visibilityPredicate = ignoredValue -> {
         Setting<SkyType> typeSettingSnapshot = this.typeSetting;
         return typeSettingSnapshot.getValue() != SkyboxModule.SkyType.END;
      };

      visibilitySetting.addVisibilityPredicate(visibilityPredicate);
      visibilitySetting = this.denseSetting;
      visibilityPredicate = ignoredValue -> {
         Setting<SkyType> typeSettingSnapshot = this.typeSetting;
         if (typeSettingSnapshot.getValue() != SkyboxModule.SkyType.FLAT) {
            Boolean netherEnabled = this.netherSetting.getValue();
            if (!netherEnabled) {
               Boolean endEnabled = this.endSetting.getValue();
               if (!endEnabled) {
                  return false;
               }
            }

            return this.typeSetting.getValue() == SkyboxModule.SkyType.NONE;
         } else {
            return true;
         }
      };

      visibilitySetting.addVisibilityPredicate(visibilityPredicate);
   }

   public record FogDistances(
      float environmentalStart,
      float environmentalEnd,
      float renderDistanceStart,
      float renderDistanceEnd,
      float skyEnd,
      float cloudsEnd
   ) {

   }

   public enum SkyType implements NamedEntry {
      NONE("Vanilla"),
      OVERWORLD("Overworld"),
      FLAT("Flat"),
      END("End");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      SkyType(String displayName) {
         this.displayName = displayName;
      }
   }
}
