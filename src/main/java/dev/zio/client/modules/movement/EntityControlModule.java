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
import dev.zio.client.events.VehicleMoveEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MinecraftDisconnectEvent;
import dev.zio.client.gui.screens.ClientScreen;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.phys.Vec3;

public class EntityControlModule extends Module {
   private final Setting<Boolean> copyYawSetting;
   public final Setting<Boolean> horseJumpSetting;
   private final Setting<Boolean> grimOverflowSetting;
   final Setting<Boolean> entitySpeedSetting;
   private final Setting<Double> speedSetting;
   private final Setting<Boolean> noCollisionSetting;
   private final Setting<Boolean> pathFinderSetting;
   private final Setting<Boolean> glideSetting;
   private final Setting<Boolean> accelerateSetting;
   private final Setting<Boolean> flightSetting;
   private final Setting<Float> verticalSetting;
   private boolean useNegativeFlightJitter;
   private long accelerationStartMillis;
   private volatile boolean grimOverflowActive;
   private final Supplier<Vec3> pathfinderMotion;

   public EntityControlModule() {
      super("EntityControl", "Helps with riding entities.", ModuleCategory.MOVEMENT, new String[0]);
      this.copyYawSetting = this.add(new BooleanSetting("CopyYaw", false));
      this.horseJumpSetting = this.add(new BooleanSetting("HorseJump", true));
      this.grimOverflowSetting = this.add(new BooleanSetting("GrimOverflow", false));
      this.entitySpeedSetting = this.add(new BooleanSetting("EntitySpeed", true).markVisibilityDependency());
      Predicate<Object> speedVisibility = ignored -> this.entitySpeedSetting.isExpanded();
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0, 0.1, 20.0, speedVisibility));
      Predicate<Object> noCollisionVisibility = ignored -> this.entitySpeedSetting.isExpanded();
      this.noCollisionSetting = this.add(new BooleanSetting("NoCollision", true, noCollisionVisibility));
      this.pathFinderSetting = this.add(new BooleanSetting("Baritone", false).dependsOn(this.entitySpeedSetting));
      this.pathFinderSetting.hideWithoutBaritone();
      Predicate<Object> glideVisibility = ignored -> this.entitySpeedSetting.isExpanded();
      this.glideSetting = this.add(new BooleanSetting("Glide", false, glideVisibility));
      Predicate<Object> accelerateVisibility = ignored -> this.entitySpeedSetting.isExpanded();
      this.accelerateSetting = this.add(new BooleanSetting("Accelerate", false, accelerateVisibility));
      Predicate<Object> flightVisibility = ignored -> this.entitySpeedSetting.isExpanded();
      this.flightSetting = this.add(new BooleanSetting("Flight", false, flightVisibility).markVisibilityDependency());
      this.verticalSetting = this.add(new NumberSetting<>("Vertical", 10.0F, 0.0F, 20.0F).dependsOn(this.flightSetting, this.entitySpeedSetting));
      this.speedSetting.setChangeCallback(() -> this.accelerationStartMillis = System.currentTimeMillis());
      this.pathfinderMotion = ClientContext.baritoneBridge.isAvailable() ? new EntityControlPathfinder(this)::getMotion : null;
   }

   @Override
   public void onDisable() {
      this.grimOverflowActive = false;
      if (!this.isWorldUnavailable() && ClientAccess.zioMinecraft.player.getVehicle() instanceof Boat boat) {
         boat.setNoGravity(false);
      }
   }

   @EventHandler
   private void onVehicleMoveEvent(VehicleMoveEvent event) {
      if (this.isWorldUnavailable()) {
         return;
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      Entity vehicle = player.getVehicle();
      if (vehicle == null) {
         return;
      }
      if (!MovementPhysics.isLocalPlayerMoving()) {
         this.accelerationStartMillis = System.currentTimeMillis();
      }
      if (this.copyYawSetting.getValue()) {
         vehicle.setYRot(player.getViewYRot(RenderUtils.getPartialTick()));
      }
      if (vehicle instanceof Mob mob && !mob.isSaddled()) {
         return;
      }
      double speed = this.speedSetting.getValue();
      double verticalSpeed = this.verticalSetting.getValue();
      if (speed >= 20.0D) {
         speed = 19.99D;
      }
      if (this.accelerateSetting.getValue()) {
         speed = MovementPhysics.accelerateTo(
            this.speedSetting.getValue(), 0.1D, this.speedSetting.getValue(), this.accelerationStartMillis
         );
      }
      Vec3 pathMotion = this.pathfinderMotion == null ? null : this.pathfinderMotion.get();
      if (!this.entitySpeedSetting.getValue() || pathMotion == null && this.shouldPauseVehicleSpeed()) {
         return;
      }
      double[] horizontalVelocity = pathMotion == null
         ? MovementPhysics.getHorizontalMotion(player.getViewYRot(RenderUtils.getPartialTick()), player.input, speed)
         : new double[]{pathMotion.x, pathMotion.z};
      double velocityY;
      if (this.flightSetting.getValue()) {
         if (vehicle instanceof Boat boat) {
            boat.setNoGravity(true);
         }
         boolean descend = KeyUtils.isPhysicallyDown(ClientAccess.zioMinecraft.options.keySprint);
         if (ClientAccess.zioMinecraft.screen instanceof ChatScreen || ClientAccess.zioMinecraft.screen instanceof ClientScreen) {
            descend = false;
         }
         if (pathMotion != null) {
            velocityY = Math.abs(pathMotion.y) > (double)0.04125F
               ? pathMotion.y : this.useNegativeFlightJitter ? (double)-0.04125F : (double)0.04125F;
         } else if (ClientAccess.zioMinecraft.options.keyJump.isDown()) {
            velocityY = verticalSpeed;
         } else if (this.glideSetting.getValue() || descend) {
            velocityY = -verticalSpeed;
         } else {
            velocityY = this.useNegativeFlightJitter ? (double)-0.04125F : (double)0.04125F;
         }
      } else {
         velocityY = event.getY();
      }
      event.setVelocity(new Vec3(horizontalVelocity[0], velocityY, horizontalVelocity[1]));
      this.useNegativeFlightJitter = !this.useNegativeFlightJitter;
   }

   boolean isEntityPathfindingEnabled() {
      return this.isToggled() && this.pathFinderSetting.getValue() && this.entitySpeedSetting.getValue() && this.flightSetting.getValue();
   }

   double getEntityPathSpeed() {
      return Math.min(this.speedSetting.getValue(), 19.99D);
   }

   double getEntityPathVerticalSpeed() {
      return this.verticalSetting.getValue();
   }

   private boolean shouldPauseVehicleSpeed() {
      Entity vehicle = ClientAccess.zioMinecraft.player.getVehicle();
      return vehicle != null && vehicle.horizontalCollision && this.noCollisionSetting.getValue();
   }

   @EventHandler
   private void updateGrimOverflow(LocalPlayerPreTickEvent event) {
      Entity vehicle = ClientAccess.zioMinecraft.player.getVehicle();
      this.grimOverflowActive = this.grimOverflowSetting.getValue()
         && vehicle instanceof AbstractHorse && this.isControlledByLocalPlayer(vehicle);
   }

   private boolean isControlledByLocalPlayer(Entity entity) {
      if (entity == null) {
         return false;
      }
      return entity.getControllingPassenger() == ClientAccess.zioMinecraft.player;
   }

   @EventHandler
   private void resetGrimOverflow(MinecraftDisconnectEvent event) {
      this.grimOverflowActive = false;
   }

   public boolean isGrimOverflowActive() {
      return this.grimOverflowActive;
   }

   @EventHandler
   public void onPacketReceive(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.accelerationStartMillis = System.currentTimeMillis();
      }
   }

}
