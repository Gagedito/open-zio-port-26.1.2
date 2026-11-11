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
import dev.zio.client.modules.movement.FireworksModule;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

final class AutoMaceStrikePlanner {
   private static final double[] RADII = {2.5, 4.5, 7.0, 10.0};
   private static final double[] HEIGHTS = {0.5, 2.0, 4.0};
   private static final double[] CENTER_HEIGHTS = {5.0, 9.0};
   private final Long2ObjectOpenHashMap<List<AABB>> collisionCache = new Long2ObjectOpenHashMap<>();
   private final List<Vec3> path = new ArrayList<>();
   private CollisionContext collisionContext;
   private AABB playerBody, targetBox;
   private Vec3 playerPosition, initialVelocity, position, velocity, look, controlVelocity;
   private double playerX, playerZ, targetX, targetY, targetZ, targetMotionX, targetMotionZ;
   private double attackRange, eyeHeight, gravity, controlSpeed, verticalSpeed, glide, boostBudget;
   private double initialFallDistance;
   private float initialYaw, initialPitch, yaw, pitch, simulationYaw, simulationPitch;
   private boolean active, control, usingRocket, rocketActive, descend, simulationDown, selectedDown;
   private int simulationStage, selectedHitTicks = Integer.MAX_VALUE;
   private float[] selectedRotation;

   private Vec3 selectedOffset;
   private int selectedMode;
   private int refreshTicks;
   private int searchCursor;
   private boolean solution;
   private int simulationCount;
   private double score;
   private boolean limitReached;
   private boolean pathBlocked;
   private int simulationHitTicks;

   void reset() {
      active = false;
      solution = false;
      selectedOffset = null;
      selectedMode = 0;
      selectedRotation = null;
      selectedHitTicks = Integer.MAX_VALUE;
      selectedDown = false;
      refreshTicks = 0;
      searchCursor = 0;
      path.clear();
      collisionCache.clear();
   }

   boolean isActive() {
      return active && solution;
   }

   float[] rotation() {
      return isActive() ? selectedRotation : null;
   }

   int hitTicks() {
      return isActive() ? selectedHitTicks : Integer.MAX_VALUE;
   }

   boolean shouldDescend() {
      return isActive() && selectedDown;
   }

   void updateNormal(Entity target, double range, boolean rockets, double budget) {
      control = false;
      usingRocket = rockets;
      boostBudget = budget;
      update(target, range);
   }

   void updateControl(Entity target, double range, double speed, double vSpeed, double glideSpeed) {
      control = true;
      usingRocket = false;
      boostBudget = Double.POSITIVE_INFINITY;
      controlSpeed = speed;
      verticalSpeed = vSpeed;
      glide = glideSpeed;
      update(target, range);
   }

   private void update(Entity target, double range) {
      var player = ClientAccess.zioMinecraft.player;
      active = true;
      collisionCache.clear();
      collisionContext = CollisionContext.of(player);
      var dimensions = player.getDimensions(Pose.FALL_FLYING);
      playerBody = dimensions.makeBoundingBox(0, 0, 0).inflate(0.1);
      eyeHeight = dimensions.eyeHeight();
      gravity = player.getGravity();
      targetBox = target.getBoundingBox();
      targetX = target.getX();
      targetY = target.getY();
      targetZ = target.getZ();
      targetMotionX = targetX - target.xo;
      targetMotionZ = targetZ - target.zo;
      double targetSpeed = Math.sqrt(targetMotionX * targetMotionX + targetMotionZ * targetMotionZ);
      if (targetSpeed > 1) {
         targetMotionX /= targetSpeed;
         targetMotionZ /= targetSpeed;
      }
      playerPosition = player.position();
      playerX = playerPosition.x;
      playerZ = playerPosition.z;
      initialVelocity = ClientContext.playerStateTracker.getTickDisplacement();
      initialFallDistance = PlayerStateUtils.getTrackedFallDistance();
      float[] rotations = ClientContext.playerStateTracker.getPacketRotations();
      initialYaw = rotations[0];
      initialPitch = rotations[1];
      attackRange = range;
      simulationCount = 0;
      path.clear();

      boolean previousWorks = solution && simulate(selectedOffset, selectedMode, path);
      boolean good = previousWorks && acceptable();
      boolean finalWorks = previousWorks;
      if (!good && (selectedOffset != null || !solution)) {
         finalWorks = false;
         if (simulate(null, 1, null) && acceptable()) {
            selectedOffset = null;
            selectedMode = 1;
            good = true;
            previousWorks = true;
            solution = true;
            refreshTicks = searchCursor = 0;
         }
      }
      boolean search = false;
      if (previousWorks) {
         if (!good && ++refreshTicks >= 10) search = true;
      } else if (!solution && searchCursor == 0) {
         if (++refreshTicks >= 5) search = true;
      } else {
         search = true;
      }
      if (search) {
         refreshSearch(previousWorks);
         finalWorks = false;
      }
      if (!finalWorks) {
         path.clear();
         finalWorks = solution && simulate(selectedOffset, selectedMode, path);
      }
      if (finalWorks) {
         selectedMode = simulationStage;
         selectedHitTicks = simulationHitTicks;
         selectedRotation = new float[] {simulationYaw, simulationPitch};
         selectedDown = simulationDown;
      } else {
         solution = false;
         selectedRotation = null;
         selectedHitTicks = Integer.MAX_VALUE;
         path.clear();
      }
   }

   private List<Vec3> candidateOffsets() {
      List<Vec3> candidates = new ArrayList<>(194);
      for (double height : CENTER_HEIGHTS) {
         candidates.add(new Vec3(0, height, 0));
      }
      double bearing = Mth.atan2(playerZ - targetZ, playerX - targetX);
      for (int index = 0; index < 16; index++) {
         int offset = (index + 1) / 2 * (index % 2 == 1 ? 1 : -1);
         // shortcut: angle uses π from a cross-capture binding, confirm against the native initializer.
         double angle = bearing + offset * Math.PI * 2 / 16;
         for (double radius : RADII) {
            for (double height : HEIGHTS) {
               candidates.add(new Vec3(Math.cos(angle) * radius, height, Math.sin(angle) * radius));
            }
         }
      }
      return candidates;
   }

   private List<AABB> collisionBoxes(int x, int y, int z) {
      long key = BlockPos.asLong(x, y, z);
      List<AABB> cached = collisionCache.get(key);
      if (cached != null) return cached;
      BlockPos position = new BlockPos(x, y, z);
      var shape = ClientAccess.zioMinecraft.level.getBlockState(position)
         .getCollisionShape(ClientAccess.zioMinecraft.level, position, collisionContext);
      List<AABB> boxes = new ArrayList<>();
      for (AABB box : shape.toAabbs()) boxes.add(box.move(x, y, z));
      collisionCache.put(key, boxes);
      return boxes;
   }

   private boolean candidateClear(Vec3 offset) {
      AABB bounds = playerBody.move(targetX + offset.x, targetY + offset.y, targetZ + offset.z);
      for (int x = Mth.floor(bounds.minX); x < Mth.ceil(bounds.maxX); x++) {
         for (int z = Mth.floor(bounds.minZ); z < Mth.ceil(bounds.maxZ); z++) {
            for (int y = Mth.floor(bounds.minY) - 1; y < Mth.ceil(bounds.maxY); y++) {
               for (AABB box : collisionBoxes(x, y, z)) {
                  if (box.intersects(bounds)) return false;
               }
            }
         }
      }
      return true;
   }

   private boolean pointClear(double x, double y, double z) {
      int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
      for (int yy = by - 1; yy <= by; yy++) {
         for (AABB box : collisionBoxes(bx, yy, bz)) {
            if (box.intersects(x, y, z, x, y, z)) return false;
         }
      }
      return true;
   }

   private boolean lineClear(Vec3 from, Vec3 to) {
      Vec3 delta = to.subtract(from);
      int steps = Math.max(1, Mth.ceil(delta.length() / 0.25));
      for (int i = 0; i < steps; i++) {
         Vec3 point = from.add(delta.scale((double) i / steps));
         if (!pointClear(point.x, point.y, point.z)) return false;
      }
      return true;
   }

   private boolean bodyClear(Vec3 position) {
      AABB bounds = playerBody.move(position.x, position.y, position.z);
      for (int x = Mth.floor(bounds.minX); x < Mth.ceil(bounds.maxX); x++) {
         for (int z = Mth.floor(bounds.minZ); z < Mth.ceil(bounds.maxZ); z++) {
            for (int y = Mth.floor(bounds.minY) - 1; y < Mth.ceil(bounds.maxY); y++) {
               for (AABB box : collisionBoxes(x, y, z)) {
                  if (box.intersects(bounds)) return false;
               }
            }
         }
      }
      return true;
   }

   private boolean movementClear(Vec3 position, Vec3 velocity) {
      int steps = Math.max(1, Mth.ceil(velocity.length() / 0.5));
      for (int i = 1; i <= steps; i++) {
         if (!bodyClear(position.add(velocity.scale((double) i / steps)))) return false;
      }
      return true;
   }

   private boolean inAttackRange(Vec3 position, int tick) {
      Vec3 eye = position.add(0, eyeHeight, 0);
      AABB movingTarget = targetBox.move(targetMotionX * tick, 0, targetMotionZ * tick);
      Vec3 nearest = BoundingBoxUtils.clampToBounds(eye, movingTarget);
      return eye.distanceToSqr(nearest) <= attackRange * attackRange && lineClear(eye, nearest);
   }

   private boolean followOnHit(Vec3 position, Vec3 velocity, int tick) {
      for (int i = 0; i < 6; i++) {
         if (inAttackRange(position, tick + i)) return true;
         if (!movementClear(position, velocity)) return false;
         position = position.add(velocity);
         velocity = new Vec3(velocity.x * 0.9100000262260437,
            (velocity.y - gravity) * 0.9800000190734863,
            velocity.z * 0.9100000262260437);
      }
      return false;
   }

   private void setAim(Vec3 direction, boolean clampPitch) {
      yaw = Mth.wrapDegrees((float) (Mth.atan2(direction.z, direction.x) * (180.0 / Math.PI)) - 90.0F);
      pitch = (float) (Mth.atan2(-direction.y,
         Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * (180.0 / Math.PI));
      if (clampPitch) pitch = Math.max(0, pitch);
      float pitchRadians = pitch * ((float) Math.PI / 180.0F);
      float yawRadians = -yaw * ((float) Math.PI / 180.0F);
      float cosPitch = Mth.cos((double) pitchRadians);
      look = new Vec3(Mth.sin((double) yawRadians) * cosPitch, -Mth.sin((double) pitchRadians),
         Mth.cos((double) yawRadians) * cosPitch);
   }

   private Vec3 elytraVelocity(Vec3 current) {
      float p = pitch * ((float) Math.PI / 180.0F);
      double cos = Math.cos(p), cosSquared = cos * cos;
      double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
      double horizontal = Math.sqrt(current.x * current.x + current.z * current.z);
      double x = current.x, y = current.y + gravity * (-1 + cosSquared * 0.75), z = current.z;
      if (lookHorizontal > 0) {
         if (y < 0) {
            double lift = y * -0.1 * cosSquared;
            x += look.x * lift / lookHorizontal;
            y += lift;
            z += look.z * lift / lookHorizontal;
         }
         if (p < 0) {
            double lift = horizontal * -Mth.sin((double) p) * 0.04;
            x -= look.x * lift / lookHorizontal;
            y += lift * 3.2;
            z -= look.z * lift / lookHorizontal;
         }
         x += (look.x / lookHorizontal * horizontal - x) * 0.1;
         z += (look.z / lookHorizontal * horizontal - z) * 0.1;
      }
      return new Vec3(x * 0.9900000095367432, y * 0.9800000190734863,
         z * 0.9900000095367432);
   }

   private void aimAt(Vec3 target, int tick) {
      Vec3 delta = target.subtract(position);
      if (control) {
         double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
         setAim(delta, true);
         double dx = targetX + targetMotionX * tick - position.x;
         double dz = targetZ + targetMotionZ * tick - position.z;
         double targetDistance = Math.sqrt(dx * dx + dz * dz);
         double speed = Math.min(Math.min(controlSpeed, targetDistance), 1.0);
         descend = delta.y < 0 && verticalSpeed > 0
            && (descend || horizontal <= -delta.y / verticalSpeed * speed);
         controlVelocity = new Vec3(horizontal < 0.0001 ? 0 : delta.x / horizontal * speed,
            descend ? -verticalSpeed : -glide,
            horizontal < 0.0001 ? 0 : delta.z / horizontal * speed);
         return;
      }
      double length = delta.length();
      Vec3 desired = length < 0.0001 ? new Vec3(0, -1, 0) : delta.scale(1 / length);
      Vec3 adjusted = desired;
      if (usingRocket) {
         for (int i = 0; i < 2; i++) {
            setAim(adjusted, false);
            Vec3 projectedVelocity = elytraVelocity(velocity).scale(rocketActive ? 1 : 0.5);
            Vec3 perpendicular = projectedVelocity.subtract(desired.scale(projectedVelocity.dot(desired)));
            double amount = perpendicular.length();
            double gain = rocketActive ? 0.8 : 0.85;
            adjusted = amount >= gain ? perpendicular.scale(-1)
               : desired.scale(Math.sqrt(1 - amount * amount / (gain * gain)))
                  .subtract(perpendicular.scale(1 / gain));
         }
      }
      setAim(adjusted, true);
   }

   private boolean simulate(Vec3 offset, int requestedStage, List<Vec3> path) {
      position = playerPosition;
      velocity = initialVelocity;
      double fallDistance = initialFallDistance;
      limitReached = false;
      pathBlocked = false;
      rocketActive = false;
      float previousYaw = initialYaw, previousPitch = initialPitch;
      descend = control && velocity.y < -glide - 0.01;
      int stage = offset == null ? 1 : requestedStage;
      Vec3 waypoint = offset == null ? new Vec3(targetX, targetY, targetZ)
         : new Vec3(targetX + offset.x, targetY + offset.y, targetZ + offset.z);
      if (inAttackRange(position, 0)) {
         aimAt(new Vec3(targetX, targetY + 0.25, targetZ), 0);
         simulationStage = 1;
         simulationYaw = yaw;
         simulationPitch = pitch;
         simulationDown = descend;
         simulationHitTicks = 0;
         score = fallDistance;
         return true;
      }
      for (int tick = 0; tick < 80; tick++) {
         simulationCount++;
         double mx = targetMotionX * tick, mz = targetMotionZ * tick;
         Vec3 movedTarget = new Vec3(targetX + mx, targetY, targetZ + mz);
         rocketActive = usingRocket && position.distanceToSqr(movedTarget) > boostBudget * boostBudget;
         Vec3 movedWaypoint = waypoint.add(mx, 0, mz);
         if (stage == 0 && (position.y < waypoint.y || position.distanceToSqr(movedWaypoint) <= 9)) stage = 1;
         aimAt(stage == 0 ? movedWaypoint : movedTarget.add(0, 0.25, 0), tick);
         if (tick == 0) {
            simulationStage = stage;
            simulationYaw = yaw;
            simulationPitch = pitch;
            simulationDown = descend;
         }
         if (velocity.y > -0.5 && fallDistance > 1) {
            fallDistance = 1;
            limitReached = true;
         }
         Vec3 previousPosition = position;
         Vec3 previousVelocity = velocity;
         velocity = control ? controlVelocity : elytraVelocity(velocity);
         if (rocketActive) velocity = velocity.add(FireworksModule.getGrimBoost(yaw, pitch, previousYaw, previousPitch, velocity));
         previousYaw = yaw;
         previousPitch = pitch;
         if (!movementClear(position, velocity)) return false;
         position = position.add(velocity);
         if (velocity.y < 0) fallDistance -= velocity.y;
         if (usingRocket && !rocketActive) {
            velocity = velocity.add(look.scale(0.1)).add(look.scale(1.5).subtract(velocity).scale(0.5));
         }
         if (path != null) path.add(position);
         if (inAttackRange(position, tick + 1)) {
            simulationHitTicks = tick + 1;
            score = fallDistance - 0.15 * simulationHitTicks;
            if (!followOnHit(previousPosition, previousVelocity, tick)) {
               score -= 10;
               pathBlocked = true;
            }
            return true;
         }
      }
      simulationHitTicks = Integer.MAX_VALUE;
      score = fallDistance - 100;
      return true;
   }

   private boolean acceptable() {
      return !limitReached && !pathBlocked && simulationHitTicks != Integer.MAX_VALUE;
   }

   private void refreshSearch(boolean previousStillWorks) {
      refreshTicks = 0;
      var candidates = candidateOffsets();
      boolean found = false;
      Vec3 bestOffset = null;
      int bestMode = 0;
      double bestScore = 0;
      if (previousStillWorks && simulate(selectedOffset, selectedMode, null)) {
         found = true;
         bestOffset = selectedOffset;
         bestMode = selectedMode;
         bestScore = score + 1;
      }
      if (simulate(null, 1, null) && (!found || score > bestScore)) {
         found = true;
         bestOffset = null;
         bestMode = 1;
         bestScore = score;
      }
      int start = searchCursor;
      searchCursor = 0;
      for (int i = 0; i < candidates.size(); i++) {
         if (simulationCount >= 3000) {
            searchCursor = (start + i) % candidates.size();
            break;
         }
         Vec3 offset = candidates.get((start + i) % candidates.size());
         if (candidateClear(offset) && lineClear(new Vec3(targetX + offset.x, targetY + offset.y + eyeHeight, targetZ + offset.z), targetBox.getCenter()) && simulate(offset, 0, null)
            && (!found || score > bestScore)) {
            found = true;
            bestOffset = offset;
            bestMode = 0;
            bestScore = score;
            if (acceptable()) break;
         }
      }
      solution = found;
      if (found && (bestOffset != selectedOffset || bestMode != selectedMode)) {
         selectedOffset = bestOffset;
         selectedMode = bestMode;
      }
   }
}
