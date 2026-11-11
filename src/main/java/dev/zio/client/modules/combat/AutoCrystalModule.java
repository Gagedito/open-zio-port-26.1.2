/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.SelectedSlotChangeEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.MinecraftDisconnectEvent;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.managers.ServerStateManager;
import dev.zio.client.utils.inventory.ArmorSlot;
import dev.zio.client.render.LineMeshRenderer;
import dev.zio.client.utils.math.Vec3Utils;
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.combat.ExtraBreakTarget;
import dev.zio.client.managers.CrystalInteractionManager;
import dev.zio.client.utils.inventory.ItemUtils;
import dev.zio.client.utils.combat.DamageUtils;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.combat.EndCrystalState;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.utils.world.BlockBreakEntry;
import dev.zio.client.utils.entity.SwingAction;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.network.ServerSideDeathState;
import dev.zio.client.render.FeatureBoxRenderer;
import dev.zio.client.utils.inventory.EnchantmentUtils;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.LockUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.managers.MovementPredictionManager;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.access.PlayerInfoJoinTime;
import dev.zio.client.events.EventStage;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.misc.KillEffectsModule;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.AutoCrystalInstantChoice;
import dev.zio.client.settings.options.AutoCrystalSequentialChoice;
import dev.zio.client.settings.options.AutoCrystalSilentChoice;
import dev.zio.client.settings.options.AutoCrystalWeaknessChoice;
import dev.zio.client.settings.options.BlockSampleMode;
import dev.zio.client.settings.options.ItemSwapMode;
import dev.zio.client.settings.options.SpeedMineRebreakChoice;
import dev.zio.client.utils.math.MathUtils;
import dev.zio.mixin.ClientboundEntityEventPacketAccessor;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class AutoCrystalModule extends Module {
   private static final ExecutorService calculationExecutor;
   private static SpeedMineModule speedMine;
   private static AntiCheatModule antiCheat;
   private static AnchorAuraModule anchorAura;
   private static AuraModule aura;
   private static AutoExpModule autoExp;
   private final Setting<Boolean> placeSetting;
   private final Setting<Integer> placeDelaySetting;
   private final Setting<Float> placeRangeSetting;
   private final Setting<Float> placeWallRangeSetting;
   final Setting<Float> minPlaceDamageSetting;
   final Setting<Float> maxPlaceSelfDamageSetting;
   final Setting<Float> placeDamageRatioSetting;
   public final Setting<AutoCrystalSequentialChoice> sequentialSetting;
   public final Setting<Boolean> requireUpperAirSetting;
   private final Setting<Boolean> strictDirectionSetting;
   public final Setting<Boolean> lowerHitBoxSetting;
   private final Setting<Boolean> mineIgnoreSetting;
   protected final Setting<Boolean> alwaysIgnoreMiningSetting;
   private final Setting<Integer> miningIgnoreThresholdSetting;
   private final Setting<Boolean> breakSetting;
   public final Setting<Integer> breakDelaySetting;
   private final Setting<Integer> attackFactorSetting;
   private final Setting<Float> breakRangeSetting;
   private final Setting<Float> breakWallRangeSetting;
   final Setting<Float> minBreakDamageSetting;
   final Setting<Float> maxBreakSelfDamageSetting;
   final Setting<Boolean> antiSuicideSetting;
   public final Setting<AutoCrystalInstantChoice> instantSetting;
   private final Setting<Boolean> awaitSetting;
   public final Setting<Integer> ticksExistedSetting;
   public final Setting<AutoCrystalWeaknessChoice> weaknessSetting;
   private final Setting<Boolean> basePlaceSetting;
   private final Setting<Integer> basePlaceDelaySetting;
   private final Setting<Boolean> basePlaceAirSetting;
   public final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> rotatePlaceSetting;
   private final Setting<Boolean> rotateBreakSetting;
   final Setting<Boolean> facePlaceSetting;
   final Setting<Float> facePlaceHealthSetting;
   final Setting<Integer> facePlaceArmorThresholdSetting;
   final Setting<Boolean> facePlaceShieldBreakerSetting;
   private final Setting<Boolean> damageSyncSetting;
   private final Setting<Boolean> predictionSetting;
   private final Setting<Integer> predictionTicksSetting;
   private final Setting<Boolean> drawPredictionSetting;
   private final Setting<Boolean> renderSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;
   private final Setting<Float> lineWidthSetting;
   final Setting<Boolean> renderDamageSetting;
   final Setting<Boolean> fadeSetting;
   private final Setting<Boolean> multiFadeSetting;
   final Setting<Float> fadeTimeSetting;
   private final Setting<Boolean> pauseSetting;
   private final Setting<Boolean> pauseMiningSetting;
   private final Setting<Boolean> pauseEatingSetting;
   private final Setting<Boolean> pauseMendingSetting;
   private final Setting<Boolean> targetingSetting;
   final Setting<Boolean> bossesSetting;
   private final Setting<Float> targetRangeSetting;
   final Setting<Float> crystalRangeSetting;
   final Setting<Boolean> antiFriendKillSetting;
   private final Setting<Boolean> antiQuiverSetting;
   final Setting<Boolean> ignoreNakedsSetting;
   public final Setting<Boolean> assumeBestArmorSetting;
   public final Setting<Boolean> forceSuicideSetting;
   private final Setting<Boolean> forceSuicideNoArmorSetting;
   private final Setting<Boolean> assumeInvincibilitySetting;
   private final Setting<Boolean> assumeSelfInvincibilitySetting;
   private final Setting<Float> invincibilityTimeoutSetting;
   private final Setting<Boolean> miscSetting;
   final Setting<ItemSwapMode> autoSwapSetting;
   final Setting<AutoCrystalSilentChoice> silentSetting;
   private final Setting<Boolean> swapBackSetting;
   private final Setting<Boolean> strictTimingSetting;
   private final Setting<Float> swapDelaySetting;
   private final Setting<Float> swapPenaltySetting;
   private final Setting<Boolean> multipointSetting;
   private final AutoCrystalTargeting targeting;
   private final CrystalDamageRenderer damageRenderer;
   private final ElapsedTimer crystalHoldingTimer;
   public final AtomicReference placementData;
   public final AtomicReference rotationTarget;
   public final AtomicBoolean placementGate;
   private final ElapsedTimer rotationTimer;
   private final ElapsedTimer placeTimer;
   private final ElapsedTimer breakTimer;
   private final ElapsedTimer swapDelayTimer;
   private final ElapsedTimer swapPenaltyTimer;
   private final ElapsedTimer sequentialPlacementTimer;
   private final ElapsedTimer attackRetryTimer;
   private final ElapsedTimer basePlaceTimer;
   private final LinkedList<Long> placementTimes;
   private final List<Integer> predictedAttackIds;
   private final Set ignoredBlocks;
   private final MutableBlockPos scanPosition;
   private CompletableFuture<CrystalPlacementResult> calculationFuture;
   protected volatile BlockPos lastPlacedBase;
   protected volatile BlockPos lastAttackedBase;
   protected volatile BlockPos lastForcedBase;
   protected volatile boolean spawnFollowupQueued;
   protected volatile boolean breakRotationRequested;
   protected volatile boolean obsidianDropNearTarget;
   protected volatile boolean quiverThreatActive;
   protected volatile float selectedDamage;
   private int lastAttackedCrystalId;
   private int attackRetries;
   private int ignoredSlotChanges;
   private int restoreSlot;
   private BlockPos previousPlacement;
   private boolean skip;
   private boolean auraHasAttackTarget;
   public LivingEntity target;
   public LivingEntity previousTarget;
   private Vec3 eyePosition;
   static {
      calculationExecutor = Executors.newFixedThreadPool(4);
      speedMine = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
      antiCheat = (AntiCheatModule)ClientContext.getModule(AntiCheatModule.class);
      anchorAura = (AnchorAuraModule)ClientContext.getModule(AnchorAuraModule.class);
      aura = (AuraModule)ClientContext.getModule(AuraModule.class);
      autoExp = (AutoExpModule)ClientContext.getModule(AutoExpModule.class);
   }

   @Override
   public String getInfo() {
      if (this.target != null) {
         LivingEntity targetSnapshot = this.target;
         if (!this.isInvalidTarget(targetSnapshot)) {
            String targetName = this.target instanceof Player player
               ? ClientContext.aliasRegistry.getPlayerAlias(player) : this.target.getPlainTextName();
            Object[] arguments = new Object[3];
            arguments[0] = targetName;
            float displayDamage = this.selectedDamage;
            arguments[1] = displayDamage;

            int placementsPerSecond = this.placementTimes.size();
            arguments[2] = placementsPerSecond;
            return "%s, %.1f, %d".formatted(arguments);
         }
      }

      return super.getInfo();
   }

   @Override
   public void onEnable() {
      this.updateEyePosition();
      this.lastAttackedCrystalId = -1;
      this.previousTarget = this.target;
      this.target = null;
      this.lastPlacedBase = null;

      this.placementData.set(null);
      if (this.calculationFuture != null) {
         try {
            this.calculationFuture.complete(null);
         } catch (CancellationException cancellation) {
         }
      }

      this.swapDelayTimer.reset();

      this.basePlaceTimer.clear();
      this.restoreSlot = -1;
   }

   @Override
   public void onDisable() {
      this.restoreSelectedSlot();
   }

   public void setRotationTarget(Vec3 point) {
      if (point != null) {
         this.rotationTimer.reset();

         this.rotationTarget.set(point);
      }
   }
   private CrystalPlacementResult calculatePlacement(List<LivingEntity> targets) {
      float selectedCrystalRange;
      if (this.shouldIgnoreMiningBlock()) {
         selectedCrystalRange = 999.0F;
      } else {
         selectedCrystalRange = this.breakRangeSetting.getValue();
      }

      boolean ignoreMiningBlock = this.shouldIgnoreMiningBlock();
      Setting<SpeedMineRebreakChoice> rebreakSetting = speedMine.rebreakSetting;
      if (rebreakSetting.getValue() == SpeedMineRebreakChoice.INSTANT) {
         Setting<Boolean> asyncSetting = speedMine.asyncSetting;

         if (!asyncSetting.getValue()) {
            ignoreMiningBlock = false;
         }
      }

      CrystalPlacementResult currentPlacement = (CrystalPlacementResult)this.placementData.get();
      CrystalTargetDamage currentTargetDamage = null;
      CrystalPlacementResult bestPlacement = null;
      CrystalPlacementValidator configuredValidator = new CrystalPlacementValidator()
         .requireUpperAir(this.requireUpperAirSetting.getValue())
         .lowerHitbox(this.lowerHitBoxSetting.getValue())
         .expandMovingPlayers(!RotationManager.isMovementSyncEnabled() && !this.shouldIgnoreMiningBlock())
         .ignoreBaseBlock(this.basePlaceSetting.getValue() && this.canRetryBasePlacement())
         .ignoreYoungItems(ignoreMiningBlock);

      Boolean extrapolate = this.predictionSetting.getValue();
      int selectedPredictionTicks;
      if (extrapolate) {
         selectedPredictionTicks = this.predictionTicksSetting.getValue();
      } else {
         selectedPredictionTicks = 0;
      }

      CrystalPlacementValidator placementValidator = configuredValidator
         .predictionTicks(selectedPredictionTicks)
         .ignoredCrystalRange(selectedCrystalRange);
      // Compare other bases against the previous placement’s rounded damage.
      if (currentPlacement != null) {
         BlockPos storedBaseSnapshot = currentPlacement.base();
         if (placementValidator.canPlace(storedBaseSnapshot)) {
            BlockPos damageBaseSnapshot = currentPlacement.base();
            BlockPos checkBaseSnapshot = currentPlacement.base();
            boolean currentHasBase = WorldUtils.isBedrockOrObsidian(checkBaseSnapshot);
            currentTargetDamage = CrystalTargetDamage.calculateForBase(this, damageBaseSnapshot, currentHasBase, targets);
         }
      }

      for (double offsetX = -Math.floor((this.placeRangeSetting.getValue()).floatValue());
         offsetX < Math.ceil((this.placeRangeSetting.getValue()).floatValue()); offsetX += 1.0D) {
         for (double offsetY = -Math.floor((this.placeRangeSetting.getValue()).floatValue());
            offsetY < Math.ceil((this.placeRangeSetting.getValue()).floatValue()); offsetY += 1.0D) {
            for (double offsetZ = -Math.floor((this.placeRangeSetting.getValue()).floatValue());
               offsetZ < Math.ceil((this.placeRangeSetting.getValue()).floatValue()); offsetZ += 1.0D) {
               if (this.eyePosition == null) {
                  continue;
               }
               double scanXSnapshot = this.eyePosition.x + offsetX;
               double scanYSnapshot = this.eyePosition.y + offsetY;
               double scanZ = this.eyePosition.z + offsetZ;

               this.scanPosition.set(scanXSnapshot, scanYSnapshot, scanZ);

               boolean hasCrystalBase = WorldUtils.isBedrockOrObsidian(this.scanPosition);
               if (bestPlacement != null) {
                  if (bestPlacement.kind()
                        != CrystalPlacementKind.OBSIDIAN
                     && !hasCrystalBase) {
                     continue;
                  }
               }

               Vec3 distanceEyeSnapshot = this.eyePosition;

               Vec3 center = this.scanPosition.getCenter();
               double distance = distanceEyeSnapshot.distanceTo(center);

               Float distanceLimit = this.placeRangeSetting.getValue();
               if (distance > distanceLimit.floatValue() || distance > selectedCrystalRange) {
                  continue;
               }

               if (!placementValidator.canPlace(this.scanPosition)) {
                  continue;
               }
               boolean visible = true;

               Object wallRangeValue = this.placeWallRangeSetting.getValue();
               Setting<Float> visibleRangeProbeSetting = this.placeRangeSetting;
               if (!Objects.equals(wallRangeValue, visibleRangeProbeSetting.getValue())) {
                  BlockSampleMode samplingMode = this.multipointSetting.getValue() ? BlockSampleMode.X8 : BlockSampleMode.FAST;

                  List visibilitySamples = samplingMode.sampleBlock(this.scanPosition);
                  var visibilityEye = this.eyePosition;
                  visible = WorldUtils.canSeeAny(visibilityEye, visibilitySamples);
               }

               if (!visible) {
                  Float wallRangeLimit = this.placeWallRangeSetting.getValue();
                  if (distance > wallRangeLimit.floatValue()) {
                     continue;
                  }
               }

               if (this.strictDirectionSetting.getValue()) {
                  MutableBlockPos wallRangePosition = this.scanPosition;
                  if (BlockInteractionUtils.getStrictDirections(wallRangePosition).isEmpty()) {
                     continue;
                  }
               }

               MutableBlockPos wallRangePosition = this.scanPosition;
               Vec3 explosionPosition = Vec3.upFromBottomCenterOf(wallRangePosition, 1.0D);
               LocalPlayer selfDamagePlayer = ClientAccess.zioMinecraft.player;
               LocalPlayer selfBoundsPlayerSnapshot = ClientAccess.zioMinecraft.player;
               AABB selfBounds = this.getTargetBounds(
                  selfBoundsPlayerSnapshot
               );
               var selfDamageIgnoredPositions = Collections.emptyList();
               var selfDamageScanPosition = this.scanPosition;
               double selfDamage = DamageUtils.getExplosionDamage(
                  explosionPosition,
                  selfDamagePlayer,
                  selfBounds,
                  6.0D,
                  true,
                  selfDamageIgnoredPositions,
                  selfDamageScanPosition
               );
               MutableBlockPos targetDamageBase = this.scanPosition;
               CrystalTargetDamage candidateTargetDamage = CrystalTargetDamage.calculateForBase(this,
                  targetDamageBase, hasCrystalBase, targets
               );
               if (candidateTargetDamage == null) {
                  continue;
               }
               CrystalDamagePolicy placeDamagePolicy = CrystalDamagePolicy.PLACE;
               LivingEntity policyTargetSnapshot = candidateTargetDamage.target();
               double policyDamageSnapshot = candidateTargetDamage.damage();
               LivingEntity lethalityTargetSnapshot = candidateTargetDamage.target();
               double lethalityDamage = candidateTargetDamage.damage();
               CrystalLethality lethality = CrystalLethality.classifyLethality(
                  lethalityTargetSnapshot, lethalityDamage
               );
               if (placeDamagePolicy.shouldRejectDamage(
                  policyTargetSnapshot, selfDamage, policyDamageSnapshot, lethality
               )) {
                  continue;
               }
               CrystalPlacementKind placementKind = CrystalPlacementKind.NONE;
               if (currentTargetDamage != null) {
                  if (!currentPlacement.base()
                     .equals(this.scanPosition)) {
                     double previousDamage = currentTargetDamage.damage();
                     double roundedPreviousDamage = Math.round(previousDamage);
                     if (roundedPreviousDamage
                        >= candidateTargetDamage.damage()) {
                        continue;
                     }
                  }
               }

               if (!visible) {
                  Float candidateWallRange = this.placeWallRangeSetting.getValue();
                  if (distance > candidateWallRange.floatValue()) {
                     placementKind = CrystalPlacementKind.RAYTRACE;
                  }
               }

               if (this.basePlaceSetting.getValue() && !hasCrystalBase) {
                  LivingEntity predictedTargetSnapshot = candidateTargetDamage.target();
                  AABB predictedBounds = this.getTargetBounds(
                     predictedTargetSnapshot
                  );
                  LivingEntity currentBoundsTarget = candidateTargetDamage.target();

                  AABB targetBoundsUnion = predictedBounds.minmax(currentBoundsTarget.getBoundingBox());
                  AABB baseBounds = new AABB(
                     this.scanPosition
                  );
                  if (targetBoundsUnion.intersects(baseBounds)) {
                     continue;
                  }

                  wallRangePosition = this.scanPosition;
                  if (!BlockInteractionUtils.canPlaceAt(
                     wallRangePosition, false
                  )) {
                     continue;
                  }

                  if (!this.basePlaceAirSetting.getValue()) {
                     wallRangePosition = this.scanPosition;
                     boolean supportDirectionMode = BlockInteractionUtils.isStrictDirectionEnabled();
                     if (BlockInteractionUtils.findSupportDirection(
                           wallRangePosition, supportDirectionMode
                        )
                        == null) {
                        continue;
                     }
                  }

                  placementKind = CrystalPlacementKind.OBSIDIAN;
               }

               if (!(this.target instanceof Player)) {
                  placementKind = CrystalPlacementKind.NON_PLAYER;
               }

               CrystalPlacementResult newPlacement = new CrystalPlacementResult(
                  this.scanPosition.immutable(), candidateTargetDamage.target(), candidateTargetDamage.damage(), placementKind
               );

               bestPlacement = newPlacement.selectBetterPlacement(bestPlacement);
            }
         }
      }

      return bestPlacement;
   }
   private boolean isPlacementFaceVisible(
      BlockPos base, Direction direction
   ) {
      if ((this.placeRangeSetting.getValue()).equals(this.placeWallRangeSetting.getValue())) {
         return true;
      }

      Vec3 eyeSnapshot = this.eyePosition;
      Vec3 center = base.getCenter();
      double distance = eyeSnapshot.distanceTo(center);

      Float distanceRange = this.placeRangeSetting.getValue();
      if (distance < distanceRange.floatValue()) {
         return true;
      }

      BlockSampleMode samplingMode = BlockSampleMode.X8;
      var placementVisibilitySamples = samplingMode.sampleFace(base, direction);
      return WorldUtils.canSeeAny(placementVisibilitySamples);
   }

   private void runPlacementCycle() {
      if (!this.shouldIgnoreMiningBlock()) {
         this.placementGate.set(false);
      }

      if (!this.placeSetting.getValue()) {
         return;
      }
      if (this.isSwapPenaltyActive()) {
         return;
      }

      if (this.placementGate.get()) {
         return;
      }

      boolean crystalInOffhand = ClientAccess.zioMinecraft.player.getOffhandItem().getItem() == Items.END_CRYSTAL;
      Item crystalItem = Items.END_CRYSTAL;

      boolean holdingCrystal = ClientAccess.zioMinecraft.player.isHolding(crystalItem);

      boolean missingTarget = this.target == null;
      if (this.damageSyncSetting.getValue()
         || this.obsidianDropNearTarget
         || this.quiverThreatActive
         || missingTarget) {
         float damageSnapshot = this.selectedDamage;

         Float minimumValue = this.minPlaceDamageSetting.getValue();
         if (missingTarget || damageSnapshot < minimumValue) {
            damageSnapshot = this.selectedDamage;
            if (missingTarget || damageSnapshot < 5.0F) {
               if (!this.placeTimer.hasElapsed(this.getTickDurationMillis())) {
                  return;
               }
            }
         }
      }

      CrystalPlacementResult selectedPlacement = (CrystalPlacementResult)this.placementData.get();
      if (selectedPlacement == null) {
         this.restoreSelectedSlot();
         return;
      }

      BlockPos base = selectedPlacement.base();
      if (base != null && this.target != null) {
         LivingEntity minimumDamageTarget = this.target;

         double minimumDamage = CrystalDamagePolicy.PLACE.getMinimumDamage(minimumDamageTarget);
         Vec3 explosionPosition = Vec3.upFromBottomCenterOf(base, 1.0D);
         LivingEntity damageTargetSnapshot = this.target;
         LivingEntity boundsTargetSnapshot = this.target;
         AABB predictedBounds = this.getTargetBounds(boundsTargetSnapshot);

         var targetDamageIgnoredPositions = this.ignoredBlocks;
         double targetDamage = DamageUtils.getExplosionDamage(
            explosionPosition,
            damageTargetSnapshot,
            predictedBounds,
            6.0D,
            true,
            targetDamageIgnoredPositions,
            base
         );

         if (this.predictionSetting.getValue()) {
            if (targetDamage > 2.0D && targetDamage < minimumDamage) {
               AABB currentBounds = BoundingBoxUtils.getInterpolationBounds(this.target);
               LivingEntity currentDamageTarget = this.target;
               double currentDamage = this.calculateExplosionDamage(
                  base, currentDamageTarget, currentBounds, true
               );

               targetDamage = Math.max(targetDamage, currentDamage);
            }
         }

         if (targetDamage < minimumDamage) {
            this.restoreSelectedSlot();
            return;
         }

         if (!WorldUtils.isBedrockOrObsidian(base)) {
            if (selectedPlacement.kind()
               == CrystalPlacementKind.OBSIDIAN) {
               if (this.basePlaceSetting.getValue()) {
                  this.runForcedPlacement(base);
               }
            }

            return;
         }

         int originalSlot = -1;
         Item slotSearchItemSnapshot = Items.END_CRYSTAL;
         int crystalSlot = InventoryUtils.findHotbarSlot(slotSearchItemSnapshot);

         if (ClientAccess.zioMinecraft.isSameThread()) {
            ClientLevel fireLevelSnapshot = ClientAccess.zioMinecraft.level;
            BlockPos firePosition = base.above();
            BlockState fireState = fireLevelSnapshot.getBlockState(firePosition);
            if (fireState.getBlock() == Blocks.FIRE) {
               var fireStartPacket = new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, base.above(), Direction.DOWN);
               PacketSender.send(fireStartPacket);
               var fireStopPacket = new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, base.above(), Direction.DOWN);
               PacketSender.send(fireStopPacket);
               return;
            }
         }

         boolean alternativeSlotSearch = false;
         boolean alternativeSwap = false;
         if (this.isSilentSwap()) {
            alternativeSwap = this.silentSetting.getValue() == AutoCrystalSilentChoice.ALTERNATIVE;
         }
         boolean miningSwapReset = false;
         if (!holdingCrystal) {
            if (this.shouldIgnoreMiningBlock()) {
               Setting<Boolean> swapResetSetting = speedMine.swapResetSetting;

               if (swapResetSetting.getValue()) {
                  Setting<Boolean> alternativeMiningSetting = speedMine.alternativeSetting;

                  if (alternativeMiningSetting.getValue()) {
                     alternativeSwap = true;
                     miningSwapReset = true;
                  }
               }
            }
         }

         // Alternative inventory swaps wait for the next cycle and an empty cursor.
         if (alternativeSwap && (!this.skip || !ClientAccess.zioMinecraft.player.containerMenu.getCarried().isEmpty())) {
            this.skip = true;
            return;
         }

         if (alternativeSwap && crystalSlot == -1) {
            Item fallbackCrystalItem = Items.END_CRYSTAL;
            crystalSlot = InventoryUtils.findMenuSlot(fallbackCrystalItem);
         }

         if (this.autoSwapSetting.getValue() != ItemSwapMode.NONE && !holdingCrystal) {
            if (!this.isSwapDelayActive() && crystalSlot != -1) {
               originalSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
               if (alternativeSwap) {
                  InventoryUtils.swapIntoSelectedSlot(
                     crystalSlot
                  );
                  alternativeSlotSearch = true;

                  this.placementGate.set(miningSwapReset);
               } else {
                  if (this.autoSwapSetting.getValue() == ItemSwapMode.SILENT) {
                     speedMine.crystalAttackPending = true;
                     this.ignoreNextSlotChange();
                  }

                  if (this.shouldRestoreSlot()) {
                     this.restoreSlot = originalSlot;
                  }

                  InventoryUtils.selectHotbarSlotForPlacement(
                     crystalSlot
                  );
               }
            }
         }

         if (!holdingCrystal && originalSlot == -1) {
            return;
         }

         this.placeCrystal(base, crystalInOffhand, targetDamage);
         if (!this.isSilentSwap() && !alternativeSlotSearch) {
            return;
         }

         if (originalSlot != -1) {
            if (alternativeSlotSearch) {
               InventoryUtils.swapIntoSelectedSlot(crystalSlot);
            } else {
               if (this.autoSwapSetting.getValue() == ItemSwapMode.SILENT) {
                  this.ignoreNextSlotChange();
                  InventoryUtils.selectHotbarSlotForPlacement(
                     originalSlot
                  );
               }
            }
         }

         if (this.strictTimingSetting.getValue() || miningSwapReset || this.skip) {
            this.skip = !this.skip;
         }

         return;
      }

      this.restoreSelectedSlot();
      return;
   }
   private void sendFactorAttacks(int crystalId) {
      if (this.attackFactorSetting.isAtMinimum()) {
         return;
      }

      Iterator<Entity> entities = ClientAccess.zioMinecraft.level.entitiesForRendering().iterator();
      while (entities.hasNext()) {
         Entity entity = entities.next();
         if (entity instanceof LivingEntity && entity.isRemoved()) {
            return;
         }
         if (!entity.isAlive()) {
            return;
         }
      }
      int attackIndex = 1;
      while (true) {
         Integer factor = this.attackFactorSetting.getValue();
         if (attackIndex >= factor) {
            return;
         }
         int nextCrystalId = crystalId + attackIndex;
         int predictedCrystalId = nextCrystalId - (RotationManager.isMovementSyncEnabled() ? 1 : 0);
         PacketSender.sendAttack(predictedCrystalId);
         InteractionHand hand = InteractionHand.MAIN_HAND;
         PacketSender.sendSwing(hand);

         this.predictedAttackIds.add(predictedCrystalId);
         attackIndex++;
      }
   }
   protected boolean isSelfInvulnerable() {
      if (!this.assumeSelfInvincibilitySetting.getValue()) {
         return false;
      }

      float scaledPlayerAge = ClientAccess.zioMinecraft.player.tickCount;
      scaledPlayerAge /= 20.0F;
      return scaledPlayerAge < this.getInvulnerabilityTimeout();
   }

   private float getInvulnerabilityTimeout() {
      if (!this.assumeInvincibilitySetting.getValue()) {
         return -1.0F;
      } else {
         return this.invincibilityTimeoutSetting.getValue();
      }
   }
   private boolean isPlayerInvulnerable(LivingEntity target) {
      if (this.isSelfInvulnerable()) {
         return false;
      }

      if (!(target instanceof Player player)) {
         return false;
      }

      ClientPacketListener connectionSnapshot = ClientAccess.zioMinecraft.player.connection;

      UUID playerId = player.getGameProfile().id();
      PlayerInfo playerInfo = connectionSnapshot.getPlayerInfo(playerId);
      if (playerInfo == null) {
         return false;
      }

      float elapsedMillis = (float)(System.currentTimeMillis() - ((PlayerInfoJoinTime)playerInfo).zio$getJoinTime());
      float timeout = this.getInvulnerabilityTimeout();
      return elapsedMillis <= timeout * 1000.0F;
   }

   private boolean shouldPause() {
      if (this.isWorldUnavailable()) {
         return true;
      }

      if (BlockInteractionUtils.isInteractionCooldownActive()) {
         return true;
      }

      if (OffhandModule.isRecentSwapWindowActive()) {
         return true;
      }
      if (SpeedMineModule.isSwapPending()) {
         return true;
      }
      LivingEntity targetSnapshot = this.target;
      if (this.isInvalidTarget(targetSnapshot)) {
         return true;
      }

      if (ClientAccess.zioMinecraft.player.isSleeping()) {
         return true;
      }

      if (ClientAccess.zioMinecraft.player.isUsingItem()) {
         if (this.pauseEatingSetting.getValue()) {
            return true;
         }
      }

      if (this.pauseMendingSetting.getValue()) {
         if (autoExp.isActive()) {
            return true;
         }
      }

      boolean miningFlag = ClientAccess.zioMinecraft.gameMode.isDestroying();
      if (!miningFlag) {
         BlockPos miningPositionSnapshot = speedMine.getMiningPos();
         if (WorldUtils.canMineBlock(miningPositionSnapshot)) {
            miningFlag = speedMine.isToggled();
         }
      }

      if (miningFlag) {
         if (this.pauseMiningSetting.getValue()) {
            return true;
         }
      }

      if (!this.forceSuicideSetting.getValue()) {
         return false;
      }

      if (anchorAura.isToggled()) {
         if (anchorAura.placementData.get() != null
            && anchorAura.targetDamage
               != null) {
            if (this.placementData.get() != null) {
               double anchorDamage = anchorAura.targetDamage.damage();

               CrystalPlacementResult crystalPlacementSnapshot = (CrystalPlacementResult)this.placementData.get();
               if (anchorDamage > crystalPlacementSnapshot.damage()) {
                  return true;
               }
            }
         }
      }

      if (RotationManager.isMovementSyncEnabled()) {
         if (!RotationManager.isSilentRotationMode()) {
            if (ClientAccess.zioMinecraft.player.isFallFlying()) {
               return true;
            }
         }
      }

      return ClientAccess.zioMinecraft.player.isDeadOrDying();
   }

   public boolean shouldIgnoreMiningBlock() {
      boolean nearlyBrokenFlag = false;

      Integer thresholdTicks = this.miningIgnoreThresholdSetting.getValue();
      float predictionTicks = 2.0F + 0.5F * thresholdTicks.intValue();

      double miningProgress = speedMine.getMiningProgress();
      SpeedMineModule tickProgressSnapshot = speedMine;
      double predictedProgress = miningProgress + tickProgressSnapshot.getTickBreakProgress() * predictionTicks;
      Setting<Float> miningDamageSetting = speedMine.damageSetting;
      Float miningDamageThreshold = miningDamageSetting.getValue();
      if (predictedProgress >= miningDamageThreshold.floatValue()) {
         if (speedMine.getMiningOrNearlyBrokenPos() != null) {
            BlockPos miningPositionSnapshot = speedMine.getMiningOrNearlyBrokenPos();
            if (WorldUtils.canMineBlock(miningPositionSnapshot)) {
               nearlyBrokenFlag = true;
            }
         }
      }

      if (this.mineIgnoreSetting.getValue()) {
         if (nearlyBrokenFlag) {
            return true;
         }

         if (!speedMine.hasCrystalDelayElapsed(100L)) {
            return true;
         }
      }

      return false;
   }

   private void runBreakCycle() {
      if (!this.breakSetting.getValue()) {
         return;
      }
      ElapsedTimer timerSnapshot = this.breakTimer;

      long delayMillis = (this.breakDelaySetting.getValue()).intValue();
      if (!timerSnapshot.hasElapsed(delayMillis)) {
         return;
      }
      if (this.isSwapPenaltyActive()) {
         return;
      }
      LivingEntity invulnerabilityTargetSnapshot = this.target;
      if (this.isPlayerInvulnerable(invulnerabilityTargetSnapshot)) {
         return;
      }
      timerSnapshot = this.sequentialPlacementTimer;

      long awaitDelayMillis = ClientContext.serverStateManager.getLatencyMillis();
      if (!timerSnapshot.hasElapsed(awaitDelayMillis)) {
         if (this.instantSetting.getValue() != AutoCrystalInstantChoice.NONE) {
            if (this.isAwaitEnabled()) {
               return;
            }
         }
      }

      Class<SpeedMineModule> speedMineType = SpeedMineModule.class;

      SpeedMineModule registeredSpeedMine = (SpeedMineModule)ClientContext.moduleRegistry.getModule(
         speedMineType
      );
      Entity bestCrystal = null;
      double bestDamage = -1337.0D;
      boolean ignoreMiningBlock = this.shouldIgnoreMiningBlock();

      BlockPos cachedBlockSnapshot = speedMine.getMiningOrNearlyBrokenPos();
      if (!WorldUtils.canMineBlock(cachedBlockSnapshot)) {
         ignoreMiningBlock = false;
      }

      BlockPos miningBlock = registeredSpeedMine.getMiningOrNearlyBrokenPos();
      if (!WorldUtils.canMineBlock(miningBlock)) {
         miningBlock = null;
      }

      Iterator entities = ClientAccess.zioMinecraft.level.entitiesForRendering().iterator();
      while (entities.hasNext()) {
         Entity entity = (Entity)entities.next();
         if (!(entity instanceof EndCrystal crystal) || !this.canAttackCrystal(crystal)) {
            continue;
         }

         double targetDamage;
         if (this.skip) {
            Vec3 positionSnapshot = entity.position();
            LivingEntity directTargetSnapshot = this.target;
            LivingEntity directBoundsTargetSnapshot = this.target;
            AABB directTargetBounds = this.getTargetBounds(
               directBoundsTargetSnapshot
            );
            targetDamage = DamageUtils.getCrystalDamage(
               positionSnapshot, directTargetSnapshot, directTargetBounds
            );
         } else {
            var crystalPositionVector = entity.position();
            BlockPos crystalPosition = BlockPos.containing(crystalPositionVector);
            BlockPos crystalBase = crystalPosition.below();
            LivingEntity baseTargetSnapshot = this.target;
            LivingEntity baseBoundsTargetSnapshot = this.target;
            AABB baseTargetBounds = this.getTargetBounds(
               baseBoundsTargetSnapshot
            );
            targetDamage = this.calculateExplosionDamage(
               crystalBase, baseTargetSnapshot, baseTargetBounds, false
            );
         }

         boolean recentPlacementIntersectionFlag = false;
         if (this.lastPlacedBase != null) {
            if (!entity.blockPosition().equals(this.lastPlacedBase.above())) {
               AABB entityBounds = entity.getBoundingBox();
               AABB placementBoxSnapshot = new AABB(this.lastPlacedBase.above());
               AABB placementIntersectionBox = placementBoxSnapshot.expandTowards(0.0, 1.0D, 0.0);
               if (entityBounds.intersects(placementIntersectionBox)) {
                  timerSnapshot = this.placeTimer;
                  if (!timerSnapshot.hasElapsed(200L)) {
                     recentPlacementIntersectionFlag = true;
                  }
               }
            }
         }

         Vec3 selfPositionSnapshot = entity.position();
         LocalPlayer selfPlayerSnapshot = ClientAccess.zioMinecraft.player;

         AABB selfBounds = ClientAccess.zioMinecraft.player.getBoundingBox();
         double selfDamage = DamageUtils.getCrystalDamage(
            selfPositionSnapshot, selfPlayerSnapshot, selfBounds
         );

         CrystalLethality lethality = CrystalLethality.classifyLethality(
            this.target, targetDamage
         );
         if (recentPlacementIntersectionFlag) {
            lethality = CrystalLethality.INTERSECTION;
         }

         if (targetDamage <= selfDamage) {
            if (!this.forceSuicideSetting.getValue()
               && !lethality.lethalDamage
               && !recentPlacementIntersectionFlag) {
               continue;
            }
         }

         LivingEntity policyTarget = this.target;

         if (!CrystalDamagePolicy.HIT.shouldRejectDamage(
            policyTarget, selfDamage, targetDamage, lethality
         )) {
            if (recentPlacementIntersectionFlag && bestCrystal == null && (!ignoreMiningBlock || miningBlock == null)) {
               bestCrystal = entity;
            }

            LivingEntity minimumDamageTarget = this.target;
            CrystalDamagePolicy minimumDamagePolicy = CrystalDamagePolicy.HIT;
            if (!(targetDamage < minimumDamagePolicy.getMinimumDamage(minimumDamageTarget))
               && targetDamage > bestDamage) {
               bestDamage = targetDamage;
               bestCrystal = entity;
            }
         }
      }

      if (bestCrystal == null) {
         return;
      }

      EndCrystal selectedCrystal = (EndCrystal)bestCrystal;
      this.attackCrystal(selectedCrystal);
      return;
   }
   private void updateEyePosition() {
      if (!this.isWorldUnavailable()) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         this.eyePosition = player.getEyePosition();
      }
   }
   public boolean isFacePlaceEnabled() {
      if (this.facePlaceSetting.getValue()) {
         if (!this.auraHasAttackTarget) {
            return true;
         }

         if (!this.damageSyncSetting.getValue()) {
            return true;
         }
      }

      return false;
   }

   private boolean canRetryBasePlacement() {
      if (this.lastForcedBase != null
         && this.target != null) {
         BlockPos forcedBaseSnapshot = this.lastForcedBase;

         boolean requireUpperAir = this.requireUpperAirSetting.getValue();

         boolean lowerHitboxEnabled = this.lowerHitBoxSetting.getValue();
         if (!BlockInteractionUtils.canPlaceCrystal(forcedBaseSnapshot, requireUpperAir, true, false, true, lowerHitboxEnabled, true)) {
            return true;
         }

         BlockSampleMode samplingMode = (this.multipointSetting.getValue()) ? BlockSampleMode.X8 : BlockSampleMode.FAST;

         List samplePoints = samplingMode.sampleBlock(this.lastForcedBase);
         boolean visible = true;

         Object wallRangeValue = this.placeWallRangeSetting.getValue();
         Setting<Float> rangeCompareSetting = this.placeRangeSetting;
         if (!(Objects.equals(wallRangeValue, rangeCompareSetting.getValue()))) {
            var placementEye = this.eyePosition;
            visible = WorldUtils.canSeeAny(placementEye, samplePoints);
         }

         Vec3 eyePositionSnapshot = ClientAccess.zioMinecraft.player.getEyePosition();

         Vec3 center = this.lastForcedBase.getCenter();
         double distance = eyePositionSnapshot.distanceTo(center);

         Float range = this.placeRangeSetting.getValue();
         if (distance > range.floatValue()) {
            return true;
         }

         if (!visible) {
            Float wallRange = this.placeWallRangeSetting.getValue();
            if (distance > wallRange.floatValue()) {
               return true;
            }
         }

         Vec3 explosionPositionSnapshot = Vec3.upFromBottomCenterOf(this.lastForcedBase, 1.0D);
         LivingEntity damageTargetSnapshot = this.target;
         LivingEntity boundsTargetSnapshot = this.target;
         AABB targetBounds = this.getTargetBounds(boundsTargetSnapshot);
         double targetDamage = DamageUtils.getCrystalDamage(explosionPositionSnapshot, damageTargetSnapshot, targetBounds);
         LivingEntity minimumDamageTarget = this.target;
         CrystalDamagePolicy damagePolicy = CrystalDamagePolicy.PLACE;
         if (targetDamage >= damagePolicy.getMinimumDamage(minimumDamageTarget)) {
            return false;
         }
      }

      ElapsedTimer baseTimerSnapshot = this.basePlaceTimer;
      long tickDurationSnapshot = this.getTickDurationMillis();

      long baseDelayMillis = (this.basePlaceDelaySetting.getValue()).intValue();
      long retryDelayMillis = Math.max(tickDurationSnapshot, baseDelayMillis);
      return (baseTimerSnapshot.hasElapsed(retryDelayMillis));
   }

   private void clearInvalidTarget() {
      LivingEntity targetSnapshot = this.target;
      if (this.isInvalidTarget(targetSnapshot)) {
         this.target = null;
      }
   }
   private Direction selectPlacementDirection(BlockPos base) {
      List directions = BlockInteractionUtils.getStrictDirections(base);
      if (directions.isEmpty()) {
         Direction[] allDirections = Direction.values();
         directions = Arrays.asList(allDirections);
      }

      Stream<Direction> directionResultCandidates = directions.stream().filter((Predicate<Direction>)candidateDirection ->
         this.isPlacementFaceVisible(base, candidateDirection));
      Comparator<Direction> directionResultOrder = Comparator.comparing((Function<Direction, Double>)candidateDirection -> {
         Vec3 eyeSnapshot = this.eyePosition;
         Vec3 centerSnapshot = base.getCenter();
         double faceOffset = MathConstants.ONE_HALF;
         Direction faceDirection = candidateDirection;
         Vec3 faceCenter = centerSnapshot.relative(faceDirection, faceOffset);
         double distance = eyeSnapshot.distanceTo(faceCenter);
         return distance;
      });
      Optional<Direction> directionResult = directionResultCandidates.min(directionResultOrder);
      return directionResult.orElse(null);
   }

   private void runForcedPlacement(BlockPos base) {
      if (!ClientAccess.zioMinecraft.isSameThread()) {
         return;
      }
      if (!this.canRetryBasePlacement()) {
         return;
      }
      Item itemSnapshot = Items.END_CRYSTAL;
      int crystalSlot = InventoryUtils.findHotbarSlot(itemSnapshot);

      if (this.autoSwapSetting.getValue() == ItemSwapMode.SILENT) {
         if (this.silentSetting.getValue() == AutoCrystalSilentChoice.ALTERNATIVE) {
            Item alternativeCrystalItem = Items.END_CRYSTAL;
            crystalSlot = InventoryUtils.findMenuSlot(alternativeCrystalItem);
         }
      }

      ItemStack offhandSnapshot = ClientAccess.zioMinecraft.player.getOffhandItem();
      Item crystalItem = Items.END_CRYSTAL;
      if (!(offhandSnapshot.is(crystalItem)) && crystalSlot == -1) {
         return;
      }

      boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();

      boolean minimumWallRange = this.placeWallRangeSetting.isAtMinimum();
      Direction direction = BlockInteractionUtils.findSupportDirection(
         base, strictDirection, minimumWallRange
      );
      Vec3 hitPoint = base.getCenter();
      if (direction != null) {
         BlockSampleMode samplingMode = (this.multipointSetting.getValue()) ? BlockSampleMode.X8 : BlockSampleMode.FAST;
         Stream<Vec3> sampleResultCandidates = samplingMode.sampleFace(base, direction).stream().filter((Predicate<Vec3>)candidatePoint ->
            this.isPlacementHitVisible(candidatePoint, base, direction));
         Comparator<Vec3> sampleResultOrder = Comparator.comparing((Function<Vec3, Double>)candidatePoint -> {
            Vec3 eyeSnapshot = ClientAccess.zioMinecraft.player.getEyePosition();
            Vec3 point = candidatePoint;
            double distance = eyeSnapshot.distanceTo(point);
            return distance;
         });
         Optional<Vec3> sampleResult = sampleResultCandidates.min(sampleResultOrder);
         Vec3 visibleHit = sampleResult.orElse(null);
         if (visibleHit != null) {
            hitPoint = visibleHit;
         } else {
            hitPoint = hitPoint.relative(direction, 0.5D);
         }
      }

      int originalSlot;
      int baseSlot;
      boolean alternativeSwap = false;

      originalSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
      itemSnapshot = Items.OBSIDIAN;
      baseSlot = InventoryUtils.findHotbarSlot(itemSnapshot);
      if (this.isSilentSwap()) {
         if (this.silentSetting.getValue() == AutoCrystalSilentChoice.ALTERNATIVE) {
            alternativeSwap = true;
         }
      }

      InteractionHand hand = InteractionHand.MAIN_HAND;
      if (alternativeSwap && baseSlot == -1) {
         Item fallbackBaseItem = Items.OBSIDIAN;
         baseSlot = InventoryUtils.findMenuSlot(fallbackBaseItem);
      }

      if (baseSlot != -1) {
         if (direction == null) {
            if (!(this.basePlaceAirSetting.getValue())) {
               return;
            }
         }
         if (BlockInteractionUtils.canPlaceAt(
                  base, false
               )) {
            if (baseSlot != originalSlot) {
               if (alternativeSwap) {
                  InventoryUtils.swapIntoSelectedSlot(baseSlot);
               } else {
                  speedMine.crystalAttackPending = true;
                  InventoryUtils.selectHotbarSlotForPlacement(baseSlot);
               }
            }

            boolean interactionAirEnabled = this.basePlaceAirSetting.getValue();
            BlockInteractionUtils.placeBlockIgnoringCrystals(
               base, hitPoint, direction, interactionAirEnabled, hand
            );

            this.basePlaceTimer.reset();

            if (this.rotatePlaceSetting.getValue()) {
               if (this.rotateSetting.getValue()) {
                  this.setRotationTarget(hitPoint);
               }
            }

            this.lastForcedBase = base;
            if (originalSlot != -1 && baseSlot != originalSlot) {
               if (alternativeSwap) {
                  InventoryUtils.swapIntoSelectedSlot(baseSlot);
               } else {
                  InventoryUtils.selectHotbarSlotForPlacement(originalSlot);
               }
            }

            return;
         }
      }

      return;
   }
   private boolean isSwapDelayActive() {
      if (this.autoSwapSetting.getValue() != ItemSwapMode.NORMAL) {
         return false;
      }

      ElapsedTimer delayTimerSnapshot = this.swapDelayTimer;

      Float delay = this.swapDelaySetting.getValue();

      return !delayTimerSnapshot.hasElapsed(((long)(50.0F * delay)));
   }

   private void throwUnboundArmor() {
      for (ArmorSlot armorSlot : ArmorSlot.values()) {
         Inventory inventorySnapshot = ClientAccess.zioMinecraft.player.getInventory();

         int inventoryIndex = armorSlot.getInventoryIndex();
         ItemStack armorStack = inventorySnapshot.getItem(inventoryIndex);
         if (!armorStack.isEmpty()) {
            ResourceKey bindingCurse = Enchantments.BINDING_CURSE;
            if (!EnchantmentUtils.hasEnchantment(bindingCurse, armorStack)) {
               int slotIndex = armorSlot.getContainerSlot();
               ContainerInput clickType = ContainerInput.THROW;
               InventoryUtils.clickSlot(slotIndex, 1, clickType);
               break;
            }
         }
      }
   }
   private long getTickDurationMillis() {
      ServerStateManager serverStateSnapshot = ClientContext.serverStateManager;
      return (long)(500.0F / serverStateSnapshot.getTickRateScale());
   }

   public void ignoreNextSlotChange() {
      this.ignoredSlotChanges++;
   }

   private boolean isQuiverThreatPresent() {
      if (this.quiverThreatActive) {
         return false;
      }

      if (this.antiQuiverSetting.getValue() && this.target instanceof Player) {
         if (!this.target.isAlive()) {
            return false;
         }

         Iterator entities = ClientAccess.zioMinecraft.level.entitiesForRendering().iterator();
         while (entities.hasNext()) {
            Entity entity = (Entity)entities.next();
            if (entity instanceof Arrow) {
               double distanceScratch = entity.getY();
               LivingEntity heightTarget = this.target;
               if (!(distanceScratch < heightTarget.getEyeY())) {
                  Vec3 arrowPositionSnapshot = entity.position();

                  Vec3 targetEyeSnapshot = this.target.getEyePosition();
                  distanceScratch = Vec3Utils.horizontalDistance(arrowPositionSnapshot, targetEyeSnapshot);
                  boolean withinHorizontalRange = distanceScratch < 0.5D;

                  distanceScratch = this.target.getEyeY();
                  double heightDifference = distanceScratch - entity.getEyeY();
                  distanceScratch = Math.abs(heightDifference);
                  boolean withinVerticalRange = distanceScratch < 4.0D;
                  if (withinHorizontalRange && withinVerticalRange) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }
   private boolean shouldRestoreSlot() {
      if (this.autoSwapSetting.getValue() != ItemSwapMode.NORMAL) {
         return false;
      }

      return this.swapBackSetting.getValue();
   }

   private boolean isSwapPenaltyActive() {
      ElapsedTimer penaltyTimerSnapshot = this.swapPenaltyTimer;

      Float penalty = this.swapPenaltySetting.getValue();

      return !penaltyTimerSnapshot.hasElapsed(((long)(50.0F * penalty)));
   }

   private void updateIgnoredBlocks() {
      synchronized (this.ignoredBlocks) {
         this.ignoredBlocks.clear();

         if (this.mineIgnoreSetting.getValue()) {
            if (this.shouldIgnoreMiningBlock()) {
               SpeedMineModule miningModuleSnapshot = speedMine;
               BlockPos miningPosition = miningModuleSnapshot.getMiningPos();
               if (miningPosition != null) {
                  this.ignoredBlocks.add(miningPosition);
               }

               miningModuleSnapshot = speedMine;
               if (miningModuleSnapshot.getExtraBreakTarget() != null) {
                  Set extraTargetIgnoredBlocks = this.ignoredBlocks;

                  ExtraBreakTarget extraTargetResult = speedMine.getExtraBreakTarget();
                  extraTargetIgnoredBlocks.add(extraTargetResult.getPosition());
               }
            }

            Setting<Boolean> assumeBreakingSetting = antiCheat.assumeBlockBreakingSetting;

            if (assumeBreakingSetting.getValue()) {
               Iterator trackedBreaks = ClientContext.blockBreakTracker.getEntries().iterator();
               while (trackedBreaks.hasNext()) {
                  BlockBreakEntry trackedBreak = (BlockBreakEntry)trackedBreaks.next();
                  double breakProgress = trackedBreak.getProgress();
                  if (!(breakProgress < 0.8D)) {
                     BlockPos positionSnapshot = trackedBreak.getPosition();

                     double distanceSquared = positionSnapshot.distSqr(ClientAccess.zioMinecraft.player.blockPosition());
                     if (!(distanceSquared > 144.0D)) {
                        this.ignoredBlocks.add(trackedBreak.getPosition());
                     }
                  }
               }
            }
         }
      }
   }
   AABB getTargetBounds(LivingEntity target) {
      if (this.predictionSetting.getValue() && target instanceof Player player) {
         MovementPredictionManager predictionSnapshot = ClientContext.movementPredictionManager;

         int predictionTicks = BoundingBoxUtils.getPredictionTicks(this.predictionTicksSetting);
         return predictionSnapshot.getPredictedBoundingBox(player, predictionTicks);
      } else {
         return BoundingBoxUtils.getInterpolationBounds(target);
      }
   }
   private boolean prepareCalculation() {
      var heldItemPlayer = ClientAccess.zioMinecraft.player;
      Predicate<ItemStack> shulkerPredicate = (Predicate<ItemStack>)stack -> {
            ItemStack heldStack = stack;
            Item heldItemSnapshot = heldStack.getItem();
            return ItemUtils.isShulkerBox(heldItemSnapshot);
         };
      boolean holdingShulker = heldItemPlayer.isHolding(shulkerPredicate);
      if (!holdingShulker) {
         if (this.crystalHoldingTimer.hasElapsed(50L)) {
            return false;
         }
      }

      if (holdingShulker) {
         this.crystalHoldingTimer.reset();
      }

      if (this.calculationFuture != null) {
         if (!this.calculationFuture.isDone()) {
            this.completeCalculation();
         }
      }

      return true;
   }

   private void restoreSelectedSlot() {
      if (!this.isWorldUnavailable()) {
         if (this.shouldRestoreSlot()
            && this.restoreSlot != -1) {
            InventoryUtils.selectHotbarSlotForPlacement(this.restoreSlot);
            this.restoreSlot = -1;
            return;
         }
      }
   }
   public boolean isAwaitEnabled() {
      return this.awaitSetting.getValue();
   }

   private boolean isSilentSwap() {
      return this.autoSwapSetting.getValue() == ItemSwapMode.SILENT;
   }

   private void onCrystalSpawn(ClientboundAddEntityPacket packet) {
      if (packet.getType() != EntityType.END_CRYSTAL || this.target == null) {
         return;
      }

      Vec3 spawnPosition = new Vec3(packet.getX(), packet.getY(), packet.getZ());
      if (this.lastPlacedBase != null) {
         Vec3 placedCenter = this.lastPlacedBase.getCenter();
         double placementDistanceSquared = spawnPosition.distanceToSqr(placedCenter);
         if (placementDistanceSquared <= 9.0D) {
            long spawnTime = System.currentTimeMillis();
            var placementClient = ClientAccess.zioMinecraft;
            Runnable recordPlacement = (Runnable)() -> {
               LinkedList<Long> placementTimesSnapshot = this.placementTimes;
               Long boxedSpawnTime = spawnTime;
               placementTimesSnapshot.add(boxedSpawnTime);
            };
            placementClient.execute(recordPlacement);
         }
      }

      if (this.instantSetting.getValue() == AutoCrystalInstantChoice.NONE) {
         return;
      }

      if (!this.breakSetting.getValue()) {
         return;
      }

      LivingEntity targetSnapshot = this.target;
      if (this.isPlayerInvulnerable(targetSnapshot)) {
         return;
      }

      if (this.shouldIgnoreMiningBlock()) {
         if (this.isAwaitEnabled()) {
            return;
         }
      }

      boolean visible = true;

      Object visibleRangeValue = this.breakRangeSetting.getValue();
      Setting<Float> wallRangeProbeSetting = this.breakWallRangeSetting;
      if (!Objects.equals(visibleRangeValue, wallRangeProbeSetting.getValue())) {
         visible = WorldUtils.canSee(this.eyePosition, spawnPosition);
      }

      double distance = ClientAccess.zioMinecraft.player.getEyePosition().distanceTo(spawnPosition);
      Float selectedRange;
      if (visible) {
         selectedRange = this.breakRangeSetting.getValue();
      } else {
         selectedRange = this.breakWallRangeSetting.getValue();
      }

      if (distance > selectedRange.floatValue()) {
         return;
      }

      LivingEntity damageTarget = this.target;
      double targetDamage = DamageUtils.getCrystalDamage(
         spawnPosition, damageTarget
      );
      LocalPlayer selfPlayer = ClientAccess.zioMinecraft.player;
      double selfDamage = DamageUtils.getCrystalDamage(
         spawnPosition, selfPlayer
      );

      CrystalLethality lethality = CrystalLethality.classifyLethality(
         this.target, targetDamage
      );
      if (targetDamage <= selfDamage) {
         if (!this.forceSuicideSetting.getValue() && !lethality.lethalDamage) {
            return;
         }
      }

      LivingEntity minimumDamageTarget = this.target;
      CrystalDamagePolicy minimumDamagePolicy = CrystalDamagePolicy.HIT;
      if (targetDamage < minimumDamagePolicy.getMinimumDamage(minimumDamageTarget)) {
         return;
      }

      LivingEntity policyTarget = this.target;

      if (CrystalDamagePolicy.HIT.shouldRejectDamage(policyTarget, selfDamage, targetDamage, lethality)) {
         return;
      }

      BlockPos crystalPosition = BlockPos.containing(spawnPosition);

      if (this.instantSetting.getValue() == AutoCrystalInstantChoice.STRICT
         && this.previousPlacement.equals(crystalPosition)) {
         return;
      }

      if (this.rotateSetting.getValue()) {
         if (this.rotateBreakSetting.getValue()) {
            this.setRotationTarget(spawnPosition);
         }
      }

      CrystalInteractionManager attackManagerSnapshot = ClientContext.crystalInteractionManager;
      int packetEntityId = packet.getId();
      if (attackManagerSnapshot.attackEntity(packetEntityId, true)) {
         this.lastAttackedBase = crystalPosition;
         this.previousPlacement = crystalPosition;
         this.lastAttackedCrystalId = packet.getId();
         this.attackRetries = 0;

         this.basePlaceTimer.reset();
      }

      Item crystalItem = Items.END_CRYSTAL;

      boolean followupAllowed = ClientAccess.zioMinecraft.player.isHolding(crystalItem);
      if (!followupAllowed) {
         float health = EntityUtils.getPlayerHealthWithAbsorption();
         followupAllowed = health >= 8.0F;
      }

      if (!antiCheat.is2b2tEnabled()) {
         followupAllowed = true;
      }

      // A confirmed spawn may immediately trigger the next placement before factor attacks.
      if ((this.sequentialSetting.getValue()).isEnabled()
         && !this.spawnFollowupQueued
         && followupAllowed) {
         this.sequentialPlacementTimer.reset();
         Runnable placementCallback = this::runPlacementCycle;
         LockUtils.runWithSharedLock(placementCallback);

         if (this.sequentialSetting.getValue() == AutoCrystalSequentialChoice.STRICT) {
            this.spawnFollowupQueued = true;
         }
      }

      int factorIdSnapshot = packet.getId();

      this.sendFactorAttacks(factorIdSnapshot);
   }
   private void placeCrystal(BlockPos base, boolean offhand, double damage) {
      if (base != null) {
         Direction direction = this.selectPlacementDirection(base);
         if (direction != null) {
            if (!base.equals(this.lastAttackedBase)
               && this.lastAttackedBase != null) {
               if (!(this.sequentialSetting.getValue()).isEnabled()) {
                  return;
               }
            }

            Vec3 centerSnapshot = base.getCenter();
            double faceOffset = MathConstants.ONE_HALF;
            Vec3 hitPoint = centerSnapshot.relative(direction, faceOffset);
            // Prefer an eligible corner; equal ranges use the fixed face hit below instead.
            Stream<Vec3> cornerResultCandidates = MathUtils.faceCorners(base, direction).stream().filter((Predicate<Vec3>)candidatePoint ->
               this.isPlacementHitVisible(candidatePoint, base, direction));
            Comparator<Vec3> cornerResultOrder = Comparator.comparing((Function<Vec3, Double>)candidatePoint -> {
               Vec3 point = candidatePoint;

               double distance = this.eyePosition.distanceTo(point);
               return distance;
            });
            Optional<Vec3> cornerResult = cornerResultCandidates.min(cornerResultOrder);
            Vec3 visibleHit = cornerResult.orElse(null);
            if (visibleHit != null) {
               hitPoint = visibleHit;
            }

            Object rangeValue = this.placeRangeSetting.getValue();
            Setting<Float> wallRangeSetting = this.placeWallRangeSetting;
            if (Objects.equals(rangeValue, wallRangeSetting.getValue())) {
               if (direction.getAxis() == Axis.Y) {
                  Vec3 verticalCenterSnapshot = base.getCenter();
                  double verticalOffset = MathConstants.ONE_HALF;
                  hitPoint = verticalCenterSnapshot.relative(direction, verticalOffset);
               } else {
                  Vec3 sideCenterSnapshot = base.getCenter();
                  double sideOffset = MathConstants.ONE_HALF;
                  Vec3 sidePosition = sideCenterSnapshot.relative(direction, sideOffset);
                  hitPoint = sidePosition.add(0.0, 0.5D, 0.0);
               }
            }

            if (this.rotatePlaceSetting.getValue()) {
               if (this.rotateSetting.getValue() && !this.breakRotationRequested) {
                  this.setRotationTarget(hitPoint);
               }
            }

            InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            BlockHitResult hit = new BlockHitResult(hitPoint, direction, base, false);
            PacketSender.sendUseItemOn(hand, hit);
            SwingAction swingAction = SwingAction.INTERACT;

            ClientContext.swingManager.swing(hand, swingAction);
            this.lastPlacedBase = base;

            if (this.renderSetting.getValue()) {
               ClientContext.featureBoxRenderer.addBox(this, base);

               this.damageRenderer.showDamage(base, damage);
            }

            this.placeTimer.reset();
         }
      }
   }
   private void attackCrystal(EndCrystal crystal) {
      if (crystal.getId() == this.lastAttackedCrystalId
         && this.attackRetryTimer.hasElapsed(500L)
         && crystal.isAlive() && !crystal.isRemoved()) {
         this.attackRetries++;
      } else {
         this.attackRetries = 0;
      }

      this.lastAttackedCrystalId = crystal.getId();

      if (this.attackRetries >= 5) {
         this.attackRetryTimer.reset();
      }

      CrystalInteractionManager attackManagerSnapshot = ClientContext.crystalInteractionManager;
      int crystalId = crystal.getId();
      if (attackManagerSnapshot.attackEntity(crystalId)) {
         ((EndCrystalState)crystal).setClientAttacked(true);
         BlockPos crystalPosition = crystal.blockPosition();
         this.lastAttackedBase = crystalPosition.below();
         int attackedCrystalId = crystal.getId();

         this.sendFactorAttacks(attackedCrystalId);

         if (this.rotateBreakSetting.getValue()) {
            if (this.rotateSetting.getValue()) {
               AABB bounds = crystal.getBoundingBox();
               Vec3 rotationPointSnapshot = BoundingBoxUtils.getLowerQuarterCenter(bounds);
               this.setRotationTarget(rotationPointSnapshot);
               this.breakRotationRequested = true;
            }
         }

         this.breakTimer.reset();

         this.basePlaceTimer.reset();
      }
   }
   private boolean isPlacementHitVisible(
      Vec3 point, BlockPos base, Direction direction
   ) {
      if ((this.placeRangeSetting.getValue()).equals(this.placeWallRangeSetting.getValue())) {
         return true;
      }

      double distance = this.eyePosition.distanceTo(point);

      Float wallRange = this.placeWallRangeSetting.getValue();
      if (distance >= wallRange.floatValue()) {
         return WorldUtils.canSee(this.eyePosition, point, base, direction);
      }

      return true;
   }

   private static float compareMovementKeys(boolean positivePressed, boolean negativePressed) {
      return positivePressed == negativePressed ? 0.0F : (positivePressed ? 1 : -1);
   }

   double calculateExplosionDamage(
      BlockPos base, LivingEntity target, AABB bounds, boolean ignoreBaseBlock
   ) {
      BlockPos trackedBlock;
      boolean miningMatchFlag = false;

      trackedBlock = ClientContext.worldStateManager.getUnconfirmedBreak();
      if (trackedBlock != null) {
         if (speedMine.getMiningOrNearlyBrokenPos() != null) {
            if (trackedBlock.equals(speedMine.getMiningOrNearlyBrokenPos())) {
               miningMatchFlag = true;
            }
         }
      }

      boolean useIgnoredBlocks = ignoreBaseBlock || miningMatchFlag;
      if (trackedBlock != null) {
         SpeedMineModule ignoredBlockSnapshot = speedMine;
         useIgnoredBlocks |= trackedBlock.equals(ignoredBlockSnapshot.getMiningOrNearlyBrokenPos());
      }

      Vec3 explosionPosition = Vec3.upFromBottomCenterOf(base, 1.0D);
      Collection damageIgnoredBlocks;
      if (useIgnoredBlocks) {
         damageIgnoredBlocks = this.ignoredBlocks;
      } else {
         damageIgnoredBlocks = Collections.emptyList();
      }

      BlockPos ignoredBase;
      if (ignoreBaseBlock) {
         ignoredBase = base.immutable();
      } else {
         ignoredBase = null;
      }

      return DamageUtils.getExplosionDamage(explosionPosition, target, bounds, 6.0D, true, damageIgnoredBlocks, ignoredBase);
   }

   @EventHandler
   private void onHeldSlotChange(SelectedSlotChangeEvent event) {
      if (this.ignoredSlotChanges > 0) {
         this.ignoredSlotChanges--;
      } else {
         ElapsedTimer timerSnapshot = this.swapPenaltyTimer;
         timerSnapshot.reset();
         timerSnapshot = this.swapDelayTimer;
         timerSnapshot.reset();
      }
   }
   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      Setting<Boolean> suicideSettingSnapshot = this.forceSuicideSetting;
      Boolean disabledValue = false;
      suicideSettingSnapshot.setValue(disabledValue);
   }

   boolean isInvalidTarget(LivingEntity candidate) {
      if (candidate == null) {
         return true;
      }

      if (this.forceSuicideSetting.getValue()) {
         return candidate != ClientAccess.zioMinecraft.player;
      }

      if (candidate instanceof Player friendCandidate) {
         SocialRegistry friends = ClientContext.socialRegistry;
         if (friends.isFriendPlayer(friendCandidate)) {
            return true;
         }
      }
      if (candidate instanceof Player player) {
         SocialRegistry friends = ClientContext.socialRegistry;
         if (friends.isFriendPlayer(player) || player.isCreative()) {
            return true;
         }
      }

      if (!candidate.isAlive()) {
         return true;
      }
      if (candidate.isDeadOrDying()) {
         return true;
      }

      float distance = candidate.distanceTo(ClientAccess.zioMinecraft.player);

      Float targetRange = this.targetRangeSetting.getValue();
      if (!(distance <= targetRange)
         || ((ServerSideDeathState)candidate).isServerSideDead()
         || ClientAccess.zioMinecraft.player == candidate) {
         return true;
      }
      return candidate.isSpectator();
   }

   public boolean canAttackCrystal(EndCrystal crystal) {
      int crystalAge = crystal.tickCount;

      Integer minimumAge = this.ticksExistedSetting.getValue();
      if (crystalAge < minimumAge) {
         if (!this.shouldIgnoreMiningBlock()) {
            return false;
         }
      }

      if (!crystal.isRemoved() && this.target != null) {
         boolean visible = ClientAccess.zioMinecraft.player.hasLineOfSight(crystal);

         Vec3 capturedEye = ClientAccess.zioMinecraft.player.getEyePosition();
         Vec3 position = crystal.position();
         double distance = capturedEye.distanceTo(position);
         Float selectedRange;
         if (visible) {
            selectedRange = this.breakRangeSetting.getValue();
         } else {
            selectedRange = this.breakWallRangeSetting.getValue();
         }

         return distance <= selectedRange.floatValue();
      } else {
         return false;
      }
   }
   @EventHandler
   private void onRenderTargetPath(WorldRenderEvent.Main event) {
      LineMeshRenderer.begin(event, 1.5F);

      if (this.drawPredictionSetting.getValue()) {
         if (this.predictionSetting.getValue() && this.target instanceof Player pathTarget) {
            int predictionTick = 1;
            while (true) {
               Setting<Integer> ticksSetting = this.predictionTicksSetting;
               if (predictionTick >= BoundingBoxUtils.getPredictionTicks(ticksSetting)) {
                  Player endStartTargetSnapshot = pathTarget;

                  Vec3 endStart = BoundingBoxUtils.getBottomCenter(this.getTargetBounds(endStartTargetSnapshot));
                  Player endPointTargetSnapshot = pathTarget;

                  Vec3 endPoint = BoundingBoxUtils.getTopCenter(
                     this.getTargetBounds(endPointTargetSnapshot)
                  );
                  Setting<Color> endOutlineSetting = this.outlineSetting;
                  LineMeshRenderer.addLine(
                     endStart, endPoint, endOutlineSetting.getValue()
                  );
                  break;
               }

               int previousTick = predictionTick - 1;
               Player previousTarget = pathTarget;

               AABB previousBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(previousTarget, previousTick);
               Player nextTarget = pathTarget;

               AABB nextBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(nextTarget, predictionTick);
               Vec3 previousBottomCenter = BoundingBoxUtils.getBottomCenter(previousBounds);
               Vec3 nextBottomCenter = BoundingBoxUtils.getBottomCenter(nextBounds);
               Setting<Color> pathOutlineSetting = this.outlineSetting;
               LineMeshRenderer.addLine(
                  previousBottomCenter, nextBottomCenter, pathOutlineSetting.getValue()
               );
               predictionTick++;
            }
         }
      }

      LineMeshRenderer.draw();
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (!this.isWorldUnavailable()) {
         if (!this.prepareCalculation()) {
            // Publish a completed scan before scheduling another one with a fresh target snapshot.
            if (this.calculationFuture != null) {
               if (this.calculationFuture.isDone()) {
                  try {
                     CrystalPlacementResult calculatedPlacement = this.calculationFuture.get();
                     if (calculatedPlacement == null) {
                        this.placementData.set(null);
                        this.selectedDamage = 0.0F;
                     } else {
                        this.placementData.set(calculatedPlacement);
                        this.selectedDamage = (float)calculatedPlacement.damage();
                        this.target = calculatedPlacement.target();
                     }
                  } catch (InterruptedException | ExecutionException failure) {
                     failure.printStackTrace();
                  }
               }
            }

            if (this.calculationFuture == null || this.calculationFuture.isDone() || this.calculationFuture.isCancelled()) {
               this.clearInvalidTarget();
               List<LivingEntity> targets = this.targeting.snapshot();
               CompletableFuture<CrystalPlacementResult> scheduledCalculation = CompletableFuture.supplyAsync(
                  () -> this.calculatePlacement(targets),
                  calculationExecutor
               );
               TimeUnit timeoutUnit = TimeUnit.MILLISECONDS;
               CrystalPlacementResult timeoutResult = null;
               this.calculationFuture = scheduledCalculation.completeOnTimeout(timeoutResult, 750L, timeoutUnit).exceptionally((Function<Throwable, CrystalPlacementResult>)failure -> null);
            }

            synchronized (this.predictedAttackIds) {
               Iterator<Integer> predictedIds = this.predictedAttackIds.iterator();
               while (predictedIds.hasNext()) {
                  Integer predictedId = predictedIds.next();
                  int predictedEntityId = predictedId;
                  int entityId = predictedEntityId;

                  if (ClientAccess.zioMinecraft.level.getEntity(entityId) instanceof EndCrystal crystal) {
                     ((EndCrystalState)crystal).setClientAttacked(true);
                  }
               }

               this.predictedAttackIds.removeIf((Predicate<Integer>)predictedId -> {
                     ClientLevel levelSnapshot = ClientAccess.zioMinecraft.level;
                     int entityId = predictedId;
                     return levelSnapshot.getEntity(entityId) != null;
                  });
            }
         }
      }
   }
   private boolean isAttackRetryWindowActive(long retryMillis) {
      ElapsedTimer setbackTimerSnapshot = ClientContext.serverStateManager.getSetbackTimer();
      if (!setbackTimerSnapshot.hasElapsed(200L)) {
         return true;
      }

      ElapsedTimer retryTimerSnapshot = this.attackRetryTimer;
      float unscaledRetryMillis = (float)retryMillis;
      ServerStateManager tickScaleSnapshot = ClientContext.serverStateManager;

      return !retryTimerSnapshot.hasElapsed(((long)(unscaledRetryMillis / tickScaleSnapshot.getTickRateScale())));
   }

   @EventHandler
   private void onPacketReceived(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundAddEntityPacket spawnPacket) {
         if (!this.isSwapPenaltyActive()) {
            try {
               if (this.shouldPause()) {
                  return;
               }

               this.onCrystalSpawn(spawnPacket);
            } catch (Throwable ignoredFailure) {
            }
         }
      }

      if (event.getPacket() instanceof ClientboundEntityEventPacket entityEventPacket) {
         int eventIdScratch = entityEventPacket.getEventId();
         if (eventIdScratch == 3
            && this.target != null) {
            eventIdScratch = ((ClientboundEntityEventPacketAccessor)entityEventPacket).getId();
            LivingEntity eventTarget = this.target;
            if (eventIdScratch == eventTarget.getId()) {
               ((ServerSideDeathState)this.target).setServerSideDead(true);
            }
         }
      }

      if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket) {
         Setting<Boolean> suicideSettingSnapshot = this.forceSuicideSetting;
         Boolean disabledValue = false;
         suicideSettingSnapshot.setValue(disabledValue);
      }
   }
   @EventHandler(priority = 200)
   private void onPlayerUpdate(RotationRequestEvent event) {
      this.breakRotationRequested = false;
      this.lastAttackedBase = null;
      int lastAttackedId = this.lastAttackedCrystalId;

      Entity lastAttackedEntity = ClientAccess.zioMinecraft.level.getEntity(lastAttackedId);
      if (lastAttackedEntity == null || !lastAttackedEntity.isAlive()) {
         this.attackRetries = 0;
      }

      int playerTicks = ClientAccess.zioMinecraft.player.tickCount;
      if (playerTicks % 4 == 0) {
         long now = System.currentTimeMillis();
         while (true) {
            if (this.placementTimes.isEmpty()) {
               break;
            }

            LinkedList<?> historyHeadCheck = this.placementTimes;
            if (historyHeadCheck.getFirst() == null) {
               break;
            }

            Long oldestPlacement = this.placementTimes.getFirst();
            long placementAgeMillis = now - oldestPlacement;
            if (placementAgeMillis <= 1000L) {
               break;
            }

            this.placementTimes.remove();
         }
      }

      Class<KillEffectsModule> killEffectsType = KillEffectsModule.class;

      KillEffectsModule killEffectsSnapshot = (KillEffectsModule)ClientContext.moduleRegistry.getModule(
         killEffectsType
      );
      if (killEffectsSnapshot != null) {
         if (killEffectsSnapshot.isToggled()) {
            LivingEntity effectTargetSnapshot = this.target;
            if (!(this.isInvalidTarget(effectTargetSnapshot))) {
               if (this.target instanceof Player player) {
                  killEffectsSnapshot.trackTarget(player);
               }
            }
         }
      }

      if (!(this.shouldPause())) {
         // Breaking first can update the attacked base used by the placement cycle.
         this.runBreakCycle();
         ElapsedTimer placeTimerSnapshot = this.placeTimer;

         long placeDelayMillis = (this.placeDelaySetting.getValue()).intValue();
         if (placeTimerSnapshot.hasElapsed(placeDelayMillis)) {
            Runnable placementCallback = this::runPlacementCycle;
            LockUtils.runWithSharedLock(placementCallback);
         }

         Vec3 rotationPoint = (Vec3)this.rotationTarget.get();

         if ((this.rotateSetting.getValue()) && rotationPoint != null) {
            Vec2 movementInput = new Vec2(
               readForwardMovementInput(),
               compareMovementKeys(ClientAccess.zioMinecraft.options.keyLeft.isDown(), ClientAccess.zioMinecraft.options.keyRight.isDown())
            );
            boolean retryWindowActive = this.isAttackRetryWindowActive(500L);
            boolean rotationFollowup = !(RotationManager.isSilentRotationMode()
               && !retryWindowActive && !(movementInput.lengthSquared() < 0.01D));

            if (PlayerStateUtils.isFallFlying()) {
               rotationFollowup = false;
            }

            float[] rotations = RotationManager.getRotationsToPosition(rotationPoint);

            ClientContext.rotationManager.requestRotation(rotations, 100, rotationFollowup);
            if ((
                     RotationManager.isSilentRotationMode()
                  )
               && rotationFollowup
               && !retryWindowActive) {
               ClientContext.rotationManager.requestRotation(rotations, 101, false);
            }

            if (this.rotationTimer.hasElapsed(400L)) {
               this.placementData.lazySet(null);

               this.rotationTarget.lazySet(null);
            }
         }
      }
   }
   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (event.getStage() != EventStage.Post) {
         this.targeting.refresh();
         this.updateEyePosition();
         this.updateIgnoredBlocks();
         AuraModule auraSnapshot = aura;
         this.auraHasAttackTarget = auraSnapshot.isTargetWithinAttackRange();
         this.spawnFollowupQueued = false;
         this.quiverThreatActive = this.isQuiverThreatPresent();

         if (this.forceSuicideSetting.getValue()) {
            if (this.forceSuicideNoArmorSetting.getValue()) {
               this.throwUnboundArmor();
            }
         }

         if (this.obsidianDropNearTarget) {
            this.obsidianDropNearTarget = false;
         } else {
            this.obsidianDropNearTarget = false;
            LivingEntity proximityTarget = this.targeting.findProximityTarget();
            if (proximityTarget == null) {
               return;
            }

            if (this.targeting.hasObsidianDropNear(proximityTarget)) {
               this.obsidianDropNearTarget = true;
            }
         }
      }
   }
   public void completeCalculation() {
      CompletableFuture<CrystalPlacementResult> pending = this.calculationFuture;
      if (pending != null && !pending.isDone() && !pending.isCancelled()) {
         pending.complete(null);
      }
   }
   public static void bindReferencedModules(ModuleRegistry modules) {
      speedMine = (SpeedMineModule)modules.getModule(
         SpeedMineModule.class
      );
      antiCheat = (AntiCheatModule)modules.getModule(
         AntiCheatModule.class
      );
      anchorAura = (AnchorAuraModule)modules.getModule(
         AnchorAuraModule.class
      );
      aura = (AuraModule)modules.getModule(
         AuraModule.class
      );
      autoExp = (AutoExpModule)modules.getModule(
         AutoExpModule.class
      );
   }

   public AutoCrystalModule() {
      super("AutoCrystal", "Kills people with end crystals (if you're good).", ModuleCategory.COMBAT, "crystalaura", "ca", "ac");
      this.placeSetting = this.add(new BooleanSetting("Place", true).markVisibilityDependency());
      this.placeDelaySetting = this.add(new NumberSetting<>("Delay", 0, 0, 1000).withUnit("ms").dependsOn(this.placeSetting));
      this.placeRangeSetting = this.add(new NumberSetting<>("Range", 6.0F, 0.0F, 6.0F).withUnit("m").dependsOn(this.placeSetting));
      this.placeWallRangeSetting = this.add(new NumberSetting<>("WallRange", 6.0F, 0.0F, 6.0F).withUnit("m").dependsOn(this.placeSetting));
      this.minPlaceDamageSetting = this.add(new NumberSetting<>("MinDamage", 6.0F, 0.1F, 20.1F).withUnit("hp").setBoundaryLabel("Lethal", SettingLabelCondition.MAX).dependsOn(this.placeSetting));
      this.maxPlaceSelfDamageSetting = this.add(new NumberSetting<>("MaxSelfDamage", 12.0F, 0.0F, 20.0F).withUnit("hp").dependsOn(this.placeSetting));
      this.placeDamageRatioSetting = this.add(new NumberSetting<>("DamageRatio", 1.0F, 0.5F, 2.0F).withUnit("x").dependsOn(this.placeSetting));
      this.sequentialSetting = this.add(new EnumSetting<>("Sequential", AutoCrystalSequentialChoice.STRICT).dependsOn(this.placeSetting));
      this.requireUpperAirSetting = this.add(new BooleanSetting("1.12", false).dependsOn(this.placeSetting));
      this.strictDirectionSetting = this.add(new BooleanSetting("StrictDirection", false).dependsOn(this.placeSetting));
      this.lowerHitBoxSetting = this.add(new BooleanSetting("LowerHitBox", false).dependsOn(this.placeSetting));
      this.mineIgnoreSetting = this.add(new BooleanSetting("MineIgnore", true).dependsOn(this.placeSetting));
      this.alwaysIgnoreMiningSetting = this.add(new BooleanSetting("Always", false).dependsOn(this.placeSetting, this.mineIgnoreSetting));
      this.miningIgnoreThresholdSetting = this.add(new NumberSetting<>("Threshold", 1, 1, 5).dependsOn(this.placeSetting, this.mineIgnoreSetting));
      this.breakSetting = this.add(new BooleanSetting("Break", true).markVisibilityDependency());
      this.breakDelaySetting = this.add(new NumberSetting<>("Delay", 0, 0, 1000).withUnit("ms").dependsOn(this.breakSetting));
      this.attackFactorSetting = this.add(new NumberSetting<>("Factor", 1, 1, 5).dependsOn(this.breakSetting));
      this.breakRangeSetting = this.add(new NumberSetting<>("Range", 6.0F, 0.0F, 6.0F).withUnit("m").dependsOn(this.breakSetting));
      this.breakWallRangeSetting = this.add(new NumberSetting<>("WallRange", 6.0F, 0.0F, 6.0F).withUnit("m").dependsOn(this.breakSetting));
      this.minBreakDamageSetting = this.add(new NumberSetting<>("MinDamage", 3.0F, 0.1F, 20.0F).withUnit("hp").dependsOn(this.breakSetting));
      this.maxBreakSelfDamageSetting = this.add(new NumberSetting<>("MaxSelfDamage", 8.0F, 0.0F, 20.0F).withUnit("hp").dependsOn(this.breakSetting));
      this.antiSuicideSetting = this.add(new BooleanSetting("AntiSuicide", true).dependsOn(this.breakSetting));
      this.instantSetting = this.add(new EnumSetting<>("Instant", AutoCrystalInstantChoice.ALWAYS).dependsOn(this.breakSetting));
      this.awaitSetting = this.add(new BooleanSetting("Await", true, unused -> this.instantSetting.getValue() != AutoCrystalInstantChoice.NONE).dependsOn(this.breakSetting));
      this.ticksExistedSetting = this.add(new NumberSetting<>("TicksExisted", 2, 0, 40).dependsOn(this.breakSetting));
      this.weaknessSetting = this.add(new EnumSetting<>("Weakness", AutoCrystalWeaknessChoice.NONE).dependsOn(this.breakSetting));
      this.basePlaceSetting = this.add(new BooleanSetting("BasePlace", false).markVisibilityDependency());
      this.basePlaceDelaySetting = this.add(new NumberSetting<>("Delay", 500, 0, 1000).withUnit("ms").dependsOn(this.basePlaceSetting));
      this.basePlaceAirSetting = this.add(new BooleanSetting("Air", false).dependsOn(this.basePlaceSetting));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", false).markVisibilityDependency());
      this.rotatePlaceSetting = this.add(new BooleanSetting("Place", true).dependsOn(this.rotateSetting));
      this.rotateBreakSetting = this.add(new BooleanSetting("Break", true).dependsOn(this.rotateSetting));
      this.facePlaceSetting = this.add(new BooleanSetting("FacePlace", false).markVisibilityDependency());
      this.facePlaceHealthSetting = this.add(new NumberSetting<>("Health", 4.0F, 0.0F, 36.0F).withUnit("hp").dependsOn(this.facePlaceSetting));
      this.facePlaceArmorThresholdSetting = this.add(new NumberSetting<>("ArmorThreshold", 10, 0, 100).withUnit("%").dependsOn(this.facePlaceSetting));
      this.facePlaceShieldBreakerSetting = this.add(new BooleanSetting("ShieldBreaker", false).dependsOn(this.facePlaceSetting));
      this.damageSyncSetting = this.add(new BooleanSetting("DamageSync", false).dependsOn(this.facePlaceSetting));
      this.predictionSetting = this.add(new BooleanSetting("Extrapolation", true).markVisibilityDependency());
      this.predictionTicksSetting = this.add(new NumberSetting<>("Ticks", 4, 1, 8).dependsOn(this.predictionSetting));
      this.drawPredictionSetting = this.add(new BooleanSetting("Draw", false).dependsOn(this.predictionSetting));
      this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(1136499199, true)).dependsOn(this.renderSetting));
      this.outlineSetting = this.add(new ColorSetting("Outline", new Color(-910321153, true)).dependsOn(this.renderSetting));
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F).dependsOn(this.renderSetting));
      this.renderDamageSetting = this.add(new BooleanSetting("Damage", false).dependsOn(this.renderSetting));
      this.fadeSetting = this.add(new BooleanSetting("Fade", true).markVisibilityDependency().dependsOn(this.renderSetting));
      this.multiFadeSetting = this.add(new BooleanSetting("MultiFade", false).dependsOn(this.fadeSetting, this.renderSetting));
      this.fadeTimeSetting = this.add(new NumberSetting<>("FadeTime", 1.0F, 0.1F, 1.5F).withUnit("s").dependsOn(this.fadeSetting, this.renderSetting));
      this.pauseSetting = this.add(new BooleanSetting("Pause", false).markAsGroup());
      this.pauseMiningSetting = this.add(new BooleanSetting("Mining", false).dependsOn(this.pauseSetting));
      this.pauseEatingSetting = this.add(new BooleanSetting("Eating", false).dependsOn(this.pauseSetting));
      this.pauseMendingSetting = this.add(new BooleanSetting("Mending", false).dependsOn(this.pauseSetting));
      this.targetingSetting = this.add(new BooleanSetting("Targeting", false).markAsGroup());
      this.targetRangeSetting = this.add(new NumberSetting<>("TargetRange", 12.0F, 1.0F, 16.0F).withUnit("m").dependsOn(this.targetingSetting));
      this.crystalRangeSetting = this.add(new NumberSetting<>("CrystalRange", 8.0F, 0.1F, 12.0F).withUnit("m").dependsOn(this.targetingSetting));
      this.antiFriendKillSetting = this.add(new BooleanSetting("AntiFriendKill", false).dependsOn(this.targetingSetting));
      this.antiQuiverSetting = this.add(new BooleanSetting("AntiQuiver", false).dependsOn(this.targetingSetting));
      this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false).dependsOn(this.targetingSetting));
      this.assumeBestArmorSetting = this.add(new BooleanSetting("AssumeBestArmor", false).dependsOn(this.targetingSetting));
      this.bossesSetting = this.add(new BooleanSetting("Bosses", false).dependsOn(this.targetingSetting));
      this.forceSuicideSetting = this.add(new BooleanSetting("ForceSuicide", false).markVisibilityDependency().dependsOn(this.targetingSetting));
      this.forceSuicideNoArmorSetting = this.add(new BooleanSetting("NoArmor", false).dependsOn(this.targetingSetting, this.forceSuicideSetting));
      this.assumeInvincibilitySetting = this.add(new BooleanSetting("AssumeInvincibility", false).markVisibilityDependency().dependsOn(this.targetingSetting));
      this.assumeSelfInvincibilitySetting = this.add(new BooleanSetting("Self", true).dependsOn(this.assumeInvincibilitySetting, this.targetingSetting));
      this.invincibilityTimeoutSetting = this.add(new NumberSetting<>("Timeout", 0.0F, 0.0F, 4.0F).withUnit("s").dependsOn(this.assumeInvincibilitySetting, this.targetingSetting));
      this.miscSetting = this.add(new BooleanSetting("Misc", false).markAsGroup());
      this.autoSwapSetting = this.add(new EnumSetting<>("AutoSwap", ItemSwapMode.NONE).dependsOn(this.miscSetting));
      this.silentSetting = this.add(new EnumSetting<>("Silent", AutoCrystalSilentChoice.NORMAL, unused -> this.autoSwapSetting.getValue() == ItemSwapMode.SILENT).dependsOn(this.miscSetting));
      this.swapBackSetting = this.add(new BooleanSetting("SwapBack", true, unused -> this.autoSwapSetting.getValue() == ItemSwapMode.NORMAL).dependsOn(this.miscSetting));
      this.strictTimingSetting = this.add(new BooleanSetting("StrictTiming", false, unused -> this.autoSwapSetting.getValue() == ItemSwapMode.SILENT && this.silentSetting.getValue() == AutoCrystalSilentChoice.ALTERNATIVE).dependsOn(this.miscSetting));
      this.swapDelaySetting = this.add(new NumberSetting<>("SwapDelay", 0.0F, 0.0F, 10.0F, unused -> this.autoSwapSetting.getValue() == ItemSwapMode.NORMAL).dependsOn(this.miscSetting));
      this.swapPenaltySetting = this.add(new NumberSetting<>("SwapPenalty", 0.0F, 0.0F, 10.0F).dependsOn(this.miscSetting));
      this.multipointSetting = this.add(new BooleanSetting("Multipoint", false).dependsOn(this.miscSetting));
      this.targeting = new AutoCrystalTargeting(this);
      this.damageRenderer = new CrystalDamageRenderer(this);
      this.crystalHoldingTimer = new ElapsedTimer();
      this.placementData = new AtomicReference();
      this.rotationTarget = new AtomicReference();
      this.placementGate = new AtomicBoolean();
      this.rotationTimer = new ElapsedTimer();
      this.placeTimer = new ElapsedTimer();
      this.breakTimer = new ElapsedTimer();
      this.swapDelayTimer = new ElapsedTimer();
      this.swapPenaltyTimer = new ElapsedTimer();
      this.sequentialPlacementTimer = new ElapsedTimer();
      this.attackRetryTimer = new ElapsedTimer();
      this.basePlaceTimer = new ElapsedTimer();
      this.placementTimes = new LinkedList<>();
      this.predictedAttackIds = Collections.synchronizedList(new ArrayList<>());
      this.ignoredBlocks = new HashSet();
      this.scanPosition = new MutableBlockPos();
      this.lastAttackedCrystalId = -1;
      FeatureBoxRenderer boxManagerSnapshot = ClientContext.featureBoxRenderer;
      FeatureBoxRenderer.RenderConfiguration boxSettings = new FeatureBoxRenderer.RenderConfiguration(
         this, this.fillSetting, this.outlineSetting, this.lineWidthSetting, this.fadeTimeSetting,
         this.multiFadeSetting, this.fadeSetting, 1000
      );
      boxManagerSnapshot.register(boxSettings);
      CrystalDamageRenderer damageListener = this.damageRenderer;
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(damageListener);
      this.placeDelaySetting.setConfigName("PlaceDelay");
      this.placeRangeSetting.setConfigName("PlaceRange");
      this.placeWallRangeSetting.setConfigName("PlaceWallRange");
      this.minPlaceDamageSetting.setConfigName("MinPlaceDamage");
      this.maxPlaceSelfDamageSetting.setConfigName("MaxSelfPlace");
      this.breakDelaySetting.setConfigName("HitDelay");
      this.breakRangeSetting.setConfigName("BreakRange");
      this.breakWallRangeSetting.setConfigName("BreakWallRange");
      this.minBreakDamageSetting.setConfigName("MinBreakDamage");
      this.maxBreakSelfDamageSetting.setConfigName("MaxSelfHit");
      this.rotatePlaceSetting.setConfigName("PlaceRotate");
      this.rotateBreakSetting.setConfigName("BreakRotate");
      this.facePlaceHealthSetting.setConfigName("FacePlaceHealth");
      this.facePlaceArmorThresholdSetting.setConfigName("FacePlaceArmor");
      this.predictionSetting.setConfigName("ExtraTicks");
      this.invincibilityTimeoutSetting.setConfigName("InvincTimeout");
      this.basePlaceDelaySetting.setConfigName("BaseDelay");
      Setting<Integer> minimumAgeSetting = this.ticksExistedSetting;
      Predicate minimumAgePredicate = unused -> {
         return this.instantSetting.getValue() != AutoCrystalInstantChoice.ALWAYS;
      };
      minimumAgeSetting.addVisibilityPredicate(minimumAgePredicate);
   }

   private static float readForwardMovementInput() {
      KeyMapping forwardKey = ClientAccess.zioMinecraft.options.keyUp;
      boolean forward = forwardKey.isDown();
      KeyMapping backKey = ClientAccess.zioMinecraft.options.keyDown;
      return compareMovementKeys(forward, backKey.isDown());
   }
}
