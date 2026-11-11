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
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.utils.combat.BlockRaycastClassifier;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.utils.math.Vec3Utils;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.combat.BlockRaycastContext;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.managers.HoleManager;
import dev.zio.client.utils.world.HoleInfo;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.movement.WallsVelocityHandler;
import dev.zio.client.utils.combat.RaycastUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.HoleSnapPriorityChoice;
import java.util.function.Predicate;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class HoleSnapModule extends Module {
   protected final Setting<HoleSnapPriorityChoice> prioritySetting;
   private final Setting<Float> heightSetting;
   private final Setting<Float> timeoutSetting;
   private final Setting<Float> rangeSetting;
   private final Setting<Float> speedSetting;
   private final Setting<Float> pitchSetting;
   private final Setting<Boolean> autoDisableSetting;
   private final Setting<Boolean> pauseStepSetting;
   private final Setting<Boolean> pauseInBlocksSetting;
   protected final Setting<Boolean> shiftSetting;
   protected final Setting<Integer> factorSetting;
   protected final Setting<Integer> chargeSetting;
   private final Setting<Boolean> directionalSetting;
   private final Setting<Boolean> softSetting;
   protected final Setting<Boolean> alignSetting;
   protected final Setting<Boolean> onlyWideSetting;
   private final Setting<Float> alignPitchSetting;
   private final HoleSnapChargeController chargeController;
   private final ElapsedTimer stepPauseTimer;
   public final ElapsedTimer targetTimeoutTimer;
   private HoleSnapModule.SnapTarget alignmentTarget;
   private boolean wasInHole;
   private boolean forwardOverrideActive;

   @Override
   public void onToggle() {
      this.targetTimeoutTimer.clear();
   }

   @EventHandler(priority = -100)
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
      if (this.forwardOverrideActive && !this.shouldPauseSnapping() && !this.isWaitingForTarget()
         && RotationManager.isMovementSyncEnabled()) {
         ClientContext.inputOverrides.requestForward(true);
         ClientContext.inputOverrides.requestForwardMovement(1.0F);
      }

      AABB playerBounds = ClientAccess.zioMinecraft.player.getBoundingBox();
      double playerY = ClientAccess.zioMinecraft.player.getY();
      AABB feetBounds = playerBounds.setMaxY(playerY).inflate(0.05D);
      if (!ClientAccess.zioMinecraft.player.isInWater()) {
         return;
      }
      HoleManager holeManager = ClientContext.holeManager;
      BlockPos belowPlayer = ClientAccess.zioMinecraft.player.blockPosition().below();
      if (!holeManager.intersectsHole(belowPlayer) || BlockPos.betweenClosedStream(feetBounds).count() != 1L) {
         return;
      }
      ClientContext.inputOverrides.requestSneak(true);
   }

   @EventHandler
   private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
      if (this.shouldPauseSnapping()) {
         return;
      }

      this.alignmentTarget = this.findAlignmentTarget();
      HoleManager holeManagerSnapshot = ClientContext.holeManager;

      BlockPos playerBlock = PlayerStateUtils.getLocalRoundedBlockPosition();

      if (holeManagerSnapshot.intersectsHole(playerBlock)) {
         if (this.autoDisableSetting.getValue() && (this.alignmentTarget == null || this.alignmentTarget.distance < 0.01D)) {
            this.disable();
            this.forwardOverrideActive = false;
            return;
         }

         this.targetTimeoutTimer.reset();
      }

      if (this.isWaitingForTarget() || RotationManager.isMovementSyncEnabled()) {
         return;
      }

      HoleSnapModule.SnapTarget target = this.getSnapTarget();
      if (target == null) {
         this.forwardOverrideActive = false;
         return;
      }

      if (this.softSetting.getValue()) {
         if (this.directionalSetting.getValue()) {
            double directionalDistance = target.distance();

            if (directionalDistance < 0.01D) {
               return;
            }
         }
      }

      Vec3 targetPosition = target.position();
      Vec3 playerPosition = ClientAccess.zioMinecraft.player.position();
      Vec3 direction = targetPosition.subtract(playerPosition).with(Axis.Y, 0.0).normalize();

      double movementSpeed = MovementPhysics.getBaseMoveSpeed(
         true
      );
      Float speedMultiplier = this.speedSetting.getValue();
      double scaledSpeed = movementSpeed * speedMultiplier.floatValue();
      double targetDistance = target.distance();
      double stepSpeed = Math.min(scaledSpeed, targetDistance);
      Vec3 movement = direction.scale(stepSpeed);
      double deltaZ = movement.z;
      double deltaX = movement.x;
      event.setHorizontalVelocity(deltaX, deltaZ);
   }

   protected boolean hasBlockingRaycast(Vec3 start, Vec3 end) {
      BlockRaycastContext context = new BlockRaycastContext(
         start,
         end,
         Block.COLLIDER,
         Fluid.NONE,
         ClientAccess.zioMinecraft.player,
         BlockRaycastClassifier.NO_OVERRIDE
      );
      BlockHitResult hit = RaycastUtils.raycast(context);
      return Type.MISS != hit.getType();
   }

   @EventHandler
   private void onRotationUpdate(RotationRequestEvent event) {
      ClientContext.playerTickController.releaseMultiplier(this);

      this.alignmentTarget = this.findAlignmentTarget();

      boolean inHole = ClientContext.holeManager.isInHole();
      if (ClientAccess.zioMinecraft.player.onGround() && inHole != this.wasInHole) {
         this.wasInHole = inHole;

         this.stepPauseTimer.reset();
      }

      if (this.shouldPauseSnapping() || this.isWaitingForTarget()) {
         return;
      }

      HoleSnapModule.SnapTarget target = this.getSnapTarget();
      if (target == null) {
         this.forwardOverrideActive = false;
         return;
      }

      if (RotationManager.isMovementSyncEnabled()) {
         ClientContext.rotationManager.forceRotationOverride();
         RotationManager targetRotationManagerSnapshot = ClientContext.rotationManager;
         Vec3 targetPosition = target.position();

         float[] angles = RotationManager.getRotationsToPosition(targetPosition);
         targetRotationManagerSnapshot.requestRotation(angles, 99999, true);
         this.forwardOverrideActive = true;
      }

      if (this.shiftSetting.getValue()) {
         this.chargeController.requestShift();
      }
   }

   private boolean shouldPauseSnapping() {
      float playerPitch = ClientAccess.zioMinecraft.player.getXRot();
      Float requiredPitch = this.pitchSetting.getValue();
      if (playerPitch < requiredPitch) {
         if (this.pitchSetting.getValue() != 0.0F) {
            return true;
         }
      }

      if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L)) {
         if (!ClientAccess.zioMinecraft.player.isSpectator()) {
            if (!ClientAccess.zioMinecraft.player.isFallFlying()) {
               return false;
            }
         }
      }

      return true;
   }

   private HoleSnapModule.SnapTarget findAlignmentTarget() {
      if (!this.alignSetting.getValue()) {
         return null;
      }
      if (!ClientContext.holeManager.isInHole()) {
         return null;
      }
      if (!ClientAccess.zioMinecraft.player.onGround() || MovementPhysics.isLocalPlayerMoving()) {
         return null;
      }
      float pitch = ClientAccess.zioMinecraft.player.getXRot();
      Float minimumPitch = this.alignPitchSetting.getValue();
      if (pitch < minimumPitch) {
         if (!this.alignPitchSetting.isAtMinimum()) {
            return null;
         }
      }
      HoleManager alignedHoleManager = ClientContext.holeManager;
      BlockPos playerBlock = PlayerStateUtils.getLocalRoundedBlockPosition();
      HoleInfo hole = alignedHoleManager.findIntersectingHole(playerBlock);
      if (hole == null) {
         return null;
      }
      double holeSize = hole.bounds().getSize();
      if (holeSize == 1.0D) {
         if (this.onlyWideSetting.getValue()) {
            return null;
         }
      }
      AABB holeBounds = hole.bounds();
      Vec3 holeCenter = BoundingBoxUtils.getBottomCenter(holeBounds);
      double distance = ClientAccess.zioMinecraft.player.position().distanceTo(holeCenter);
      double minimumDistance = RotationManager.isMovementSyncEnabled() ? 0.1D : 0.0;
      if (distance < minimumDistance) {
         return null;
      }
      return new HoleSnapModule.SnapTarget(holeCenter, distance);
   }

   private boolean isWaitingForTarget() {
      if (this.alignmentTarget != null) {
         return false;
      }

      ElapsedTimer timeoutTimerSnapshot = this.targetTimeoutTimer;
      double timeoutSeconds = this.timeoutSetting.getValue();
      return !timeoutTimerSnapshot.hasElapsed(timeoutSeconds, TimeUnit.SECONDS);
   }

   public HoleSnapModule.SnapTarget getSnapTarget() {
      if (this.pauseInBlocksSetting.getValue() && WallsVelocityHandler.hasRaisedPlayerCollision()) {
         return null;
      }
      if (this.alignmentTarget != null) {
         return this.alignmentTarget;
      }

      double bestDistance = 9999.0D;
      HoleInfo bestHole = null;
      Vec3 bestPosition = null;

      Vec3 footPosition = ClientAccess.zioMinecraft.player.position().add(0.0, 0.01D, 0.0);
      double heightOffsetY = ClientAccess.zioMinecraft.player.getBbHeight();
      Vec3 headPosition = footPosition.add(0.0, heightOffsetY, 0.0);

      for (Object holeEntry : ClientContext.holeManager.getHoles()) {
         HoleInfo hole = (HoleInfo)holeEntry;
         Vec3 candidatePosition = hole.bounds().getCenter();
         double candidateDistance = footPosition.distanceTo(candidatePosition);
         if (this.softSetting.getValue() && this.directionalSetting.getValue()) {
            Axis yAxis = Axis.Y;
            double playerY = footPosition.y();
            double candidateY = candidatePosition.y;
            double candidateTop = candidatePosition.y;
            Setting<Float> heightSettingSnapshot = this.heightSetting;
            Float heightValue = heightSettingSnapshot.getValue();
            candidateTop += heightValue.floatValue();
            double maximumCandidateY = candidateTop - 0.5D;
            double clampedY = Mth.clamp(playerY, candidateY, maximumCandidateY);
            candidatePosition = candidatePosition.with(yAxis, clampedY);
         } else {
            candidatePosition = candidatePosition.add(0.0, 0.5D, 0.0);
            Vec3 capturedPlayerPosition = ClientAccess.zioMinecraft.player.position();
            candidateDistance = Vec3Utils.horizontalDistance(candidatePosition, capturedPlayerPosition);
            double verticalDifference = ClientAccess.zioMinecraft.player.getY() - candidatePosition.y;
            double heightDifference = Math.abs(verticalDifference);
            Setting<Float> heightSettingSnapshot = this.heightSetting;
            Float maximumHeight = heightSettingSnapshot.getValue();
            if (heightDifference > maximumHeight.floatValue()) {
               continue;
            }
         }

         double holeY = hole.pos().getY();
         if (holeY >= footPosition.y()) {
            continue;
         }
         if (hole.trapped()) {
            continue;
         }
         if (this.directionalSetting.getValue()) {
            if (!MovementPhysics.isMovingToward(candidatePosition) && candidateDistance > 0.05D) {
               continue;
            }
         }
         Float range = this.rangeSetting.getValue();
         if (candidateDistance > range.floatValue()) {
            continue;
         }
         ClientLevel level = ClientAccess.zioMinecraft.level;
         AABB collisionBounds = hole.bounds();
         double playerMaxY = ClientAccess.zioMinecraft.player.getBoundingBox().maxY;
         AABB boundsForCollision = collisionBounds.setMaxY(playerMaxY);
         if (!level.noCollision(boundsForCollision)) {
            continue;
         }
         Vec3 firstRayEnd = candidatePosition.with(Axis.Y, footPosition.y());
         if (this.hasBlockingRaycast(footPosition, firstRayEnd)) {
            continue;
         }
         Vec3 secondRayEnd = candidatePosition.with(Axis.Y, headPosition.y());
         if (this.hasBlockingRaycast(headPosition, secondRayEnd)) {
            continue;
         }
         Vec3 thirdRayEnd = candidatePosition.with(Axis.Y, headPosition.y());
         if (this.hasBlockingRaycast(candidatePosition, thirdRayEnd)) {
            continue;
         }
         if (bestHole != null) {
            if ((this.prioritySetting.getValue()).getComparator().compare(hole, bestHole) >= 0) {
               continue;
            }
         }
         bestHole = hole;
         bestDistance = candidateDistance;
         bestPosition = candidatePosition;
      }

      if (bestHole == null) {
         return null;
      }

      Float finalRange = this.rangeSetting.getValue();
      return bestDistance > finalRange.floatValue() ? null : new HoleSnapModule.SnapTarget(bestPosition, bestDistance);
   }

   public boolean shouldPauseStep() {
      if (!this.stepPauseTimer.hasElapsed(400L)) {
         if (ClientContext.holeManager.isInHole()) {
            if (this.pauseStepSetting.getValue()) {
               return true;
            }
         }
      }

      return false;
   }

   private static String[] createAliases() {
      String[] aliases = new String[2];
      aliases[0] = "holetp";
      aliases[1] = "anchor";
      return aliases;
   }

   public HoleSnapModule() {
      super("HoleSnap", "Pushes you into holes as you go past them.", ModuleCategory.MOVEMENT, createAliases());
      this.prioritySetting = this.add(new EnumSetting<>("Priority", HoleSnapPriorityChoice.DISTANCE));
      this.heightSetting = this.add(new NumberSetting<>("Height", 2.0F, 1.0F, 6.0F).withUnit("m"));
      this.timeoutSetting = this.add(new NumberSetting<>("Timeout", 0.5F, 0.0F, 2.0F).withUnit("s"));
      this.rangeSetting = this.add(new NumberSetting<>("Range", 1.0F, 0.1F, 3.0F).withUnit("m"));
      Predicate<Object> speedVisibility = ignored -> !RotationManager.isMovementSyncEnabled();
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.5F, 3.0F, speedVisibility));
      this.pitchSetting = this.add(new NumberSetting<>("Pitch", 0.0F, 0.0F, 90.0F).withUnit("°"));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", true));
      this.pauseStepSetting = this.add(new BooleanSetting("PauseStep", false));
      this.pauseInBlocksSetting = this.add(new BooleanSetting("PauseInBlocks", false));
      this.shiftSetting = this.add(new BooleanSetting("Shift", false).markVisibilityDependency());
      this.factorSetting = this.add(new NumberSetting<>("Factor", 2, 1, 6).dependsOn(this.shiftSetting));
      this.chargeSetting = this.add(new NumberSetting<>("Charge", 3, 1, 16).dependsOn(this.shiftSetting));
      this.directionalSetting = this.add(new BooleanSetting("Directional", false).markVisibilityDependency());
      this.softSetting = this.add(new BooleanSetting("Soft", false).dependsOn(this.directionalSetting));
      this.alignSetting = this.add(new BooleanSetting("Align", false).markVisibilityDependency());
      this.onlyWideSetting = this.add(new BooleanSetting("OnlyWide", false).dependsOn(this.alignSetting));
      this.alignPitchSetting = this.add(new NumberSetting<>("AlignPitch", 0.0F, 0.0F, 90.0F).withUnit("°").dependsOn(this.alignSetting));
      this.chargeController = new HoleSnapChargeController(this);
      this.stepPauseTimer = new ElapsedTimer();
      this.targetTimeoutTimer = new ElapsedTimer();
      this.pitchSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.alignPitchSetting.setBoundaryLabel("Any", SettingLabelCondition.MIN);
   }

   public record SnapTarget(
      Vec3 position,
      double distance
   ) {}
}
