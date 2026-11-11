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
import dev.zio.client.events.ClientLevelAddEntityEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.MinecraftDisconnectEvent;
import dev.zio.client.managers.BlockColorRegistry;
import dev.zio.client.render.LineMeshRenderer;
import dev.zio.client.render.WorldBoxRenderer;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.audio.BuiltinSound;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.utils.world.ChunkScanner;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.inventory.EquipmentUtils;
import dev.zio.client.audio.SoundClip;
import dev.zio.client.managers.SoundManager;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.utils.collections.NamedIdentifier;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.ColorsModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.IdentifierSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.ColorsSchemeChoice;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.MapColor.Brightness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SearchModule extends Module {
   private static ColorsModule colorsModule;
   private static final MutableBlockPos scanPosition;
   private static final int MAX_CACHED_BLOCKS = 100_000;
   private final Setting<Float> rangeSetting;
   private final Setting<Boolean> blocksSetting;
   private final Setting<Set<Block>> selectedBlocksSetting;
   private final Setting<HeightChoice> heightSetting;
   private final Setting<Boolean> onlyOminousSetting;
   private final Setting<Boolean> ignoreNaturalSetting;
   private final Setting<Boolean> dungeonSetting;
   private final Setting<Boolean> fortressSetting;
   private final Setting<Boolean> bastionSetting;
   private final Setting<Boolean> ancientCitySetting;
   private final Setting<Boolean> trialChambersSetting;
   private final Setting<Boolean> entitiesSetting;
   private final Setting<Set<EntityType<?>>> selectedEntitiesSetting;
   private final Setting<Boolean> ignoreVehiclesSetting;
   private final Setting<Boolean> ignoreNakedsSetting;
   private final Setting<Boolean> soundSetting;
   private final Setting<NamedIdentifier> soundTypeSetting;
   private final Setting<Float> soundVolumeSetting;
   private final Setting<Boolean> renderSetting;
   private final Setting<Integer> fillSetting;
   private final Setting<Integer> outlineSetting;
   private final Setting<Integer> tracersSetting;
   private final Map<BlockPos, SearchResult> blocksByPosition;
   private final Set<BlockPos> knownBlockPositions;
   private final ElapsedTimer soundTimer;
   private final ChunkScanner chunkScanner;
   private boolean alertPending;
   private int minimumBuildHeight;
   private int maximumBuildHeight;

   private boolean shouldSearchEntity(Entity entity) {
      if (entity == ClientAccess.zioMinecraft.player) {
         return false;
      }

      Set<?> selectedEntities = (Set<?>)this.selectedEntitiesSetting.getValue();
      if (!selectedEntities.contains(entity.getType())) {
         return false;
      }

      Boolean ignoreVehicles = this.ignoreVehiclesSetting.getValue();
      if (ignoreVehicles && entity.isVehicle()) {
         return false;
      }

      if (entity instanceof Player player && this.ignoreNakedsSetting.getValue()) {
         boolean wearingArmor = false;
         for (ItemStack armorPiece : EquipmentUtils.getArmorStacks(player)) {
            if (!armorPiece.isEmpty()) {
               wearingArmor = true;
               break;
            }
         }
         if (!wearingArmor) {
            return false;
         }
      }

      if (!this.rangeSetting.isAtMaximum()) {
         float range = this.rangeSetting.getValue();
         if (this.isOutsideRange(entity.blockPosition(), range * range)) {
            return false;
         }
      }
      return true;
   }

   private static Color getBlockColor(BlockState state) {
      BlockColorRegistry blockColors = ClientContext.blockColorRegistry;
      Block block = state.getBlock();

      Color configuredColor = blockColors.getColor(block);
      if (configuredColor != null) {
         return configuredColor;
      }

      if (state.getBlock() == Blocks.NETHER_PORTAL) {
         return new Color(128, 0, 255);
      }

      if (state.getBlock() == Blocks.ENDER_CHEST) {
         return new Color(64, 32, 96);
      }

      ClientLevel colorLevel = ClientAccess.zioMinecraft.level;
      BlockPos colorPosition = BlockPos.ZERO;
      MapColor mapColor = state.getMapColor(colorLevel, colorPosition);
      Brightness brightness = Brightness.HIGH;
      int argb = mapColor.calculateARGBColor(brightness);
      if (state.getBlock() instanceof BedBlock bed) {
         DyeColor bedDye = bed.getColor();
         MapColor bedMapColor = bedDye.getMapColor();
         Brightness bedBrightness = Brightness.HIGH;
         argb = bedMapColor.calculateARGBColor(bedBrightness);
      }

      if (mapColor == MapColor.NONE) {
         argb = -1;
      }

      return new Color(argb, false);
   }

   private Color getEntityColor(Entity entity) {
      Color color = Color.GRAY;
      if (entity instanceof AbstractMinecart) {
         color = Color.blue;
      }

      if (EntityUtils.isHostile(entity)) {
         color = Color.RED;
      }

      if (entity instanceof Player player) {
         SocialRegistry friends = ClientContext.socialRegistry;
         Component nameComponent = player.getName();
         String playerName = nameComponent.getString();
         Color fallbackColor = color;
         color = friends.getColor(playerName, fallbackColor);
      }

      if (entity instanceof Animal || entity instanceof AbstractFish) {
         color = Color.GREEN;
      }

      return color;
   }

   public void restartSearch() {
      this.blocksByPosition.clear();
      this.chunkScanner.stop();
      this.alertPending = false;
      this.chunkScanner.start();
      this.rescanBlocks();
   }

   private void resetChunkScanner() {
      this.chunkScanner.restartExecutor();
      this.blocksByPosition.clear();
   }

   private void rescanBlocks() {
      this.blocksByPosition.clear();
      this.scanBlocksIfWorldAvailable();
   }

   private void scanBlocksIfWorldAvailable() {
      if (!this.isWorldUnavailable()) {
         ChunkScanner scanner = this.chunkScanner;

         scanner.scanLoadedChunks();
      }
   }

   private void detectExistingEntities() {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      for (Entity entity : level.entitiesForRendering()) {
         if (this.shouldSearchEntity(entity)) {
            this.alertPending = true;
            return;
         }
      }
   }

   static {

      colorsModule = (ColorsModule)ClientContext.getModule(ColorsModule.class);
      scanPosition = new MutableBlockPos();
   }

   @Override
   public void onEnable() {
      this.alertPending = false;
      this.chunkScanner.start();
      this.rescanBlocks();
   }

   @Override
   public void onDisable() {
      this.blocksByPosition.clear();
      this.chunkScanner.stop();
      this.knownBlockPositions.clear();
   }

   private void renderBlocks(WorldRenderEvent event) {
      boolean drawFill = !this.fillSetting.isAtMinimum();
      boolean drawOutline = !this.outlineSetting.isAtMinimum();
      boolean drawTracers = !this.tracersSetting.isAtMinimum();
      boolean limitRange = !this.rangeSetting.isAtMaximum();
      float range = this.rangeSetting.getValue();
      float squaredRange = range * range;

      this.blocksByPosition.forEach((position, result) -> {
         result.updateVisibility();
         if (limitRange && this.isOutsideRange(position, squaredRange)) {
            return;
         }

         if (result.visible) {
            if (drawFill) {
               float alpha = this.fillSetting.getValue() * 0.01F;
               WorldBoxRenderer.fillBox(
                  event.getPoseStack(), result.bounds, ARGB.color(alpha, result.argb)
               );
            }
            if (drawOutline) {
               float alpha = this.outlineSetting.getValue() * 0.01F;
               int color = ARGB.color(alpha, result.argb);
               WorldBoxRenderer.outlineBox(event.getPoseStack(), result.bounds, color, 1.0F);
            }
         }

         if (drawTracers) {
            float alpha = this.tracersSetting.getValue() * 0.01F;
            LineMeshRenderer.addTracer(result.center, ARGB.color(alpha, result.argb));
         }
      });
   }

   private static boolean isPositionInChunk(BlockPos position, ChunkPos chunkPosition) {
      return position.getX() >= chunkPosition.getMinBlockX()
         && position.getX() <= chunkPosition.getMaxBlockX()
         && position.getZ() >= chunkPosition.getMinBlockZ()
         && position.getZ() <= chunkPosition.getMaxBlockZ();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.minimumBuildHeight = ClientAccess.zioMinecraft.level.getMinY();

      this.maximumBuildHeight = getMaximumBuildHeight();
      Boolean soundEnabled = this.soundSetting.getValue();
      if (soundEnabled && this.alertPending) {
         this.alertPending = false;
         ElapsedTimer timer = this.soundTimer;

         long soundInterval = 100L;
         if (timer.hasElapsedAndReset(soundInterval)) {
            SoundManager soundManager = ClientContext.soundManager;
            NamedIdentifier soundIdentifier = this.soundTypeSetting.getValue();
            SoundClip sound = soundManager.getSound(soundIdentifier);
            float playbackVolume = (this.soundVolumeSetting.getValue());
            sound.play(playbackVolume);
         }
      }
   }

   private static int getMaximumBuildHeight() {
      return (int)WorldUtils.getTopY();
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {

      this.resetChunkScanner();
   }

   private void renderEntities(WorldRenderEvent event) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      for (Entity entity : level.entitiesForRendering()) {
         if (!this.shouldSearchEntity(entity)) {
            continue;
         }

         AABB bounds = BoundingBoxUtils.getRenderBounds(entity, event.getPartialTick());
         Color color = (colorsModule.schemeSetting.getValue())
            .transform(this.getEntityColor(entity));
         if (RotationManager.isVisible(bounds)) {
            if (!this.fillSetting.isAtMinimum()) {
               float alpha = this.fillSetting.getValue() * 0.01F;
               WorldBoxRenderer.fillBox(event.getPoseStack(), bounds, ColorUtils.withAlpha(color, alpha));
            }
            if (!this.outlineSetting.isAtMinimum()) {
               float alpha = this.outlineSetting.getValue() * 0.01F;
               Color outlineColor = ColorUtils.withAlpha(color, alpha);
               WorldBoxRenderer.outlineBox(event.getPoseStack(), bounds, outlineColor, 1.0F);
            }
         }

         if (!this.tracersSetting.isAtMinimum()) {
            float alpha = this.tracersSetting.getValue() * 0.01F;
            Color tracerColor = ColorUtils.withAlpha(color, alpha);
            Color transformedTracerColor = (colorsModule.schemeSetting.getValue())
               .transform(tracerColor);
            LineMeshRenderer.addTracer(bounds.getCenter(), transformedTracerColor);
         }
      }
   }

   @EventHandler
   private void onClientLevelAddEntityEvent(ClientLevelAddEntityEvent event) {
      Boolean soundEnabled = this.soundSetting.getValue();
      if (soundEnabled) {
         Boolean searchEntities = this.entitiesSetting.getValue();
         if (searchEntities) {
            Entity entity = event.getEntity();

            if (this.shouldSearchEntity(entity)) {
               this.alertPending = true;
            }

            return;
         }
      }
   }

   @EventHandler
   private void onRenderWorld(WorldRenderEvent.Main event) {
      LineMeshRenderer.begin(
         event.getPoseStack(), 1.0F
      );
      Boolean searchEntities = this.entitiesSetting.getValue();
      if (searchEntities) {


         this.renderEntities(event);
      }

      synchronized (this.blocksByPosition) {
         Boolean searchBlocks = this.blocksSetting.getValue();
         if (searchBlocks) {

            this.renderBlocks(event);
         }
      }


      LineMeshRenderer.draw();
   }

   private boolean shouldSearchBlock(BlockState state) {
      if (state.isAir() || !((Set<?>)this.selectedBlocksSetting.getValue()).contains(state.getBlock())) {
         return false;
      }
      if (this.onlyOminousSetting.getValue() && state.is(Blocks.VAULT)) {
         return state.getValueOrElse(VaultBlock.OMINOUS, false);
      }
      return true;
   }

   private boolean isOutsideRange(BlockPos position, float squaredRange) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      Vec3 eyePosition = player.getEyePosition();
      double x = position.getX();
      double y = position.getY();
      double z = position.getZ();
      return eyePosition.distanceToSqr(x, y, z) > squaredRange;
   }

   @EventHandler
   private void onRespawnPacket(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundRespawnPacket) {

         this.resetChunkScanner();
      }
   }

   public static void bindModules(ModuleRegistry registry) {
      colorsModule = (ColorsModule)registry.getModule(
         ColorsModule.class
      );
   }

   public SearchModule() {
      super("Search", "Helps finding whitelisted stuff.", ModuleCategory.RENDER, new String[0]);
      this.rangeSetting = this.add(new NumberSetting<>("Range", 512.0F, 0.0F, 512.0F).withUnit("m"));
      this.blocksSetting = this.add(new BooleanSetting("Blocks", true).markVisibilityDependency());
      this.selectedBlocksSetting = this.add(new RegistrySetSetting<>("Selected", BuiltInRegistries.BLOCK,
         new Block[]{Blocks.NETHER_PORTAL, Blocks.ENDER_CHEST, Blocks.END_PORTAL}).dependsOn(this.blocksSetting));
      this.heightSetting = this.add(new EnumSetting<>("Height", HeightChoice.NEW).dependsOn(this.blocksSetting));
      this.onlyOminousSetting = this.add(new BooleanSetting("OnlyOminous", false).dependsOn(this.blocksSetting));
      this.ignoreNaturalSetting = this.add(new BooleanSetting("IgnoreNatural", false).markVisibilityDependency().dependsOn(this.blocksSetting));
      this.dungeonSetting = this.add(new BooleanSetting("Dungeon", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting));
      this.fortressSetting = this.add(new BooleanSetting("Fortress", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting));
      this.bastionSetting = this.add(new BooleanSetting("Bastion", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting));
      this.ancientCitySetting = this.add(new BooleanSetting("AncientCity", false).dependsOn(this.blocksSetting, this.ignoreNaturalSetting));
      this.trialChambersSetting = this.add(new BooleanSetting("TrialChambers", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting));
      this.entitiesSetting = this.add(new BooleanSetting("Entities", false).markVisibilityDependency());
      this.selectedEntitiesSetting = this.add(new RegistrySetSetting<>("Selected", BuiltInRegistries.ENTITY_TYPE, new EntityType[0])
         .dependsOn(this.entitiesSetting));
      this.ignoreVehiclesSetting = this.add(new BooleanSetting("IgnoreVehicles", false).dependsOn(this.entitiesSetting));
      this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false).dependsOn(this.entitiesSetting));
      this.soundSetting = this.add(new BooleanSetting("Sound", false).markVisibilityDependency());
      this.soundTypeSetting = this.add(new IdentifierSetting("Type", BuiltinSound.WARNING.getIdentifier()).dependsOn(this.soundSetting));
      this.soundVolumeSetting = this.add(new NumberSetting<>("Volume", 1.0F, 0.0F, 1.0F).dependsOn(this.soundSetting));
      this.renderSetting = this.add(new BooleanSetting("Render", false).markAsGroup());
      this.fillSetting = this.add(new NumberSetting<>("Fill", 0, 0, 100).withUnit("%").dependsOn(this.renderSetting));
      this.outlineSetting = this.add(new NumberSetting<>("Outline", 100, 0, 100).withUnit("%").dependsOn(this.renderSetting));
      this.tracersSetting = this.add(new NumberSetting<>("Tracers", 50, 0, 100).withUnit("%").dependsOn(this.renderSetting));
      this.blocksByPosition = Collections.synchronizedMap(new HashMap<>());
      this.knownBlockPositions = Collections.synchronizedSet(new HashSet<>());
      this.soundTimer = new ElapsedTimer();
      this.blocksSetting.setConfigName("SearchBlocks");
      this.entitiesSetting.setConfigName("SearchEntities");
      this.selectedBlocksSetting.setConfigName("Blocks");
      this.selectedEntitiesSetting.setConfigName("Entities");
      this.fillSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.outlineSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.tracersSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.rangeSetting.setBoundaryLabel("Unlimited", SettingLabelCondition.MAX);
      this.onlyOminousSetting.addVisibilityPredicate(ominousIgnoredValue -> {
         return ((Set<?>)this.selectedBlocksSetting.getValue()).contains(Blocks.VAULT);
      });
      this.chunkScanner = new ChunkScanner(
         1,
         (java.util.function.BiConsumer<ChunkPos, LevelChunk>)((chunkPosition, chunk) -> {
            if (this.blocksByPosition.size() > MAX_CACHED_BLOCKS) {
               return;
            }

            int firstY = this.heightSetting.getValue() == HeightChoice.OLD
               ? 0
               : this.minimumBuildHeight;
            for (int localX = 0; localX < 16; localX++) {
               int x = chunkPosition.getMinBlockX() + localX;
               for (int y = firstY; y < this.maximumBuildHeight; y++) {
                  for (int localZ = 0; localZ < 16; localZ++) {
                     scanPosition.set(x, y, chunkPosition.getMinBlockZ() + localZ);
                     BlockState state = chunk.getBlockState(scanPosition);

                     if (this.ignoreNaturalSetting.getValue()
                        && WorldUtils.isStructureChest(
                           chunk,
                           scanPosition,
                           this.fortressSetting.getValue(),
                           this.bastionSetting.getValue(),
                           this.ancientCitySetting.getValue(),
                           this.dungeonSetting.getValue(),
                           this.trialChambersSetting.getValue()
                        )) {
                        continue;
                     }
                     if (!this.shouldSearchBlock(state) || !this.blocksSetting.getValue()) {
                        continue;
                     }

                     BlockPos position = scanPosition.immutable();
                     this.knownBlockPositions.add(position);
                     this.blocksByPosition.put(position, SearchResult.create(position, state));

                     if (!this.rangeSetting.isAtMaximum()) {
                        float range = this.rangeSetting.getValue();
                        if (this.isOutsideRange(position, range * range)) {
                           continue;
                        }
                     }
                     this.alertPending = true;
                  }
               }
            }
         }),
         (java.util.function.BiConsumer<ChunkPos, LevelChunk>)((unloadedChunkPosition, unloadedChunk) -> {
            this.blocksByPosition.keySet().removeIf(position -> isPositionInChunk(position, unloadedChunkPosition));
            this.knownBlockPositions.removeIf(position -> isPositionInChunk(position, unloadedChunkPosition));
         }),
         (java.util.function.BiConsumer<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>)((updatedPosition, updatedState) -> {
            if (updatedState.isAir()) {
               this.knownBlockPositions.remove(updatedPosition);
            }

            Set<?> updatedSelectedBlocks = (Set<?>)this.selectedBlocksSetting.getValue();
            if (updatedSelectedBlocks.contains(updatedState.getBlock())) {
               if (updatedState.isAir()) {
                  return;
               }

               Map<BlockPos, SearchResult> updatedBlocks = this.blocksByPosition;
               updatedBlocks.put(updatedPosition, SearchModule.SearchResult.create(updatedPosition, updatedState));
               boolean updatedNewlyKnown = this.knownBlockPositions.add(updatedPosition);
               Setting<Float> updatedCapturedRangeBoundaryOption = this.rangeSetting;

               if (!updatedCapturedRangeBoundaryOption.isAtMaximum()) {
                  float updatedRangeValue = (this.rangeSetting.getValue());
                  Float updatedSecondRange = this.rangeSetting.getValue();
                  float updatedSquaredRange = updatedRangeValue * updatedSecondRange;

                  if (this.isOutsideRange(updatedPosition, updatedSquaredRange)) {
                     return;
                  }
               }

               this.alertPending = updatedNewlyKnown;
            } else {
               this.blocksByPosition.remove(updatedPosition);
            }
         })
      );
      this.blocksSetting.setChangeCallback(() -> {
         if (this.blocksSetting.getValue()) {
            this.rescanBlocks();
         }
      });
      this.entitiesSetting.setChangeCallback(() -> {
         if (this.entitiesSetting.getValue()) {
            this.detectExistingEntities();
         }
      });
      this.selectedBlocksSetting.setChangeCallback(this::rescanBlocks);
      this.onlyOminousSetting.setChangeCallback(this::rescanBlocks);
      this.heightSetting.setChangeCallback(this::rescanBlocks);
   }

   public enum HeightChoice implements NamedEntry {
      NEW("1.18"),
      OLD("1.12");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      HeightChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   private static class SearchResult {
      private final Vec3 center;
      private final AABB bounds;
      private final int argb;
      private boolean visible;

      public void updateVisibility() {
         AABB box = this.bounds;

         this.visible = RotationManager.isVisible(
            box
         );
      }

      public SearchResult(Vec3 center, AABB bounds, int argb) {
         this.center = center;
         this.bounds = bounds;
         this.argb = argb;
      }

      static SearchModule.SearchResult create(
         BlockPos position, BlockState state
      ) {
         TagKey shulkerTag = BlockTags.SHULKER_BOXES;
         VoxelShape selectedShape;
         if (state.is(shulkerTag)) {
            selectedShape = Shapes.block();
         } else {
            ClientLevel shapeLevel = ClientAccess.zioMinecraft.level;
            selectedShape = state.getShape(shapeLevel, position);
         }

         AABB worldBounds;
         if (selectedShape.isEmpty()) {
            worldBounds = new AABB(position);
         } else {
            AABB shapeBounds = selectedShape.bounds();
            worldBounds = shapeBounds.move(position);
         }

         Color color = SearchModule.getBlockColor(state);
         Setting<ColorsSchemeChoice> schemeOption = SearchModule.colorsModule.schemeSetting;
         ColorsSchemeChoice configuredScheme = schemeOption.getValue();
         color = configuredScheme.transform(color);
         return new SearchModule.SearchResult(getBlockCenter(position), worldBounds, color.hashCode());
      }

      private static Vec3 getBlockCenter(BlockPos position) {
         return position.getCenter();
      }
   }
}
