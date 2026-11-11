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
import dev.zio.client.events.ClientPacketListenerHandleExplosionEvent;
import dev.zio.client.events.LivingEntityRotationEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LivingEntityJumpFromGroundEvent;
import dev.zio.client.events.LocalPlayerAiStepEvent;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerMoveTowardsClosestSpaceEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.utils.movement.StrafeSpeedHandler;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.utils.movement.GrimSpeedHandler;
import dev.zio.client.events.StepHeightEvent;
import dev.zio.client.utils.combat.DamageUtils;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.movement.StrictStrafeSpeedHandler;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SpeedModule extends Module {
   private static ObstaclePasserModule obstaclePasserModule;
   public final Setting<ModeChoice> modeSetting;
   public final Setting<Float> speedSetting;
   public final Setting<Boolean> boostSetting;
   public final Setting<Boolean> inLiquidSetting;
   public final Setting<Boolean> useTimerSetting;
   public final Setting<Boolean> pauseInBlocksSetting;
   private boolean skipNextMoveAfterStep;
   public final ElapsedTimer explosionBoostTimer;
   private final EnumSelectionMap<SpeedModeHandler> modeHandlers;

   public void reset() {
      SpeedModeHandler handler = this.modeHandlers.getSelected();
      handler.lastHorizontalDistance = 0.0;
      handler.moveSpeed = 0.0;
      handler.movementStage = this.modeSetting.getValue() == ModeChoice.OnGround ? 2 : 4;
   }

   @Override
   public String getInfo() {
      ModeChoice selectedMode = this.modeSetting.getValue();
      return selectedMode.getName();
   }

   @Override
   public void onEnable() {
      this.modeHandlers.getSelected().onEnable();
   }

   @EventHandler(priority = 100)
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (ClientContext.serverStateManager.getTeleportAckTimer().hasElapsed(10L)) {
         this.modeHandlers.getSelected().onSendPosition(event);
      }
   }

   @EventHandler
   private void onLocalPlayerAiStepEvent(LocalPlayerAiStepEvent event) {
      if (this.modeSetting.getValue() == ModeChoice.StrafeStrict && !this.shouldPauseSpeed()) {
         ClientAccess.zioMinecraft.player.setSprinting(true);
      }
   }

   @EventHandler
   private void onStepHeight(StepHeightEvent event) {
      if (EventStage.Post == event.getStage() && ClientAccess.zioMinecraft.player.onGround() && event.getHeight() > 0.6D) {
         this.skipNextMoveAfterStep = true;
      }
   }

   @EventHandler
   private void onLocalPlayerMoveTowardsClosestSpaceEvent(LocalPlayerMoveTowardsClosestSpaceEvent event) {
      if (this.modeSetting.getValue() == ModeChoice.Grim) {
         event.cancel();
      }
   }

   @EventHandler
   private void onClientPacketListenerHandleExplosionEvent(ClientPacketListenerHandleExplosionEvent event) {
      Vec3 explosionPosition = new Vec3(event.getCenterX(), event.getCenterY(), event.getCenterZ());
      double explosionDamage = DamageUtils.getCrystalDamage(explosionPosition, ClientAccess.zioMinecraft.player);

      if (!MovementPhysics.isLocalPlayerMoving()) {
         return;
      }
      if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
         return;
      }
      if (!(this.boostSetting.getValue())) {
         return;
      }
      double explosionDistance = Math.sqrt(ClientAccess.zioMinecraft.player.distanceToSqr(explosionPosition));
      if (explosionDistance > 6.0D || explosionDamage < 4.0D) {
         return;
      }
      this.explosionBoostTimer.reset();
   }

   @EventHandler
   private void onLivingEntityJumpFromGroundEvent(LivingEntityJumpFromGroundEvent event) {
      if (this.modeSetting.getValue() == ModeChoice.StrafeStrict
         && MovementPhysics.isLocalPlayerMoving() && !this.shouldPauseSpeed()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
      if (!ClientContext.serverStateManager.getTeleportAckTimer().hasElapsed(10L)) {
         return;
      }
      if (this.skipNextMoveAfterStep) {
         this.skipNextMoveAfterStep = false;
      } else {
         this.modeHandlers.getSelected().onMove(event);
      }
   }

   @EventHandler
   private void onRotationEvent(LivingEntityRotationEvent event) {
      if (!event.isJump()) {
         this.modeHandlers.getSelected().onRotationUpdate();
      }
   }

   public boolean shouldYieldToObstaclePasser() {
      return obstaclePasserModule != null && obstaclePasserModule.isPassingObstacles();
   }

   public boolean shouldPauseInBlocks() {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      AABB playerBounds = player.getBoundingBox();
      BlockPos position = player.blockPosition();
      AABB column = new AABB(position.getX(), playerBounds.minY, position.getZ(),
         position.getX() + 1.0D, playerBounds.maxY, position.getZ() + 1.0D).deflate(1e-7D);
      return ClientAccess.zioMinecraft.level.collidesWithSuffocatingBlock(player, column)
         && this.pauseInBlocksSetting.getValue();
   }

   public boolean shouldPauseInLiquid() {
      return PlayerStateUtils.isInLiquid(ClientAccess.zioMinecraft.player) && !this.inLiquidSetting.getValue();
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.reset();
      }
   }

   public boolean shouldPauseSpeed() {
      return this.isWorldUnavailable() || this.shouldPauseInBlocks() || this.shouldPauseInLiquid()
         || PlayerStateUtils.isHungerLimited() || ClientAccess.zioMinecraft.player.isFallFlying()
         || ClientAccess.zioMinecraft.player.isSpectator() || ClientContext.baritoneBridge.isPathingOrCalculating();
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      obstaclePasserModule = (ObstaclePasserModule)registry.getModule(
         ObstaclePasserModule.class
      );
      StrafeSpeedHandler.bindModules(registry);
      StrictStrafeSpeedHandler.bindModules(registry);
      GrimSpeedHandler.bindModules(registry);
   }

   public SpeedModule() {
      super("Speed", "Makes you move faster.", ModuleCategory.MOVEMENT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.Strafe));
      Predicate<Object> speedVisibility = ignored -> this.modeSetting.getValue() != ModeChoice.StrafeStrict
         && this.modeSetting.getValue() != ModeChoice.Grim;
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.0F, 10.0F, speedVisibility));
      Predicate<Object> boostVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.StrafeStrict
         || this.modeSetting.getValue() == ModeChoice.Strafe;
      this.boostSetting = this.add(new BooleanSetting("Boost", false, boostVisibility));
      Predicate<Object> liquidVisibility = ignored -> this.modeSetting.getValue() != ModeChoice.Grim;
      this.inLiquidSetting = this.add(new BooleanSetting("InLiquid", false, liquidVisibility));
      Predicate<Object> timerVisibility = ignored -> this.modeSetting.getValue() != ModeChoice.Grim;
      this.useTimerSetting = this.add(new BooleanSetting("UseTimer", true, timerVisibility));
      Predicate<Object> blocksVisibility = ignored -> this.modeSetting.getValue() != ModeChoice.Grim;
      this.pauseInBlocksSetting = this.add(new BooleanSetting("PauseInBlocks", true, blocksVisibility));
      this.explosionBoostTimer = new ElapsedTimer();
      this.modeHandlers = new EnumSelectionMap<>(this.modeSetting);
      this.modeHandlers.register(ModeChoice.Strafe, new StrafeSpeedHandler(this));
      this.modeHandlers.register(ModeChoice.StrafeStrict, new StrictStrafeSpeedHandler(this));
      this.modeHandlers.register(ModeChoice.Vanilla, new VanillaSpeedHandler(this));
      this.modeHandlers.register(ModeChoice.OnGround, new OnGroundSpeedHandler(this));
      this.modeHandlers.register(ModeChoice.Grim, new GrimSpeedHandler(this));
   }

   public enum ModeChoice implements NamedEntry {
      Strafe,
      StrafeStrict,
      Vanilla,
      OnGround,
      Grim;

      @Override
      public String getName() {
         return this.name();
      }
   }
}
