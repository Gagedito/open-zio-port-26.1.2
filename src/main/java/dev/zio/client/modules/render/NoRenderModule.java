/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import java.util.Set;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.ClientLevelAddEntityEvent;
import dev.zio.client.events.EntityRenderEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.StatusEffectQueryEvent;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.LivingEntityModelSubmitEvent;
import dev.zio.client.events.NameTagRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.ParticleEngineAddEvent;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.DimensionType;
import dev.zio.client.settings.options.ListFilterMode;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collection;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ExplodeParticle;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.SpellParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class NoRenderModule extends Module {
   private final Setting<Boolean> selfGroupSetting;
   public final Setting<Boolean> hurtCamSetting;
   private final Setting<Boolean> potionParticlesSetting;
   public final Setting<Boolean> darknessSetting;
   public final Setting<Boolean> blindnessSetting;
   public final Setting<Boolean> newSneakingSetting;
   public final Setting<Boolean> totemOverlaySetting;
   public final Setting<Boolean> handsSetting;
   private final Setting<Float> handOpacitySetting;
   private final Setting<Boolean> uiGroupSetting;
   public final Setting<Boolean> messageIndicatorSetting;
   public final Setting<Boolean> unsecureServerSetting;
   public final Setting<Boolean> tutorialToastSetting;
   public final Setting<Boolean> textShadowSetting;
   public final Setting<Boolean> bossBarsSetting;
   public final Setting<Boolean> scoreBoardSetting;
   public final Setting<Boolean> actionBarSetting;
   public final Setting<Boolean> tabIconsSetting;
   public final Setting<Boolean> heldTooltipsSetting;
   public final Setting<Boolean> tintSetting;
   private final Setting<Boolean> worldGroupSetting;
   public final Setting<Boolean> explosionsSetting;
   public final Setting<Boolean> worldBorderSetting;
   public final Setting<Boolean> skyLightSetting;
   public final Setting<Boolean> celestialBodiesSetting;
   public final Setting<Boolean> starsSetting;
   public final Setting<Boolean> mapsSetting;
   private final Setting<Boolean> chunkRadiusPacketsSetting;
   public final Setting<Boolean> blockFireSetting;
   public final Setting<Boolean> vinesSetting;
   public final Setting<Boolean> fogSetting;
   public final Setting<Float> rangeSetting;
   public Setting<Boolean> skySetting;
   public final Setting<Boolean> particlesSetting;
   public final Setting<Set<ParticleType<?>>> particleTypesSetting;
   public final Setting<ListFilterMode> particleSelectionSetting;
   private final Setting<Boolean> blockBreakingParticlesSetting;
   private final Setting<Boolean> entityGroupSetting;
   public final Setting<Boolean> hurtSetting;
   public final Setting<Boolean> shadowSetting;
   public final Setting<Boolean> glowSetting;
   public final Setting<Boolean> nameTagsSetting;
   public final Setting<Boolean> interpolationSetting;
   public final Setting<Boolean> armorTrimSetting;
   public final Setting<Boolean> elytraSetting;
   public final Setting<Boolean> fireSetting;
   public final Setting<Boolean> othersSetting;
   public final Setting<Boolean> armorSetting;
   public final Setting<Float> armorOpacitySetting;
   protected final Setting<Boolean> noClusterSetting;
   protected final Setting<Integer> clusterAlphaSetting;
   public final Setting<Boolean> wardensSetting;
   public final Setting<Integer> wardenDistanceSetting;
   public final Setting<Boolean> itemsSetting;
   public final Setting<Integer> limitSetting;
   public final Setting<Boolean> tileEntitiesSetting;
   public final Setting<Set<BlockEntityType<?>>> tileTypesSetting;
   public final Setting<ListFilterMode> blockEntitySelectionSetting;
   public final Setting<Integer> tileDistanceSetting;
   public final Setting<Boolean> entitiesSetting;
   public final Setting<Set<EntityType<?>>> entityTypesSetting;
   public final Setting<RemovalChoice> removalSetting;
   public final Setting<ListFilterMode> entitySelectionSetting;
   private static final Map<Block, BlockEntityType<?>> BLOCK_ENTITY_TYPES_BY_BLOCK;
   private boolean worldRenderPassActive;
   private float lastEntityAlpha;
   private BlockPos pendingCrystalFirePosition;

   static {
      var typesByBlock = new Object2ObjectOpenHashMap<Block, BlockEntityType<?>>();
      var blockEntityTypes = BuiltInRegistries.BLOCK_ENTITY_TYPE;
      blockEntityTypes.forEach(blockEntityType -> {
         var validBlocks = blockEntityType.validBlocks;
         validBlocks.forEach(block -> typesByBlock.put(block, blockEntityType));
      });
      BLOCK_ENTITY_TYPES_BY_BLOCK = typesByBlock;
   }

   @Override
   public void onToggle() {
      Setting<Boolean> tileEntities = this.tileEntitiesSetting;
      if (tileEntities.getValue()) {
         WorldUtils.reloadRenderer();
      }
   }

   public boolean shouldHideBlock(BlockState state) {
      if (!this.isToggled()) return false;

      Setting<Boolean> fire = this.blockFireSetting;
      if (fire.getValue()) {
         Block fireBlock = Blocks.FIRE;
         if (state.is(fireBlock)) return true;
      }

      if (!(this.vinesSetting.getValue())) return false;
      return this.isVineLikeBlock(state);
   }

   public boolean shouldHideEntity(Entity entity) {
      EntityType type = entity.getType();
      return this.shouldHideEntityType(type);
   }

   public boolean shouldHideTabIcons() {
      if (!this.isToggled()) return false;
      return this.tabIconsSetting.getValue();
   }

   public float getHandOpacity() {
      if (this.isToggled()) {
         Setting<Boolean> hands = this.handsSetting;
         if (hands.getValue() && !ClientContext.shaderRegistry.renderingIntoTarget && !ClientContext.shaderRegistry.renderingHands) {
            float alpha = this.handOpacitySetting.getValue();
            if (alpha == 0.0F) return 0.0F;
            return Math.max(alpha, 0.11F);
         }
      }

      return 1.0F;
   }

   public boolean shouldHideScreenTint() {
      if (!this.isToggled()) return false;
      return this.tintSetting.getValue();
   }

   public boolean shouldHideCelestialBodies() {
      if (!this.isToggled()) return false;
      return this.celestialBodiesSetting.getValue();
   }

   public boolean shouldHideStars() {
      if (!this.isToggled()) return false;
      return this.starsSetting.getValue();
   }

   public boolean shouldHideMaps() {
      if (!this.isToggled()) return false;
      return this.mapsSetting.getValue() && this.worldRenderPassActive;
   }

   public float getEntityAlpha() {
      if (!this.isToggled()) {
         return 1.0F;
      }
      return this.lastEntityAlpha;
   }

   public boolean shouldHideEntityShadow() {
      if (!this.isToggled()) return false;
      return this.shadowSetting.getValue();
   }

   public boolean areHandsFullyHidden() {
      if (!this.isToggled()) return false;
      if (!(this.handsSetting.getValue()) || ClientContext.shaderRegistry.renderingHands || ClientContext.shaderRegistry.renderingIntoTarget) return false;
      return (this.handOpacitySetting.getValue()) == 0.0F;
   }

   @EventHandler
   private void onWorldRenderFinished(WorldRenderEvent.Main event) {
      this.worldRenderPassActive = false;
   }

   public boolean shouldHideBlockBreakingParticles() {
      if (!this.isToggled()) return false;
      if (!(this.particlesSetting.getValue())) return false;
      return this.blockBreakingParticlesSetting.getValue();
   }

   private void pruneExcessItems() {
      if (this.isWorldUnavailable()) return;
      if (!(this.itemsSetting.getValue())) return;


      IntArrayList excessIds = new IntArrayList(50);
      int itemCount = 0;
      ClientLevel level = ClientAccess.zioMinecraft.level;
      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof ItemEntity) {
            int previousCount = itemCount++;
            Integer maximum = this.limitSetting.getValue();
            if (previousCount >= maximum) {
               int id = entity.getId();
               excessIds.add(id);
            }
         }
      }
      IntConsumer removeItem = id -> {
         ClientLevel removalLevel = ClientAccess.zioMinecraft.level;
         int entityId = id;
         RemovalReason reason = RemovalReason.KILLED;
         removalLevel.removeEntity(entityId, reason);
      };
      excessIds.forEach(removeItem);
   }

   public float getArmorOpacity() {
      if (this.isToggled()) {
         Setting<Boolean> armor = this.armorSetting;
         if (armor.getValue()) {
            float alpha = this.armorOpacitySetting.getValue();
            if (alpha == 0.0F) return 0.0F;
            return Math.max(alpha, 0.11F);
         }
      }

      return 1.0F;
   }

   @EventHandler
   private void onEntityRenderCheck(EntityRenderEvent.Pre event) {
      Entity entity = event.getEntity();
      if (!this.shouldHideEntity(entity)) return;
      if (event.getEntity() == ClientAccess.zioMinecraft.player) return;
      Setting<RemovalChoice> removal = this.removalSetting;
      if (removal.getValue() == NoRenderModule.RemovalChoice.FULL) {
         event.cancel();
      }
   }

   public void applyFogSettings(FogData fog, int viewDistance) {
      Setting<Float> range = this.rangeSetting;
      if (range.isAtMaximum()) {

         float distance = Math.max(1024.0F, viewDistance * 160.0F);
         fog.renderDistanceEnd = distance;
         fog.renderDistanceStart = distance;
         if (this.skySetting.getValue()) {
            fog.environmentalEnd = distance;
            fog.environmentalStart = distance;
         }
      } else {
         fog.renderDistanceStart = 0.0F;
         Float configuredRange = this.rangeSetting.getValue();
         float scaledRange = 0.5F * configuredRange;
         fog.renderDistanceEnd = scaledRange * (viewDistance * 16);
         Setting<Boolean> sky = this.skySetting;
         if (sky.getValue()) {
            fog.environmentalStart = 0.0F;
            Float configuredSkyRange = this.rangeSetting.getValue();
            float scaledSkyRange = 0.5F * configuredSkyRange;
            fog.environmentalEnd = scaledSkyRange * (viewDistance * 16);
         }
      }
   }

   @EventHandler
   private void onStatusEffectRender(StatusEffectQueryEvent event) {
      Setting<Boolean> blindness = this.blindnessSetting;
      if (blindness.getValue()) {
         if (event.getEffect() == MobEffects.BLINDNESS) {
            event.cancel();
         }
      }
      Setting<Boolean> darkness = this.darknessSetting;
      if (darkness.getValue()) {
         if (event.getEffect() == MobEffects.DARKNESS) {
            event.cancel();
         }
      }
   }

   @EventHandler(priority = -100)
   private void updateEntityAlpha(LivingEntityModelSubmitEvent event) {
      Setting<Boolean> noCluster = this.noClusterSetting;
      if (noCluster.getValue()) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         Vec3 playerPosition = player.position();
         Vec3 entityPosition = (event.getEntity()).position();
         double distance = playerPosition.distanceTo(entityPosition);
         float width = (event.getEntity()).getBbWidth();
         if (distance <= width) {
            if (event.getEntity() != ClientAccess.zioMinecraft.player) {
               float distanceRatio = (float)(distance / width);
               float alphaValue = (this.clusterAlphaSetting.getValue()).intValue();
               float minimumAlpha = alphaValue / 255.0F;
               float alpha = minimumAlpha + distanceRatio * (1.0F - minimumAlpha);
               event.setAlpha(alpha);
               this.lastEntityAlpha = event.getAlpha();
               return;
            }
         }
         this.lastEntityAlpha = 1.0F;
      } else {
         this.lastEntityAlpha = 1.0F;
      }
   }

   @EventHandler
   private void onClientLevelAddEntityEvent(ClientLevelAddEntityEvent event) {
      if (!(event.getEntity() instanceof ItemEntity)) return;
      if (this.isWorldUnavailable()) return;
      if (!(this.itemsSetting.getValue())) return;
      int itemCount = 0;
      ClientLevel level = ClientAccess.zioMinecraft.level;
      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof ItemEntity) {
            int previousCount = itemCount++;
            Integer maximum = this.limitSetting.getValue();
            if (previousCount > maximum) {
               event.cancel();
               break;
            }
         }
      }
   }

   public boolean shouldHideEntityType(EntityType type) {
      if (!this.isToggled()) return false;
      if (!(this.entitiesSetting.getValue())) return false;
      Setting<ListFilterMode> entitySelectionSetting = this.entitySelectionSetting;
      ListFilterMode selection = entitySelectionSetting.getValue();
      Collection selectedTypes = this.entityTypesSetting.getValue();
      return !ListFilterMode.matches(selection, type, selectedTypes);
   }

   public boolean shouldHideParticle(ParticleOptions options) {
      if (!this.isToggled()) return false;
      if (!(this.particlesSetting.getValue())) return false;
      ListFilterMode selection = this.particleSelectionSetting.getValue();
      ParticleType type = options.getType();
      Collection selectedTypes = this.particleTypesSetting.getValue();
      return !ListFilterMode.matches(selection, type, selectedTypes);
   }

   public boolean shouldHideBlockEntityType(BlockEntityType type) {
      if (!this.isToggled()) return false;
      if (!(this.tileEntitiesSetting.getValue())) return false;
      ListFilterMode selection = this.blockEntitySelectionSetting.getValue();
      Collection selectedTypes = this.tileTypesSetting.getValue();
      return !ListFilterMode.matches(selection, type, selectedTypes);
   }

   @EventHandler
   private void onNameTagRender(NameTagRenderEvent event) {
      Setting<Boolean> nameTags = this.nameTagsSetting;
      if (nameTags.getValue()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onParticleEngineAddEvent(ParticleEngineAddEvent event) {
      Particle particle = event.getParticle();
      if (particle instanceof SmokeParticle smoke) {
         double speedSquared = smoke.xd * smoke.xd + smoke.yd * smoke.yd + smoke.zd * smoke.zd;
         double speed = Math.sqrt(speedSquared);
         if (speed > 0.25D) {
            Setting<Boolean> explosions = this.explosionsSetting;
            if (explosions.getValue()) {
               event.cancel();
            }
         }
         if (speed <= 0.25D) {
            Setting<Boolean> fire = this.blockFireSetting;
            if (fire.getValue()) {
               event.cancel();
            }
         }
      }
      if (particle instanceof HugeExplosionParticle || particle instanceof ExplodeParticle) {
         Setting<Boolean> explosions = this.explosionsSetting;
         if (explosions.getValue()) {
            event.cancel();
         }
      }
      Setting<Boolean> potions = this.potionParticlesSetting;
      if (potions.getValue() && particle instanceof SpellParticle) {
         event.cancel();
      }
   }

   private boolean isVineLikeBlock(BlockState state) {
      if (state.getBlock() instanceof VineBlock) return true;
      Block caveVines = Blocks.CAVE_VINES;
      if (state.is(caveVines)) return true;
      Block caveVinesPlant = Blocks.CAVE_VINES_PLANT;
      if (state.is(caveVinesPlant)) return true;
      Block glowLichen = Blocks.GLOW_LICHEN;
      return state.is(glowLichen);
   }

   @EventHandler
   private void onWorldRenderStarted(WorldRenderEvent.BeforeBufferSource event) {
      this.worldRenderPassActive = true;
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundSetChunkCacheRadiusPacket) {
         Setting<Boolean> chunks = this.chunkRadiusPacketsSetting;
         if (chunks.getValue()) {
            event.cancel();
         }
      }
      if (!(this.fireSetting.getValue())) return;
      if (WorldUtils.getDimension() != DimensionType.THE_END) return;
      if (event.getPacket() instanceof ClientboundBlockUpdatePacket update && this.pendingCrystalFirePosition != null) {
         if (!update.getPos().equals(this.pendingCrystalFirePosition)) return;
         BlockState state = update.getBlockState();
         Block fireBlock = Blocks.FIRE;
         if (!state.is(fireBlock)) return;
         this.pendingCrystalFirePosition = null;
         event.cancel();
      }
      if (event.getPacket() instanceof ClientboundAddEntityPacket spawn) {
         if (spawn.getType() == EntityType.END_CRYSTAL) {
            double x = spawn.getX();
            double y = spawn.getY();
            double z = spawn.getZ();
            this.pendingCrystalFirePosition = BlockPos.containing(x, y, z);
         }
      }
   }

   public boolean shouldHideBlockEntity(BlockState state) {
      Map<Block, BlockEntityType<?>> typesByBlock = BLOCK_ENTITY_TYPES_BY_BLOCK;
      Block block = state.getBlock();
      BlockEntityType<?> type = typesByBlock.get(block);
      if (type == null) return false;
      return this.shouldHideBlockEntityType(type);
   }

   public NoRenderModule() {
      super("NoRender", "Removes the annoying stuff from your screen.", ModuleCategory.RENDER, new String[0]);
      this.selfGroupSetting = this.add(new BooleanSetting("Self", false).markAsGroup());
      this.hurtCamSetting = this.add(new BooleanSetting("HurtCam", true).dependsOn(this.selfGroupSetting));
      this.potionParticlesSetting = this.add(new BooleanSetting("Potions", false).dependsOn(this.selfGroupSetting));
      this.darknessSetting = this.add(new BooleanSetting("Darkness", true).dependsOn(this.selfGroupSetting));
      this.blindnessSetting = this.add(new BooleanSetting("Blindness", true).dependsOn(this.selfGroupSetting));
      this.newSneakingSetting = this.add(new BooleanSetting("NewSneaking", false).dependsOn(this.selfGroupSetting));
      this.totemOverlaySetting = this.add(new BooleanSetting("TotemOverlay", false).dependsOn(this.selfGroupSetting));
      this.handsSetting = this.add(new BooleanSetting("Hands", false).markVisibilityDependency().dependsOn(this.selfGroupSetting));
      this.handOpacitySetting = this.add(new NumberSetting<>("Opacity", 0.0F, 0.0F, 1.0F).dependsOn(this.selfGroupSetting, this.handsSetting));
      this.uiGroupSetting = this.add(new BooleanSetting("UI", false).markAsGroup());
      this.messageIndicatorSetting = this.add(new BooleanSetting("MessageIndicator", true).dependsOn(this.uiGroupSetting));
      this.unsecureServerSetting = this.add(new BooleanSetting("UnsecureServer", true).dependsOn(this.uiGroupSetting));
      this.tutorialToastSetting = this.add(new BooleanSetting("TutorialToast", true).dependsOn(this.uiGroupSetting));
      this.textShadowSetting = this.add(new BooleanSetting("TextShadow", false).dependsOn(this.uiGroupSetting));
      this.bossBarsSetting = this.add(new BooleanSetting("BossBars", false).dependsOn(this.uiGroupSetting));
      this.scoreBoardSetting = this.add(new BooleanSetting("ScoreBoard", false).dependsOn(this.uiGroupSetting));
      this.actionBarSetting = this.add(new BooleanSetting("ActionBar", true).dependsOn(this.uiGroupSetting));
      this.tabIconsSetting = this.add(new BooleanSetting("TabIcons", false).dependsOn(this.uiGroupSetting));
      this.heldTooltipsSetting = this.add(new BooleanSetting("HeldTooltips", false).dependsOn(this.uiGroupSetting));
      this.tintSetting = this.add(new BooleanSetting("Tint", false).dependsOn(this.uiGroupSetting));
      this.worldGroupSetting = this.add(new BooleanSetting("World", false).markAsGroup());
      this.explosionsSetting = this.add(new BooleanSetting("Explosions", true).dependsOn(this.worldGroupSetting));
      this.worldBorderSetting = this.add(new BooleanSetting("WorldBorder", true).dependsOn(this.worldGroupSetting));
      this.skyLightSetting = this.add(new BooleanSetting("SkyLight", false).dependsOn(this.worldGroupSetting));
      this.celestialBodiesSetting = this.add(new BooleanSetting("CelestialBodies", false).dependsOn(this.worldGroupSetting));
      this.starsSetting = this.add(new BooleanSetting("Stars", false).dependsOn(this.worldGroupSetting));
      this.mapsSetting = this.add(new BooleanSetting("Maps", false).dependsOn(this.worldGroupSetting));
      this.chunkRadiusPacketsSetting = this.add(new BooleanSetting("Chunks", false).dependsOn(this.worldGroupSetting));
      this.blockFireSetting = this.add(new BooleanSetting("Fire", false).dependsOn(this.worldGroupSetting));
      this.vinesSetting = this.add(new BooleanSetting("Vines", false).dependsOn(this.worldGroupSetting));
      this.fogSetting = this.add(new BooleanSetting("Fog", false).markVisibilityDependency().dependsOn(this.worldGroupSetting));
      this.rangeSetting = this.add(new NumberSetting<>("Range", 3.0F, 0.1F, 10.0F).dependsOn(this.fogSetting, this.worldGroupSetting));
      this.skySetting = this.add(new BooleanSetting("Sky", false).dependsOn(this.fogSetting, this.worldGroupSetting));
      this.particlesSetting = this.add(new BooleanSetting("Particles", false).markVisibilityDependency().dependsOn(this.worldGroupSetting));
      this.particleTypesSetting = this.add(new RegistrySetSetting<>("ParticleTypes", BuiltInRegistries.PARTICLE_TYPE, new ParticleType[0])
         .dependsOn(this.particlesSetting, this.worldGroupSetting));
      this.particleSelectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.BLACKLIST).dependsOn(this.particlesSetting, this.worldGroupSetting));
      this.blockBreakingParticlesSetting = this.add(new BooleanSetting("NoBlockBreaking", false).dependsOn(this.particlesSetting, this.worldGroupSetting));
      this.entityGroupSetting = this.add(new BooleanSetting("Entities", false).markAsGroup());
      this.hurtSetting = this.add(new BooleanSetting("Hurt", true).dependsOn(this.entityGroupSetting));
      this.shadowSetting = this.add(new BooleanSetting("Shadow", false).dependsOn(this.entityGroupSetting));
      this.glowSetting = this.add(new BooleanSetting("Glow", true).dependsOn(this.entityGroupSetting));
      this.nameTagsSetting = this.add(new BooleanSetting("NameTags", false).dependsOn(this.entityGroupSetting));
      this.interpolationSetting = this.add(new BooleanSetting("Interpolation", false).dependsOn(this.entityGroupSetting));
      this.armorTrimSetting = this.add(new BooleanSetting("ArmorTrim", false).dependsOn(this.entityGroupSetting));
      this.elytraSetting = this.add(new BooleanSetting("Elytra", false).dependsOn(this.entityGroupSetting));
      this.fireSetting = this.add(new BooleanSetting("Fire", true).markVisibilityDependency().dependsOn(this.entityGroupSetting));
      this.othersSetting = this.add(new BooleanSetting("Others", false).dependsOn(this.entityGroupSetting, this.fireSetting));
      this.armorSetting = this.add(new BooleanSetting("Armor", false).markVisibilityDependency().dependsOn(this.entityGroupSetting));
      this.armorOpacitySetting = this.add(new NumberSetting<>("Opacity", 0.0F, 0.0F, 1.0F).dependsOn(this.entityGroupSetting, this.armorSetting));
      this.noClusterSetting = this.add(new BooleanSetting("NoCluster", false).markVisibilityDependency().dependsOn(this.entityGroupSetting));
      this.clusterAlphaSetting = this.add(new NumberSetting<>("ClusterAlpha", 120, 0, 255).dependsOn(this.noClusterSetting, this.entityGroupSetting));
      this.wardensSetting = this.add(new BooleanSetting("Wardens", false).markVisibilityDependency().dependsOn(this.entityGroupSetting));
      this.wardenDistanceSetting = this.add(new NumberSetting<>("WardenDistance", 32, 1, 128).withUnit("m").dependsOn(this.entityGroupSetting, this.wardensSetting));
      this.itemsSetting = this.add(new BooleanSetting("Items", false).markVisibilityDependency().dependsOn(this.entityGroupSetting));
      this.limitSetting = this.add(new NumberSetting<>("Limit", 128, 0, 256).dependsOn(this.entityGroupSetting, this.itemsSetting));
      this.tileEntitiesSetting = this.add(new BooleanSetting("TileEntities", false).markVisibilityDependency().dependsOn(this.entityGroupSetting));
      this.tileTypesSetting = this.add(new RegistrySetSetting<>("TileTypes", BuiltInRegistries.BLOCK_ENTITY_TYPE, new BlockEntityType[0])
         .dependsOn(this.entityGroupSetting, this.tileEntitiesSetting));
      this.blockEntitySelectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.BLACKLIST).dependsOn(this.entityGroupSetting, this.tileEntitiesSetting));
      this.tileDistanceSetting = this.add(new NumberSetting<>("TileDistance", 64, 1, 256).withUnit("m").dependsOn(this.entityGroupSetting, this.tileEntitiesSetting));
      this.entitiesSetting = this.add(new BooleanSetting("Entities", false).markVisibilityDependency().dependsOn(this.entityGroupSetting));
      this.entityTypesSetting = this.add(new RegistrySetSetting<>("EntityTypes", BuiltInRegistries.ENTITY_TYPE, new EntityType[0])
         .dependsOn(this.entitiesSetting, this.entityGroupSetting));
      this.removalSetting = this.add(new EnumSetting<>("Removal", RemovalChoice.PARTIAL).dependsOn(this.entityGroupSetting, this.entitiesSetting));
      this.entitySelectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.BLACKLIST).dependsOn(this.entityGroupSetting, this.entitiesSetting));
      this.setDrawn(false);
      this.itemsSetting.setChangeCallback(this::pruneExcessItems);
      this.tileEntitiesSetting.setChangeCallback(WorldUtils::reloadRenderer);
      this.tileTypesSetting.setChangeCallback(WorldUtils::reloadRenderer);
      this.blockEntitySelectionSetting.setChangeCallback(WorldUtils::reloadRenderer);
      this.blockFireSetting.setChangeCallback(WorldUtils::reloadRenderer);
      this.vinesSetting.setChangeCallback(WorldUtils::reloadRenderer);
      this.blockFireSetting.setConfigName("FireBlock");
      this.entityGroupSetting.setConfigName("EntitiesPage");
      this.particleSelectionSetting.setConfigName("ParticleSelection");
      this.entitySelectionSetting.setConfigName("EntitySelection");
      this.handOpacitySetting.setConfigName("HandsOpacity");
      this.blockFireSetting.setConfigName("FireBlock");
   }

   public enum RemovalChoice implements NamedEntry {
      PARTIAL("Partial"),
      FULL("Remove");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      RemovalChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
