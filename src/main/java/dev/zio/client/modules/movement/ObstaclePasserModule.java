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
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.utils.math.Vec3Utils;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.combat.AutoArmorModule;
import dev.zio.client.modules.player.RotationLockModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.mixin.BlockBehaviourAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ObstaclePasserModule extends Module {
   private static RotationLockModule rotationLock;
   private static AutoArmorModule autoArmor;
   private static ElytraFlyModule elytraFly;
   private final ObstacleCorrectionWindow correctionWindow;
   public final Setting<Boolean> alignSetting;
   private final Setting<Boolean> onlyLoadedSetting;
   public final Setting<Integer> ySetting;
   public final Setting<Integer> maxTravelSetting;
   private final ElapsedTimer loadedChunksTimer;
   private float travelYaw;
   private BlockPos anchorPosition;
   private boolean pathWasActive;
   private boolean elytraPassActive;
   private int passCooldownTicks;
   private ObstacleAlignmentDirection alignmentDirection;

   private boolean hasNearbyCollision() {
      AABB bounds = ClientAccess.zioMinecraft.player.getBoundingBox();
      double horizontalInflation = BoundingBoxUtils.blockCornerInset;
      bounds = bounds.inflate(horizontalInflation, 0.0, horizontalInflation);
      return !ClientAccess.zioMinecraft.level.noBlockCollision(ClientAccess.zioMinecraft.player, bounds);
   }

   private boolean shouldAdjustBounceYaw() {
      int integerYaw = (int)this.travelYaw;

      if (integerYaw % MathConstants.NINETY_DEGREES
         != 0) {
         return false;
      }

      if (elytraFly.isToggled()) {
         if (elytraFly.modeSetting.getValue() == ElytraFlyModule.ModeChoice.BOUNCE) {
            return elytraFly.packetSetting.getValue();
         }
      }

      return false;
   }

   private float findYawAdjustment() {
      int closestOffset = 1000;
      float yawAdjustment = 0.0F;
      float yawSteps = this.travelYaw
         / MathConstants.NINETY_DEGREES;
      int roundedYaw = Math.round(yawSteps)
         * MathConstants.NINETY_DEGREES;
      float scanYaw = roundedYaw + MathConstants.NINETY_DEGREES;
      Vec3 direction = Vec3.directionFromRotation(0.0F, scanYaw);
      BlockPos playerPosition = ClientAccess.zioMinecraft.player.blockPosition();
      double directionX = direction.x;
      double offsetX = Math.ceil(directionX);
      double directionZ = direction.z;
      double offsetZ = Math.ceil(directionZ);
      BlockPos blockDirection = BlockPos.containing(offsetX, 0.0, offsetZ);

      int scanOffset = -6;

      while (true) {
         if (scanOffset > 6) {
            return yawAdjustment;
         }

         int absoluteOffset = Math.abs(scanOffset);
         BlockPos scaledDirection = blockDirection.multiply(scanOffset);
         BlockPos positionToCheck = playerPosition.offset(scaledDirection);
         if (WorldUtils.isPlacementOccupied(positionToCheck) && absoluteOffset < closestOffset) {
            closestOffset = absoluteOffset;
            float adjustment;
            if (scanOffset > 0) {
               adjustment = 3.0F;
            } else {
               adjustment = -3.0F;
            }

            yawAdjustment = adjustment;
         }

         scanOffset++;
      }
   }

   private void captureTravelYaw() {
      float playerYaw = ClientAccess.zioMinecraft.player.getYRot();

      float yawSteps = (playerYaw + 1.0F)
         / MathConstants.FORTY_FIVE_DEGREES;
      this.travelYaw = Math.round(yawSteps)
         * MathConstants.FORTY_FIVE_DEGREES;
   }

   private boolean canUseLoadedChunks() {
      ClientChunkCache chunkSource = ClientAccess.zioMinecraft.level.getChunkSource();
      ChunkPos capturedChunk = ClientAccess.zioMinecraft.player.chunkPosition();

      if (!ChunkPos.rangeClosed(capturedChunk, 2).allMatch(chunk -> {
         int chunkZ = chunk.z();
         int chunkX = chunk.x();
         return chunkSource.hasChunk(chunkX, chunkZ);
      })) {
         if (this.onlyLoadedSetting.getValue()) {
            return false;
         }
      }

      return true;
   }

   public boolean isElytraPassActive() {
      return this.elytraPassActive && elytraFly.isToggled();
   }

   public boolean isPathActive() {
      return ClientContext.baritoneBridge.isPathingOrCalculating();
   }

   private void updateObstacleTarget() {
      if (ClientContext.baritoneBridge.isPathingOrCalculating()) {
         BlockPos activeTarget = ClientContext.baritoneBridge.getGoalPosition();
         if (activeTarget != null) {
            Vec3 targetCenter = activeTarget.getBottomCenter();
            Vec3 yawOrigin = ClientAccess.zioMinecraft.player.position();
            float targetYaw = RotationManager.calculateRotations(yawOrigin, targetCenter)[0];
            float currentTravelYaw = this.getTravelYaw();
            float yawDifference = Mth.degreesDifferenceAbs(targetYaw, currentTravelYaw);
            if (yawDifference > 100.0F) {
               ClientContext.baritoneBridge.cancelPathing();
               return;
            }

            Vec3 rangeOrigin = ClientAccess.zioMinecraft.player.position();
            float targetDistance = Vec3Utils.horizontalDistance(rangeOrigin, targetCenter);
            int maximumTravel = this.maxTravelSetting.getValue();
            if (targetDistance > maximumTravel + 2) {
               ClientContext.baritoneBridge.cancelPathing();
               return;
            }

            Vec3 arrivalOrigin = ClientAccess.zioMinecraft.player.position();
            float arrivalDistance = Vec3Utils.horizontalDistance(arrivalOrigin, targetCenter);
            if (arrivalDistance <= 1.0F) {
               ClientContext.baritoneBridge.cancelPathing();
               this.passCooldownTicks = 10;
               if (elytraFly.isToggled()) {
                  this.elytraPassActive = true;
               }
            }
         }
      }

      if (this.shouldStartPass() && !ClientContext.baritoneBridge.isPathingOrCalculating()) {
         this.correctionWindow.clear();
         Integer configuredHeight = this.ySetting.getValue();
         int travelHeight;
         if (configuredHeight == -1) {
            travelHeight = this.anchorPosition.getY();
         } else {
            Integer customHeight = this.ySetting.getValue();
            travelHeight = customHeight;
         }

         if (this.alignmentDirection != null) {
            if (this.alignSetting.getValue()) {
               BlockPos alignedTarget = null;
               int alignedSearchOffset = 5;

               while (true) {
                  Integer alignedTravelLimit = this.maxTravelSetting.getValue();
                  if (alignedSearchOffset >= alignedTravelLimit) {
                     break;
                  }

                  BlockPos offsetPosition = this.alignmentDirection.positionAtOffset(alignedSearchOffset);
                  alignedTarget = offsetPosition.atY(travelHeight);
                  BlockPos groundBelow = alignedTarget.below();
                  if (((BlockBehaviourAccessor)WorldUtils.getBlock(groundBelow)).isCollidable()) {
                     break;
                  }

                  alignedSearchOffset++;
               }

               ClientContext.baritoneBridge.pathTo(alignedTarget);
               return;
            }
         }

         Vec3 anchorCenter = this.anchorPosition.getBottomCenter();
         float projectionYaw = this.getTravelYaw();
         Vec3 horizontalDirection = this.createHorizontalDirection(projectionYaw);
         Vec3 playerPosition = ClientAccess.zioMinecraft.player.position();
         double anchorDistance = this.horizontalDistance(anchorCenter, playerPosition);
         Vec3 projectionOffset = horizontalDirection.scale(anchorDistance);
         BlockPos projectedPosition = BlockPos.containing((Position)anchorCenter.add(projectionOffset));
         Vec3 projectedDistanceTarget = new Vec3(projectedPosition);
         double projectedDistance = this.horizontalDistance(playerPosition, projectedDistanceTarget);

         if (projectedDistance > 32.0D) {
            this.anchorPosition = ClientAccess.zioMinecraft.player.blockPosition();
            this.captureTravelYaw();
            return;
         }

         BlockPos searchPosition = null;
         int searchOffset = 5;

         while (true) {
            Integer freeTravelLimit = this.maxTravelSetting.getValue();
            if (searchOffset >= freeTravelLimit) {
               break;
            }

            double freeOffset = searchOffset;
            BlockPos freePositionOffset = BlockPos.containing((Position)horizontalDirection.scale(freeOffset));
            searchPosition = projectedPosition.offset(freePositionOffset);
            BlockPos freeGroundBelow = searchPosition.below();
            if (((BlockBehaviourAccessor)WorldUtils.getBlock(freeGroundBelow)).isCollidable()) {
               break;
            }

            searchOffset++;
         }

         ClientContext.baritoneBridge.pathTo(searchPosition.atY(travelHeight));
      }
   }

   private boolean shouldStartPass() {
      if (this.passCooldownTicks > 0) {
         return false;
      }

      boolean startPass = ClientAccess.zioMinecraft.player.horizontalCollision;

      if (this.shouldAdjustBounceYaw()) {
         double trackedMotion = ClientContext.movementSpeedTracker.getDisplaySpeed();

         if (trackedMotion > 15.0D) {
            startPass = false;
         }
      }

      if (!startPass) {
         if (!this.correctionWindow.exceedsLimit()) {
            if (!PlayerStateUtils.isFallFlying()) {
               int movementState = ClientContext.playerStateTracker.getGroundedTicks();

               if (movementState > 3) {
                  if (ClientAccess.zioMinecraft.player.onGround()) {
                     if (elytraFly.isToggled()) {
                        return true;
                     }
                  }
               }
            }

            return false;
         }
      }

      return true;
   }

   private void updatePathTransition() {
      if (ClientContext.baritoneBridge.isPathingOrCalculating()) {
         if (!this.pathWasActive) {
            this.pathWasActive = true;
            ClientContext.playerStateTracker.setSneaking(true);
            ClientContext.playerStateTracker.setSneaking(false);
         }
      } else if (this.pathWasActive) {
         this.pathWasActive = false;
      }
   }

   @Override
   public void onEnable() {
      if (this.isWorldUnavailable()) {
         this.toggle();
      } else {
         this.pathWasActive = false;
         this.alignmentDirection = null;
         this.passCooldownTicks = 0;
         this.anchorPosition = ClientAccess.zioMinecraft.player.blockPosition();
         this.correctionWindow.clear();

         this.captureTravelYaw();
      }
   }

   @Override
   public void onDisable() {
      ClientContext.baritoneBridge.cancelPathing();
      if (this.pathWasActive) {
         if (autoArmor.isToggled()) {
            autoArmor.elytraSetting.setValue(true);
         }
      }
   }

   public boolean isPassingObstacles() {
      return this.isToggled() && this.isPathActive();
   }

   private float getTravelYaw() {
      if (rotationLock.isToggled()) {
         if (rotationLock.yawSetting.getValue()) {
            Boolean customYaw = rotationLock.customYawSetting.getValue();
            float yaw;
            if (customYaw) {
               Float yawValue = rotationLock.yawValueSetting.getValue();
               yaw = yawValue;
            } else {
               RotationLockModule yawLock = rotationLock;
               float baseYaw = this.travelYaw;
               yaw = yawLock.calculateYaw(baseYaw);
            }

            return yaw;
         }
      }

      return this.travelYaw;
   }

   @EventHandler(priority = -100)
   private void updatePassState(LocalPlayerPreTickEvent event) {
      if (this.canUseLoadedChunks()) {
         this.loadedChunksTimer.reset();
      }

      this.updatePathTransition();
      if (this.alignmentDirection == null) {
         float yaw = this.getTravelYaw();
         this.alignmentDirection = ObstacleAlignmentDirection.fromYaw(
            yaw
         );
      }

      if (this.passCooldownTicks > 0) {
         this.passCooldownTicks--;
      }

      this.correctionWindow.pruneExpired();

      this.updateObstacleTarget();
      if (this.onlyLoadedSetting.getValue()) {
         if (this.loadedChunksTimer.hasElapsed(750L)) {
            return;
         }
      }

      if (!this.isPathActive()) {
         flightInput: {
            if (!this.isElytraPassActive()) {
               if (ClientAccess.zioMinecraft.player.isFallFlying()) {
                  break flightInput;
               }

               if (!elytraFly.isToggled()) {
                  break flightInput;
               }
            }

            ClientContext.inputOverrides.requestJump(true);
         }

         ClientContext.inputOverrides.requestMovement(1.0F, 0.0F);
      }
   }

   @EventHandler
   private void applyTravelYaw(LocalPlayerTickEvent event) {
      if (!ClientContext.baritoneBridge.isPathingOrCalculating()) {
         float yaw = this.getTravelYaw();

         if (this.shouldAdjustBounceYaw()) {
            if (!this.hasNearbyCollision()) {
               yaw += this.findYawAdjustment();
            }
         }

         ClientAccess.zioMinecraft.player.setYRot(yaw);
      }
   }

   private Vec3 createHorizontalDirection(float yaw) {
      Vec3 direction = ClientAccess.zioMinecraft.player.calculateViewVector(0.0F, yaw);
      double directionX = direction.x;
      double absoluteX = Math.abs(directionX);

      if (absoluteX < 0.01D) {
         double preservedYForX = direction.y;
         double preservedZ = direction.z;
         Vec3Utils.setComponents(direction, 0.0, preservedYForX, preservedZ);
      }

      double directionZ = direction.z;
      double absoluteZ = Math.abs(directionZ);

      if (absoluteZ < 0.01D) {
         double preservedX = direction.x;
         double preservedYForZ = direction.y;
         Vec3Utils.setComponents(direction, preservedX, preservedYForZ, 0.0);
      }

      double horizontalX = direction.x;
      double horizontalZ = direction.z;
      Vec3Utils.setComponents(direction, horizontalX, 0.0, horizontalZ);
      return direction;
   }

   private double horizontalDistance(Vec3 first, Vec3 second) {
      Vec3 firstFlat = first.multiply(1.0D, 0.0, 1.0D);
      Vec3 secondFlat = second.multiply(1.0D, 0.0, 1.0D);
      return firstFlat.distanceTo(secondFlat);
   }

   @EventHandler
   private void onPositionCorrection(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.correctionWindow.recordCorrection();
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      rotationLock = (RotationLockModule)modules.getModule(RotationLockModule.class);
      autoArmor = (AutoArmorModule)modules.getModule(AutoArmorModule.class);
      elytraFly = (ElytraFlyModule)modules.getModule(ElytraFlyModule.class);
   }

   private static String createDescription() {
      String warningColor = String.valueOf(ChatFormatting.YELLOW);
      return "Passes obstacles in your way. \n" + warningColor + "Requires either ElytraFly or Speed enabled.";
   }

   public ObstaclePasserModule() {
      super("ObstaclePasser", createDescription(), ModuleCategory.MOVEMENT, new String[0]);

      this.correctionWindow = new ObstacleCorrectionWindow(
         3
      );
      this.alignSetting = this.add(new BooleanSetting("Align", true));
      this.onlyLoadedSetting = this.add(new BooleanSetting("OnlyLoaded", true));
      this.ySetting = this.add(new NumberSetting<>("Y", -1, -1, 128));
      this.maxTravelSetting = this.add(new NumberSetting<>("MaxTravel", 12, 6, 15));
      this.loadedChunksTimer = new ElapsedTimer();
      this.ySetting.setBoundaryLabel("~", SettingLabelCondition.MIN);
      this.alignSetting.setChangeCallback(() -> this.alignmentDirection = null);
   }
}
