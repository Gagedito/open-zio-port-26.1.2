/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.BlockChangeEvent;
import baritone.api.event.events.ChunkEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.interfaces.IGoalRenderPos;
import dev.zio.client.ClientAccess;
import dev.zio.client.utils.chat.ChatUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class EntityControlPathfinder implements ClientAccess, AbstractGameEventListener, IBaritoneProcess {
   private final EntityControlModule module;
   private final IBaritone baritone;
   private EntityControlPathContext context;
   private CompletableFuture<EntityControlPathContext.Route> calculation;
   private Goal goal;
   private List<Vec3> route = List.of();
   private boolean pathFinished;
   private int routeIndex;
   private int shortcutDelay;
   private boolean blocksChanged;
   private final Set<BlockPos> blockedPositions = new HashSet<>();
   private int retryTicks;
   private boolean warnedNoRoute;
   private boolean warnedUnsupported;
   private Vec3 lastPosition;
   private int stuckTicks;
   private Vec3 lastRepathPosition;
   private int repeatedRepaths;
   private Vec3 motion = Vec3.ZERO;
   private int motionTick = -1;

   EntityControlPathfinder(EntityControlModule module) {
      this.module = module;
      this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      this.baritone.getPathingControlManager().registerProcess(this);
      this.baritone.getGameEventHandler().registerEventListener(this);
   }

   Vec3 getMotion() {
      return zioMinecraft.player != null && zioMinecraft.player.tickCount == this.motionTick ? this.motion : null;
   }

   @Override
   public boolean isActive() {
      Entity vehicle = this.getVehicle();
      Goal activeGoal = this.getGoal();
      if (vehicle == null || activeGoal == null || target(activeGoal, vehicle.position()) == null) {
         return false;
      }
      if (!EntityControlPathContext.isSupported()) {
         this.warnUnsupported("This Baritone version doesn't contain a supported elytra pathfinder.");
         return false;
      }
      if (!isBelowNetherRoof(vehicle)) {
         this.warnUnsupported("This Baritone version pathfinder works only in Nether.");
         return false;
      }
      return true;
   }

   @Override
   public PathingCommand onTick(boolean calculationFailed, boolean safeToCancel) {
      Entity vehicle = this.getVehicle();
      Goal activeGoal = this.getGoal();
      Vec3 position = vehicle == null ? null : vehicle.position();
      Vec3 destination = position == null || activeGoal == null ? null : target(activeGoal, position);
      if (destination == null) {
         return new PathingCommand(null, PathingCommandType.DEFER);
      }
      if (!activeGoal.equals(this.goal)) {
         this.reset();
         this.goal = activeGoal;
         this.message("Going to " + BlockPos.containing(destination).toShortString() + ".");
      }
      if (isAtGoal(activeGoal, vehicle, position, destination)) {
         this.message("Done :^)");
         return this.finish();
      }
      EntityControlPathContext pathContext = this.getContext(vehicle);
      if (pathContext == null) {
         this.warning("Failed to start the pathfinder.", ChatUtils.MessagePriority.HIGH);
         return this.finish();
      }
      AABB bounds = relativeBounds(vehicle);
      if (this.calculation != null && this.calculation.isDone()) {
         EntityControlPathContext.Route result = this.calculation.getNow(null);
         this.calculation = null;
         if (result == null || result.nodes().isEmpty()) {
            if (!this.warnedNoRoute) {
               this.warning("No route found yet, retrying.", ChatUtils.MessagePriority.MID);
            }
            this.warnedNoRoute = true;
            this.retryTicks = 40;
         } else {
            this.warnedNoRoute = false;
            this.route = routePoints(result.nodes(), bounds);
            this.pathFinished = result.finished();
            this.shortcutDelay = 0;
            this.stuckTicks = 0;
            Vec3 last = this.route.getLast();
            if (this.pathFinished && last.distanceTo(destination) <= 16.0D && isLoaded(destination)
               && canTraverse(vehicle, last, destination)) {
               this.route.add(destination);
            } else if (this.pathFinished && last.distanceTo(position) < 1.5D) {
               this.message("Stopped " + Math.round(position.distanceTo(destination)) + "m away, impossible to reach the destination.");
               return this.finish();
            }
            this.routeIndex = this.nearestReachablePoint(vehicle, position);
         }
      }
      Vec3 next = this.nextTarget(vehicle, position);
      boolean stuck = this.updateStuck(position, next != null);
      if (this.retryTicks > 0) {
         this.retryTicks--;
      }
      if (this.calculation == null && this.retryTicks == 0
         && (next == null || stuck || this.routeChanged(bounds)
            || !this.pathFinished && this.route.getLast().distanceTo(position) < 64.0D)) {
         if (stuck) {
            this.markObstruction(pathContext, position, bounds, next);
         }
         this.calculation = pathContext.findPath(BlockPos.containing(position.add(bounds.getCenter())),
            BlockPos.containing(destination.add(bounds.getCenter())));
      }
      this.motion = this.calculateMotion(vehicle, position, bounds, next);
      this.motionTick = zioMinecraft.player.tickCount;
      return new PathingCommand(activeGoal, PathingCommandType.SET_GOAL_AND_PAUSE);
   }

   @Override
   public boolean isTemporary() {
      return true;
   }

   @Override
   public void onLostControl() {
      this.reset();
      this.goal = null;
      this.motion = Vec3.ZERO;
      this.motionTick = -1;
   }

   @Override
   public double priority() {
      return 100.0D;
   }

   @Override
   public String displayName0() {
      return "EntityControl to " + this.goal;
   }

   @Override
   public void onChunkEvent(ChunkEvent event) {
      if (this.context != null && this.context.level == zioMinecraft.level && event.isPostPopulate()) {
         this.context.loadChunk(event.getX(), event.getZ());
         this.blocksChanged = true;
      }
   }

   @Override
   public void onBlockChange(BlockChangeEvent event) {
      if (this.context != null && this.context.level == zioMinecraft.level) {
         this.context.onBlockChange(event);
         this.blocksChanged = true;
      }
   }

   private void message(String message) {
      ChatUtils.printChat(Component.literal(message), ChatUtils.moduleSignature(this.module));
   }

   private void warning(String message, ChatUtils.MessagePriority priority) {
      ChatUtils.printChat(Component.literal(message), ChatUtils.moduleSignature(this.module), priority);
   }

   private void warnUnsupported(String message) {
      if (!this.warnedUnsupported) {
         this.warning(message, ChatUtils.MessagePriority.HIGH);
      }
      this.warnedUnsupported = true;
   }

   private PathingCommand finish() {
      this.reset();
      this.motion = Vec3.ZERO;
      this.motionTick = zioMinecraft.player.tickCount;
      this.baritone.getCustomGoalProcess().onLostControl();
      return new PathingCommand(this.goal, PathingCommandType.SET_GOAL_AND_PAUSE);
   }

   private void reset() {
      if (this.calculation != null && this.context != null) {
         this.context.cancel();
      }
      this.calculation = null;
      this.route = List.of();
      this.routeIndex = 0;
      this.blocksChanged = false;
      this.blockedPositions.clear();
      this.retryTicks = 0;
      this.warnedNoRoute = false;
      this.lastPosition = null;
      this.stuckTicks = 0;
      this.lastRepathPosition = null;
      this.repeatedRepaths = 0;
   }

   private EntityControlPathContext getContext(Entity vehicle) {
      int height = isBelowNetherRoof(vehicle) ? 128 : Math.min(zioMinecraft.level.getHeight(), 128);
      if (this.context != null && (this.context.level != zioMinecraft.level || this.context.height != height)) {
         this.context.close();
         this.context = null;
         this.calculation = null;
      }
      if (this.context == null) {
         EntityControlPathContext created = new EntityControlPathContext(zioMinecraft.level, height);
         if (!created.isValid()) {
            created.close();
            return null;
         }
         this.context = created;
         this.context.loadChunks(vehicle.chunkPosition());
      }
      this.context.cullChunks(vehicle.chunkPosition());
      return this.context;
   }

   private void markObstruction(EntityControlPathContext pathContext, Vec3 position, AABB bounds, Vec3 next) {
      this.repeatedRepaths = this.lastRepathPosition != null && position.distanceTo(this.lastRepathPosition) < 8.0D
         ? this.repeatedRepaths + 1 : 1;
      this.lastRepathPosition = position;
      this.stuckTicks = 0;
      if (this.repeatedRepaths >= 2) {
         BlockPos obstruction = firstBlock(bounds, position, position.add(this.motionToTarget(position, next)),
            block -> !zioMinecraft.level.getBlockState(block).getCollisionShape(zioMinecraft.level, block).isEmpty());
         if (obstruction != null) {
            pathContext.markBlocked(obstruction, 1);
         }
      }
   }

   private boolean routeChanged(AABB bounds) {
      if (!this.blocksChanged || zioMinecraft.player.tickCount % 5 != 0) {
         return false;
      }
      this.blocksChanged = false;
      AABB contracted = bounds.deflate(0.05D);
      for (int index = Math.max(this.routeIndex - 1, 0); index + 1 < Math.min(this.route.size(), this.routeIndex + 400); index++) {
         if (!isLoaded(this.route.get(index + 1))) {
            break;
         }
         BlockPos obstruction = firstBlock(contracted, this.route.get(index), this.route.get(index + 1),
            block -> !zioMinecraft.level.getBlockState(block).isAir());
         if (obstruction != null && this.blockedPositions.add(obstruction)) {
            return true;
         }
      }
      return false;
   }

   private int nearestReachablePoint(Entity vehicle, Vec3 position) {
      List<Integer> closest = IntStream.range(0, this.route.size()).boxed()
         .sorted(Comparator.comparingDouble(index -> this.route.get(index).distanceToSqr(position))).toList();
      return closest.stream().limit(12).filter(index -> canTraverse(vehicle, position, this.route.get(index))).findFirst().orElse(closest.getFirst());
   }

   private Vec3 nextTarget(Entity vehicle, Vec3 position) {
      while (this.routeIndex < this.route.size() && isNear(this.route.get(this.routeIndex), position)) {
         this.routeIndex++;
      }
      if (this.routeIndex >= this.route.size()) {
         return null;
      }
      if (this.shortcutDelay-- <= 0) {
         this.shortcutDelay = 4;
         this.advanceTarget(vehicle, position, this.routeIndex + 1);
      }
      return this.route.get(this.routeIndex);
   }

   private boolean advanceTarget(Entity vehicle, Vec3 position, int minimum) {
      for (int index = Math.min(this.routeIndex + 8, this.route.size() - 1); index >= minimum; index--) {
         Vec3 point = this.route.get(index);
         if (isLoaded(point) && canTraverse(vehicle, position, point)) {
            this.routeIndex = index;
            return true;
         }
      }
      return false;
   }

   private boolean updateStuck(Vec3 position, boolean hasTarget) {
      this.stuckTicks = hasTarget && this.lastPosition != null && position.distanceToSqr(this.lastPosition) < 0.0025D
         ? this.stuckTicks + 1 : 0;
      this.lastPosition = position;
      return this.stuckTicks >= 20;
   }

   private Vec3 calculateMotion(Entity vehicle, Vec3 position, AABB bounds, Vec3 next) {
      if (!zioMinecraft.level.noBlockCollision(vehicle, bounds.move(position))) {
         return escapeMotion(vehicle, position, bounds);
      }
      boolean escapeHazard = vehicle.isInLava() || isHazard(bounds.move(position));
      Vec3 result = this.tryMotion(vehicle, position, bounds, next, escapeHazard);
      if (result == null && next != null && this.advanceTarget(vehicle, position, Math.max(0, this.routeIndex - 8))) {
         result = this.tryMotion(vehicle, position, bounds, this.route.get(this.routeIndex), escapeHazard);
      }
      return result == null ? Vec3.ZERO : result;
   }

   private Vec3 tryMotion(Entity vehicle, Vec3 position, AABB bounds, Vec3 next, boolean escapeHazard) {
      Vec3 desired = next == null ? Vec3.ZERO : this.motionToTarget(position, next);
      List<Vec3> candidates = new ArrayList<>(List.of(desired, desired.scale(0.5D), desired.scale(0.25D),
         new Vec3(desired.x, 0.0D, desired.z), new Vec3(0.0D, desired.y, 0.0D)));
      if (escapeHazard) {
         candidates.addFirst(new Vec3(0.0D, Mth.clamp(this.module.getEntityPathVerticalSpeed(), 0.1D, 1.0D), 0.0D));
      }
      return candidates.stream().filter(candidate -> allowsMotion(vehicle, position, bounds, candidate, escapeHazard)).findFirst().orElse(null);
   }

   private static Vec3 escapeMotion(Entity vehicle, Vec3 position, AABB bounds) {
      for (double distance : new double[]{0.25D, 0.5D, 1.0D, 2.0D}) {
         for (Direction direction : Direction.values()) {
            Vec3 motion = direction.getUnitVec3().scale(distance);
            AABB moved = bounds.move(position.add(motion));
            if (zioMinecraft.level.noBlockCollision(vehicle, moved) && !isHazard(moved)) {
               return motion;
            }
         }
      }
      return Vec3.ZERO;
   }

   private static boolean allowsMotion(Entity vehicle, Vec3 position, AABB bounds, Vec3 motion, boolean escapeHazard) {
      if (motion.lengthSqr() < 1.0E-6D) {
         return true;
      }
      if (!escapeHazard && motion.y < 0.0D && isHazard(bounds.move(position.add(motion)).expandTowards(0.0D, -2.0D, 0.0D))) {
         return false;
      }
      return firstCollision(vehicle, bounds, position, position.add(motion), !escapeHazard) == null;
   }

   private static boolean canTraverse(Entity vehicle, Vec3 from, Vec3 to) {
      return firstCollision(vehicle, relativeBounds(vehicle), from, to, true) == null;
   }

   private static BlockPos firstBlock(AABB bounds, Vec3 from, Vec3 to, Predicate<BlockPos> predicate) {
      int steps = Math.max(1, Mth.ceil(from.distanceTo(to) / 0.5D));
      for (int step = 0; step <= steps; step++) {
         AABB moved = bounds.move(from.lerp(to, (double)step / steps));
         for (BlockPos position : BlockPos.betweenClosed(BlockPos.containing(moved.minX, moved.minY, moved.minZ),
            BlockPos.containing(moved.maxX, moved.maxY, moved.maxZ))) {
            if (predicate.test(position)) {
               return position.immutable();
            }
         }
      }
      return null;
   }

   private static AABB firstCollision(Entity vehicle, AABB bounds, Vec3 from, Vec3 to, boolean avoidHazards) {
      int steps = Math.max(1, Mth.ceil(from.distanceTo(to) / 0.25D));
      for (int step = 0; step <= steps; step++) {
         AABB moved = bounds.move(from.lerp(to, (double)step / steps));
         if (!zioMinecraft.level.noBlockCollision(vehicle, moved) || avoidHazards && isHazard(moved)) {
            return moved;
         }
      }
      return null;
   }

   private Vec3 motionToTarget(Vec3 from, Vec3 to) {
      Vec3 direction = to.subtract(from);
      double horizontal = Math.hypot(direction.x, direction.z);
      double speed = this.module.getEntityPathSpeed();
      double scale = horizontal > speed ? speed / horizontal : 1.0D;
      double vertical = Math.max(this.module.getEntityPathVerticalSpeed(), 0.1D);
      if (Math.abs(direction.y) * scale > vertical) {
         scale = vertical / Math.abs(direction.y);
      }
      return direction.scale(scale);
   }

   private static List<Vec3> routePoints(List<EntityControlPathContext.Node> nodes, AABB bounds) {
      Vec3 offset = bounds.getCenter();
      List<Vec3> points = new ArrayList<>();
      AABB previous = null;
      for (EntityControlPathContext.Node node : nodes) {
         AABB box = node.bounds();
         if (previous != null) {
            addPortal(points, previous, box, bounds);
         }
         points.add(box.getCenter().subtract(offset));
         previous = box;
      }
      return points;
   }

   private static void addPortal(List<Vec3> points, AABB from, AABB to, AABB bounds) {
      Direction.Axis touching = null;
      for (Direction.Axis axis : Direction.Axis.values()) {
         double overlap = overlap(from, to, axis);
         if (overlap < -1.0E-6D) {
            return;
         }
         if (overlap < 1.0E-6D) {
            if (touching != null) {
               return;
            }
            touching = axis;
         }
      }
      if (touching == null) {
         return;
      }
      Vec3 half = new Vec3(bounds.getXsize() / 2.0D, bounds.getYsize() / 2.0D, bounds.getZsize() / 2.0D).add(0.1D);
      for (Direction.Axis axis : Direction.Axis.values()) {
         if (axis != touching && overlap(from, to, axis) < 2.0D * half.get(axis)) {
            return;
         }
      }
      double width = 2.0D * half.get(touching);
      if (from.max(touching) - from.min(touching) < width || to.max(touching) - to.min(touching) < width) {
         return;
      }
      double sign = Math.signum(to.getCenter().get(touching) - from.getCenter().get(touching));
      Vec3 center = from.intersect(to).getCenter();
      for (int side = -1; side <= 1; side += 2) {
         points.add(center.with(touching, center.get(touching) + side * sign * half.get(touching)).subtract(bounds.getCenter()));
      }
   }

   private static double overlap(AABB first, AABB second, Direction.Axis axis) {
      return Math.min(first.max(axis), second.max(axis)) - Math.max(first.min(axis), second.min(axis));
   }

   private static boolean isNear(Vec3 first, Vec3 second) {
      return Math.hypot(first.x - second.x, first.z - second.z) < 0.3D && Math.abs(first.y - second.y) < 0.3D;
   }

   private static boolean isLoaded(Vec3 position) {
      return zioMinecraft.level.getChunkSource().hasChunk(Mth.floor(position.x) >> 4, Mth.floor(position.z) >> 4);
   }

   private static boolean isHazard(AABB bounds) {
      return zioMinecraft.level.getBlockStates(bounds).anyMatch(EntityControlPathContext::isHazard);
   }

   private static boolean isBelowNetherRoof(Entity vehicle) {
      return zioMinecraft.level.dimension() == Level.NETHER && vehicle.getY() < 128.0D;
   }

   private static AABB relativeBounds(Entity vehicle) {
      return vehicle.getBoundingBox().minmax(zioMinecraft.player.getBoundingBox()).move(vehicle.position().reverse());
   }

   private static boolean isAtGoal(Goal goal, Entity vehicle, Vec3 position, Vec3 destination) {
      if (goal instanceof GoalXZ) {
         return Math.hypot(destination.x - position.x, destination.z - position.z) < 1.0D;
      }
      if (goal instanceof GoalYLevel) {
         return Math.abs(destination.y - position.y) < 0.5D;
      }
      return position.distanceTo(destination) < 1.0D || goal.isInGoal(vehicle.blockPosition());
   }

   private static Vec3 target(Goal goal, Vec3 position) {
      if (goal instanceof IGoalRenderPos rendered) {
         return rendered.getGoalPos().getBottomCenter();
      }
      if (goal instanceof GoalXZ horizontal) {
         int x = horizontal.getX();
         int z = horizontal.getZ();
         boolean surface = !zioMinecraft.level.dimensionType().hasCeiling() && zioMinecraft.level.getChunkSource().hasChunk(x >> 4, z >> 4);
         return new Vec3(x + 0.5D, surface ? zioMinecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : position.y, z + 0.5D);
      }
      if (goal instanceof GoalYLevel vertical) {
         return new Vec3(position.x, vertical.level, position.z);
      }
      if (goal instanceof GoalComposite composite) {
         Vec3 nearest = null;
         for (Goal child : composite.goals()) {
            Vec3 candidate = target(child, position);
            if (candidate != null && (nearest == null || candidate.distanceToSqr(position) < nearest.distanceToSqr(position))) {
               nearest = candidate;
            }
         }
         return nearest;
      }
      return null;
   }

   private Goal getGoal() {
      var process = this.baritone.getCustomGoalProcess();
      return process.isActive() ? process.getGoal() : null;
   }

   private Entity getVehicle() {
      if (zioMinecraft.player == null || zioMinecraft.level == null || !this.module.isEntityPathfindingEnabled()) {
         return null;
      }
      Entity vehicle = zioMinecraft.player.getVehicle();
      return vehicle != null && vehicle.getControllingPassenger() == zioMinecraft.player ? vehicle : null;
   }
}
