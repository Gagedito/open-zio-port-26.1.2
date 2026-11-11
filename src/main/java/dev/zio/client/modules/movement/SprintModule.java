/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;


import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.CancellableEvent;
import dev.zio.client.events.ClientPacketListenerHandleExplosionEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerAiStepEvent;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.zio.client.events.PlayerAttackEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.movement.WallsVelocityHandler;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.access.LocalPlayerTickAccess;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.utils.combat.RotationRequest;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.AntiCheatRotationsChoice;
import dev.zio.mixin.EntityAccessor;
import dev.zio.mixin.LocalPlayerAccessor;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class SprintModule extends Module {
   public static boolean forceSprintKeyDown;

   private static ElytraFlyModule elytraFlyModule;
   private static LongJumpModule longJumpModule;
   private static FastFallModule fastFallModule;
   private static SpeedModule speedModule;
   private static FastWebModule fastWebModule;
   private static AntiCheatModule antiCheatModule;
   private static NoSlowModule noSlowModule;
   private static FlightModule flightModule;
   final Setting<ModeChoice> modeSetting;
   private final Setting<Boolean> stopInLiquidsSetting;
   private final Setting<Boolean> inBlocksSetting;
   public final Setting<Boolean> directionSpoofSetting;
   public final Setting<Boolean> keepSetting;
   private final Setting<Boolean> vanillaSlowdownSetting;
   private final Setting<Boolean> adjustSetting;
   private final Setting<Float> factorSetting;
   final Setting<Boolean> boostSetting;
   private final Setting<Float> boostSpeedSetting;
   private final ElapsedTimer explosionBoostTimer;
   private Vec2 previousMoveVector;
   private float explosionBoostSpeed;
   private boolean wasSprintingBeforeAttack;
   private boolean fallFlyingSinceGround;

   @Override
   public String getInfo() {
      return FontRenderer.capitalize(this.modeSetting.getValue());
   }

   private void updateDirectionSpoof(CancellableEvent event) {
      if (!this.directionSpoofSetting.getValue() || ClientContext.baritoneBridge.isPathingOrCalculating()) {
         return;
      }
      if (this.isWorldUnavailable()) {
         return;
      }

      if (this.isSprintSuppressed()) {
         return;
      }
      if (this.shouldDeferToVanillaSprinting()) {
         return;
      }

      if (!MovementPhysics.isLocalPlayerMoving()) {
         return;
      }
      if (PlayerStateUtils.isFallFlying()) {
         return;
      }
      if (ClientAccess.zioMinecraft.player.input.hasForwardImpulse()) {
         return;
      }
      if (flightModule.isToggled()) {
         return;
      }

      if (event instanceof LocalPlayerSendPositionEvent positionEvent) {
         float yaw = MovementPhysics.getMovementYaw();
         positionEvent.setYaw(yaw);
      } else {
         RotationManager rotationManager = ClientContext.rotationManager;
         float[] rotations = new float[2];
         rotations[0] = MovementPhysics.getMovementYaw();
         rotations[1] = ClientAccess.zioMinecraft.player.getXRot();
         rotationManager.requestRotation(rotations, (byte)0, true);

         ClientContext.rotationManager.useRequestedYawForPriority((byte)0);
      }
   }

   @EventHandler(priority = 100)
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (this.modeSetting.getValue() == SprintModule.ModeChoice.INSTANT) {
         this.updateDirectionSpoof(event);
      }
   }

   @EventHandler
   private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
      if (ClientContext.baritoneBridge.isPathingOrCalculating()) {
         return;
      }
      if (!antiCheatModule.movementSyncSetting.getValue()) {
         return;
      }
      if (antiCheatModule.rotationsSetting.getValue() != AntiCheatRotationsChoice.SILENT) {
         return;
      }

      float rotationDifference = this.getRotationDifference();
      boolean hasLargeRotation = false;
      if (rotationDifference >= 5.0F) {
         if (this.modeSetting.getValue() != SprintModule.ModeChoice.LEGIT) {
            hasLargeRotation = true;
         }
      }

      if (this.shouldDeferToVanillaSprinting() && !hasLargeRotation) {
         return;
      }

      RotationRequest request = ClientContext.rotationManager.getRequest();
      boolean hasForwardMovement = ClientAccess.zioMinecraft.player.input.hasForwardImpulse() || hasLargeRotation;
      boolean hasHigherPriority = request != null && request.priority() > 0;
      if (!this.directionSpoofSetting.getValue() || hasHigherPriority || !hasForwardMovement) {
         return;
      }

      ClientLevel level = ClientAccess.zioMinecraft.level;
      BlockPos hitPosition = event.getHitResult().getBlockPos();
      if (level.getBlockEntity(hitPosition) == null) {
         InteractionHand hand = event.getHand();
         if (this.hasFoodComponentInHand(hand)) {
            MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
            LocalPlayer player = ClientAccess.zioMinecraft.player;
            InteractionHand useHand = event.getHand();
            gameMode.useItem(player, useHand);
            event.cancel();
            return;
         }
      }

      LocalPlayerTickAccess serverRotations = (LocalPlayerTickAccess)ClientAccess.zioMinecraft.player;
      double x = ClientAccess.zioMinecraft.player.getX();
      double y = ClientAccess.zioMinecraft.player.getY();
      double z = ClientAccess.zioMinecraft.player.getZ();
      float yaw = ClientAccess.zioMinecraft.player.getYRot();
      float pitch = ClientAccess.zioMinecraft.player.getXRot();
      boolean onGround = ClientAccess.zioMinecraft.player.onGround();
      PacketSender.sendPositionRotation(x, y, z, yaw, pitch, onGround);
      float serverYaw = ClientAccess.zioMinecraft.player.getYRot();
      serverRotations.zio$updateServer(serverYaw, ClientAccess.zioMinecraft.player.getXRot());
   }

   @EventHandler
   private void onClientPacketListenerHandleExplosionEvent(ClientPacketListenerHandleExplosionEvent event) {
      if (ClientContext.baritoneBridge.isPathingOrCalculating()) {
         return;
      }
      Vec3 explosionPosition = new Vec3(event.getCenterX(), event.getCenterY(), event.getCenterZ());
      if (!MovementPhysics.isLocalPlayerMoving()) {
         return;
      }
      if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(750L)) {
         return;
      }
      if (!this.boostSetting.getValue()) {
         return;
      }
      double squaredDistance = ClientAccess.zioMinecraft.player.distanceToSqr(explosionPosition);
      double distance = Math.sqrt(squaredDistance);
      if (distance > 6.0D) {
         return;
      }
      this.explosionBoostTimer.reset();
      double impulseX = event.getVelocityX();
      double impulseZ = event.getVelocityZ();
      float horizontalImpulse = (float)Math.hypot(impulseX, impulseZ);
      Float boostMultiplier = this.boostSpeedSetting.getValue();
      this.explosionBoostSpeed = horizontalImpulse * boostMultiplier;
   }

   @EventHandler(priority = 1000)
   private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
      if (PlayerStateUtils.isFallFlying()) {
         this.fallFlyingSinceGround = true;
      } else {
         if (ClientAccess.zioMinecraft.player.onGround()) {
            this.fallFlyingSinceGround = false;
         }
      }
      if (ClientContext.baritoneBridge.isPathingOrCalculating() || this.isWorldUnavailable()) {
         return;
      }
      if (this.isSprintSuppressed()) {
         return;
      }
      if (this.modeSetting.getValue() != SprintModule.ModeChoice.INSTANT) {
         return;
      }
      if (PlayerStateUtils.isHungerLimited()) {
         return;
      }
      if (PlayerStateUtils.isFallFlying()) {
         return;
      }
      if (ClientAccess.zioMinecraft.player.getAbilities().flying) {
         return;
      }
      if (ClientAccess.zioMinecraft.player.isInWater()) {
         return;
      }
      if (ClientAccess.zioMinecraft.player.isInLava()) {
         return;
      }
      if (ClientAccess.zioMinecraft.player.isShiftKeyDown()) {
         return;
      }
      if (ClientAccess.zioMinecraft.player.isVisuallySwimming() || fastFallModule.suppressSprint) {
         return;
      }
      if (longJumpModule.isToggled()) {
         return;
      }
      if (speedModule.isToggled()) {
         return;
      }
      if (!ClientAccess.zioMinecraft.player.onGround() && this.fallFlyingSinceGround) {
         return;
      }

      Boolean slowdownEnabled = this.vanillaSlowdownSetting.getValue();
      double movementSpeed = MovementPhysics.getBaseMoveSpeed(slowdownEnabled);
      if (this.adjustSetting.getValue()) {
         Float multiplier = this.factorSetting.getValue();
         movementSpeed *= multiplier.floatValue();
      }

      if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
         double currentX = event.getX();
         double currentZ = event.getZ();
         double currentSpeed = Math.hypot(currentX, currentZ);
         movementSpeed = Math.max(movementSpeed, currentSpeed);
      }
      if (this.vanillaSlowdownSetting.getValue()) {
         ClientLevel level = ClientAccess.zioMinecraft.level;
         BlockPos velocityPosition = ((EntityAccessor)ClientAccess.zioMinecraft.player).zio$getVelocityAffectingPos();
         Block velocityBlock = level.getBlockState(velocityPosition).getBlock();
         if (velocityBlock == Blocks.SLIME_BLOCK) {
            if (!noSlowModule.isSlimeSlowdownDisabled()) {
               movementSpeed *= 0.7D;
            }
         }
         movementSpeed *= velocityBlock.getSpeedFactor();
      }
      if (this.boostSetting.getValue()) {
         if (!this.explosionBoostTimer.hasElapsed(500L)) {
            movementSpeed += this.explosionBoostSpeed;
         }
      }
      MovementPhysics.applyHorizontalMotion(event, movementSpeed);
   }

   @EventHandler
   private void onLocalPlayerAiStepEvent(LocalPlayerAiStepEvent event) {
      if (ClientContext.baritoneBridge.isPathingOrCalculating() || this.isWorldUnavailable()) {
         return;
      }
      if (this.isSprintSuppressed()) {
         return;
      }
      if (this.shouldDeferToVanillaSprinting()) {
         return;
      }
      if (RotationManager.isMovementSyncEnabled()) {
         if (this.directionSpoofSetting.getValue()) {
            if (this.consumeMoveVectorChange() || ClientAccess.zioMinecraft.player.zza <= 0.0F) {
               this.setPlayerSprinting(false);
               return;
            }
         }
      }
      boolean sprinting = this.canStartSprinting();
      this.setPlayerSprinting(sprinting);
   }

   @EventHandler
   private void onPlayerAttackEvent(PlayerAttackEvent event) {
      if (!ClientContext.baritoneBridge.isPathingOrCalculating()) {
         if (this.keepSetting.getValue()) {
            if (event.getStage() == EventStage.Pre) {
               LocalPlayer sprintPlayer = ClientAccess.zioMinecraft.player;
               this.wasSprintingBeforeAttack = sprintPlayer.isSprinting();
               if (this.wasSprintingBeforeAttack) {
                  LocalPlayer packetPlayer = ClientAccess.zioMinecraft.player;
                  PacketSender.sendPlayerCommand(packetPlayer, Action.STOP_SPRINTING);
               }
            } else if (this.wasSprintingBeforeAttack) {
               LocalPlayer sprintPlayer = ClientAccess.zioMinecraft.player;
               sprintPlayer.setSprinting(true);
               LocalPlayer packetPlayer = ClientAccess.zioMinecraft.player;
               PacketSender.sendPlayerCommand(packetPlayer, Action.START_SPRINTING);
            }
         }
      }
   }

   private boolean hasWaterBelowWhileAirborne() {
      if (ClientAccess.zioMinecraft.player.onGround()) {
         return false;
      }

      ClientLevel eyeLevel = ClientAccess.zioMinecraft.level;
      Vec3 eyePosition = ClientAccess.zioMinecraft.player.getEyePosition();
      BlockPos eyeBlockPosition = BlockPos.containing(eyePosition);
      if (eyeLevel.isWaterAt(eyeBlockPosition)) {
         return false;
      }

      AABB expandedBounds = ClientAccess.zioMinecraft.player.getBoundingBox().expandTowards(0.0, -0.1D, 0.0);
      Stream<BlockPos> positions = BlockPos.betweenClosedStream(expandedBounds);
      Predicate<BlockPos> isWater = position -> ClientAccess.zioMinecraft.level.isWaterAt(position);
      return positions.anyMatch(isWater);
   }

   private boolean isSprintSuppressed() {
      if (ClientContext.inputOverrides.isSprintBlocked()) {
         return true;
      }
      if (noSlowModule.isUsingItemInConstantiamMode()) {
         return true;
      }
      if (ClientAccess.zioMinecraft.player.isShiftKeyDown()) {
         return true;
      }
      if (ClientContext.baritoneBridge.isAvailable()
         && ClientContext.baritoneBridge.isPathingOrCalculating()) {
         return true;
      }
      if (PlayerStateUtils.isInLiquid(ClientAccess.zioMinecraft.player)) {
         if (this.stopInLiquidsSetting.getValue()) {
            return true;
         }
      }
      if (elytraFlyModule.isToggled()) {
         if (elytraFlyModule.modeSetting.getValue() == ElytraFlyModule.ModeChoice.BOUNCE) {
            if (elytraFlyModule.isElytraEquipped()) {
               return true;
            }
         }
      }
      return false;
   }

   public boolean shouldDeferToVanillaSprinting() {
      if (ClientContext.inputOverrides.isSprintBlocked()) {
         return false;
      }
      if (antiCheatModule.is2b2tEnabled()
         && ClientAccess.zioMinecraft.player != null
         && ClientAccess.zioMinecraft.player.containerMenu != ClientAccess.zioMinecraft.player.inventoryMenu) {
         return true;
      }
      if (this.isSprintInBlocksRestricted()) {
         if (WallsVelocityHandler.hasUsableGroundCollision()) {
            return true;
         }
      }
      if (this.fallFlyingSinceGround) {
         return true;
      }
      RotationManager rotationManager = ClientContext.rotationManager;
      boolean hasUnreadyRotation = false;
      if (rotationManager.getRequest() != null) {
         if (!ClientContext.rotationManager.isRequestedYawUsedForMovement()) {
            hasUnreadyRotation = true;
         }
      }
      if (this.directionSpoofSetting.getValue()) {
         Setting<ModeChoice> mode = this.modeSetting;
         if (mode.getValue() == SprintModule.ModeChoice.RAGE && hasUnreadyRotation) {
            return true;
         }
      }
      Setting<ModeChoice> mode = this.modeSetting;
      return mode.getValue() == SprintModule.ModeChoice.LEGIT;
   }

   public void setPlayerSprinting(boolean sprinting) {
      try {
         ClientAccess.zioMinecraft.player.setSprinting(sprinting);
      } catch (Exception ignoredFailure) {
      }
   }

   private boolean consumeMoveVectorChange() {
      Vec2 currentVector = ClientAccess.zioMinecraft.player.input.moveVector;
      boolean changed;
      if (this.previousMoveVector != null) {
         changed = !currentVector.equals(this.previousMoveVector);
      } else {
         changed = true;
      }
      this.previousMoveVector = currentVector;
      return changed;
   }

   private float getRotationDifference() {
      float[] rotations = ClientContext.playerStateTracker.getPacketRotations();
      float sourceYaw = rotations[0];
      float playerYaw = ClientAccess.zioMinecraft.player.getYRot();
      float yawDifference = Mth.degreesDifferenceAbs(sourceYaw, playerYaw);
      float sourcePitch = rotations[1];
      float pitchDifference = sourcePitch - ClientAccess.zioMinecraft.player.getXRot();
      return yawDifference + Math.abs(pitchDifference);
   }

   private boolean hasMovementSpeedFactorChange() {
      Vec2 inputVector = ClientAccess.zioMinecraft.player.input.moveVector;
      Vec2 adjustedVector = ((LocalPlayerAccessor)ClientAccess.zioMinecraft.player)
         .zio$applyMovementSpeedFactors(inputVector);
      float inputLength = inputVector.length();
      float lengthDifference = inputLength - adjustedVector.lengthSquared();
      double absoluteDifference = Math.abs(lengthDifference);
      return absoluteDifference > 0.05D;
   }

   public boolean isSprintInBlocksRestricted() {
      if (this.inBlocksSetting.isVisible()) {
         return !this.inBlocksSetting.getValue();
      }
      return false;
   }

   public boolean canStartSprinting() {
      if (this.isSprintSuppressed()) {
         return false;
      }
      if (this.directionSpoofSetting.getValue()) {
         if (PlayerStateUtils.isFallFlying()) {
            return false;
         }
      }
      if (PlayerStateUtils.isHungerLimited()) {
         return false;
      }
      if (this.hasWaterBelowWhileAirborne()) {
         return false;
      }
      if (this.shouldDeferToVanillaSprinting()) {
         return false;
      }
      if (ClientAccess.zioMinecraft.player.horizontalCollision && !ClientAccess.zioMinecraft.player.minorHorizontalCollision) {
         return false;
      }
      if (this.hasMovementSpeedFactorChange()) {
         return true;
      }
      if (fastWebModule.isGrimEnabled()) {
         if (PlayerStateUtils.isInCobweb(ClientAccess.zioMinecraft.player)) {
            int ticks = ClientContext.playerStateTracker.getGroundedTicks();
            if (ticks < 2) {
               return false;
            }
            if (ClientAccess.zioMinecraft.player.input.keyPresses.jump()) {
               return false;
            }
         }
      }
      return ClientAccess.zioMinecraft.player.input.getMoveVector().lengthSquared() > 0.0F;
   }

   @EventHandler
   private void onPacketReceive(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.explosionBoostSpeed = 0.0F;
      }
   }

   @EventHandler(priority = 100)
   private void onDirectionUpdateEvent(RotationRequestEvent event) {
      if (this.modeSetting.getValue() != SprintModule.ModeChoice.INSTANT) {
         this.updateDirectionSpoof(event);
      }
   }

   private boolean hasFoodComponentInHand(InteractionHand hand) {
      return ClientAccess.zioMinecraft.player.getItemInHand(hand).has(DataComponents.FOOD);
   }
   public static void bindReferencedModules(ModuleRegistry registry) {
      elytraFlyModule = (ElytraFlyModule)registry.getModule(ElytraFlyModule.class);
      longJumpModule = (LongJumpModule)registry.getModule(LongJumpModule.class);
      fastFallModule = (FastFallModule)registry.getModule(FastFallModule.class);
      speedModule = (SpeedModule)registry.getModule(SpeedModule.class);
      fastWebModule = (FastWebModule)registry.getModule(FastWebModule.class);
      antiCheatModule = (AntiCheatModule)registry.getModule(AntiCheatModule.class);
      noSlowModule = (NoSlowModule)registry.getModule(NoSlowModule.class);
      flightModule = (FlightModule)registry.getModule(FlightModule.class);
   }

   public SprintModule() {
      super("Sprint", "Sprints automatically.", ModuleCategory.MOVEMENT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.RAGE));
      this.stopInLiquidsSetting = this.add(new BooleanSetting("StopInLiquids", true));
      Predicate<Object> inBlocksVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.RAGE;
      this.inBlocksSetting = this.add(new BooleanSetting("InBlocks", true, inBlocksVisibility));
      this.directionSpoofSetting = this.add(new BooleanSetting("DirectionSpoof", false));
      Predicate<Object> keepVisibility = ignored -> this.modeSetting.getValue() != ModeChoice.INSTANT;
      this.keepSetting = this.add(new BooleanSetting("Keep", true, keepVisibility));
      Predicate<Object> slowdownVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.INSTANT;
      this.vanillaSlowdownSetting = this.add(new BooleanSetting("VanillaSlowdown", false, slowdownVisibility));
      Predicate<Object> adjustVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.INSTANT;
      this.adjustSetting = this.add(new BooleanSetting("Adjust", false, adjustVisibility).markVisibilityDependency());
      Predicate<Object> factorVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.INSTANT;
      this.factorSetting = this.add(new NumberSetting<>("Factor", 1.0F, 0.5F, 2.0F, factorVisibility).dependsOn(this.adjustSetting));
      Predicate<Object> boostVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.INSTANT;
      this.boostSetting = this.add(new BooleanSetting("Boost", false, boostVisibility).markVisibilityDependency());
      Predicate<Object> boostSpeedVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.INSTANT && this.boostSetting.isExpanded();
      this.boostSpeedSetting = this.add(new NumberSetting<>("BoostSpeed", 0.5F, 0.1F, 1.0F, boostSpeedVisibility));
      this.explosionBoostTimer = new ElapsedTimer();
      this.previousMoveVector = null;
      this.explosionBoostSpeed = 0.0F;

      Setting<Boolean> stopVisibilitySetting = this.stopInLiquidsSetting;
      Predicate<Boolean> stopVisibility = ignored -> this.modeSetting.getValue() != SprintModule.ModeChoice.LEGIT;
      stopVisibilitySetting.addVisibilityPredicate(stopVisibility);
      Setting<Boolean> directionVisibilitySetting = this.directionSpoofSetting;
      Predicate<Boolean> directionVisibility = ignored -> this.modeSetting.getValue() != SprintModule.ModeChoice.LEGIT;
      directionVisibilitySetting.addVisibilityPredicate(directionVisibility);
      this.adjustSetting.disableNumericBounds();
   }

   public enum ModeChoice implements NamedEntry {
      LEGIT("Vanilla"),
      RAGE("Rage"),
      INSTANT("Instant");

      private final String displayName;
      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
