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
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Abilities.Packed;
import net.minecraft.world.entity.player.Input;

public class FlightModule extends Module {
   private final Setting<ModeChoice> modeSetting;
   private final Setting<Float> speedSetting;
   private final Setting<Float> verticalSetting;
   private final Setting<Float> glideSetting;
   private final Setting<Boolean> antiKickSetting;
   public final Setting<Boolean> accelerateSetting;
   public final Setting<Float> startSpeedSetting;
   public final Setting<Float> timeSetting;
   private Packed savedAbilities;
   private long accelerationStartTimeMillis;

   protected double computeFlightSpeed() {
      if (this.accelerateSetting.getValue() && this.modeSetting.getValue() != ModeChoice.JETPACK) {
         return MovementPhysics.accelerateTo(
            this.speedSetting.getValue(), this.startSpeedSetting.getValue(),
            this.timeSetting.getValue(), this.accelerationStartTimeMillis
         );
      }
      return this.speedSetting.getValue();
   }

   private void saveAbilities() {
      this.savedAbilities = ClientAccess.zioMinecraft.player.getAbilities().pack();
   }

   private void restoreAbilities() {
      if (this.savedAbilities != null) {
         ClientAccess.zioMinecraft.player.getAbilities().apply(this.savedAbilities);
         this.savedAbilities = null;
      }
   }

   @Override
   public String getInfo() {
      try {
         return FontRenderer.capitalize(this.modeSetting.getValue());
      } catch (Exception failure) {
         return null;
      }
   }

   @Override
   public void onEnable() {
      if (this.modeSetting.getValue() == FlightModule.ModeChoice.VANILLA) {
         this.saveAbilities();
      }

      this.accelerationStartTimeMillis = System.currentTimeMillis();
   }

   @Override
   public void onDisable() {
      if (!this.isWorldUnavailable()) {
         if (this.modeSetting.getValue() == FlightModule.ModeChoice.VANILLA) {
            this.restoreAbilities();
         }
      }
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      FlightModule.ModeChoice mode = this.modeSetting.getValue();
      mode.handleSendPosition(this, event);
   }

   @EventHandler
   private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
      if (!MovementPhysics.isLocalPlayerMoving()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
      }

      FlightModule.ModeChoice mode = this.modeSetting.getValue();
      mode.handleMovement(this, event);
      if (this.antiKickSetting.getValue()) {
         if (ClientAccess.zioMinecraft.player.onGround()) {
            return;
         }
         double verticalOffset = 0.04D;
         int playerTicks = ClientAccess.zioMinecraft.player.tickCount;

         if (playerTicks % 2 == 0) {
            verticalOffset = -verticalOffset;
         }

         double eventY = event.getY() + verticalOffset;
         event.setY(eventY);
         ClientAccess.zioMinecraft.player.setDeltaMovement(
            ClientAccess.zioMinecraft.player.getDeltaMovement().add(0.0, verticalOffset, 0.0)
         );
      }
   }

   @EventHandler
   public void onPacketReceive(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
      }
   }

   public FlightModule() {
      super("Flight", "Allows you to fly using magic.", ModuleCategory.MOVEMENT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.VANILLA));
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.1F, 10.0F));
      this.verticalSetting = this.add(new NumberSetting<>("Vertical", 1.0F, 0.0F, 5.0F));
      this.glideSetting = this.add(new NumberSetting<>("Glide", 0.0F, 0.0F, 5.0F));
      this.antiKickSetting = this.add(new BooleanSetting("AntiKick", false));
      this.accelerateSetting = this.add(new BooleanSetting("Accelerate", false).markVisibilityDependency());
      this.startSpeedSetting = this.add(new NumberSetting<>("StartSpeed", 1.0F, 0.0F, 5.0F).dependsOn(this.accelerateSetting));
      this.timeSetting = this.add(new NumberSetting<>("Time", 1.0F, 0.1F, 10.0F).withUnit("s").dependsOn(this.accelerateSetting));
      this.verticalSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() == ModeChoice.STATIC);
      this.glideSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() == ModeChoice.STATIC);
      this.antiKickSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() == ModeChoice.STATIC);
      this.accelerateSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() != ModeChoice.JETPACK);
      this.startSpeedSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() != ModeChoice.JETPACK);
      this.timeSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() != ModeChoice.JETPACK);
      this.antiKickSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() != ModeChoice.JETPACK);
      this.speedSetting.disableNumericBounds();
      this.verticalSetting.disableNumericBounds();
      this.modeSetting.setChangeCallback(() -> {
         if (this.isToggled()) {
            if (this.modeSetting.getValue() == ModeChoice.VANILLA) {
               this.saveAbilities();
            } else {
               this.restoreAbilities();
            }
         }
      });
   }

   public enum ModeChoice implements NamedEntry {
      VANILLA("Vanilla") {
         @Override
         public void handleMovement(
            FlightModule module, LocalPlayerMoveEvent event
         ) {
            ClientAccess.zioMinecraft.player.getAbilities().flying = true;
            Abilities abilities = ClientAccess.zioMinecraft.player.getAbilities();
            float flyingSpeed = (float)(module.computeFlightSpeed() * 0.05F);
            abilities.setFlyingSpeed(flyingSpeed);
         }
      },
      STATIC("Static") {
         private int jumpTicks;

         @Override
         public void handleMovement(FlightModule module, LocalPlayerMoveEvent event) {
            double verticalSpeed = module.verticalSetting.getValue();
            LocalPlayer player = ClientAccess.zioMinecraft.player;
            if (player.input.keyPresses.jump()) {
               this.jumpTicks++;
               if (module.antiKickSetting.getValue()) {
                  if (this.jumpTicks >= 17) {
                     verticalSpeed = -0.15;
                  }
                  if (this.jumpTicks >= 21) {
                     this.jumpTicks = 0;
                  }
               }
               event.setY(verticalSpeed);
               player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, verticalSpeed));
            } else if (player.input.keyPresses.shift()) {
               event.setY(-verticalSpeed);
               player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, -verticalSpeed));
            } else {
               float idleY = (float)(player.onGround() ? player.getDeltaMovement().y : 0.0);
               event.setY(idleY);
               player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, idleY));
               if (!player.verticalCollision && module.glideSetting.getValue() != 0.0F) {
                  player.setDeltaMovement(player.getDeltaMovement().add(0.0, -module.glideSetting.getValue(), 0.0));
                  event.setY(player.getDeltaMovement().y);
               }
            }
            double[] horizontal = MovementPhysics.applyHorizontalMotion(event, module.computeFlightSpeed());
            player.setDeltaMovement(horizontal[0], player.getDeltaMovement().y, horizontal[1]);
         }
      },
      JETPACK("JetPack") {
         @Override
         public void handleSendPosition(
            FlightModule module, LocalPlayerSendPositionEvent event
         ) {
            Input jumpInput = ClientAccess.zioMinecraft.player.input.keyPresses;
            if (jumpInput.jump()) {
               ClientAccess.zioMinecraft.player.setDeltaMovement(ClientAccess.zioMinecraft.player.getDeltaMovement().add(0.0, module.computeFlightSpeed() * 0.1, 0.0));
            }
         }
      },
      SOURCE("Source") {
         @Override
         public void handleMovement(
            FlightModule module, LocalPlayerMoveEvent event
         ) {
            double speed = module.computeFlightSpeed();
            MovementPhysics.applyHorizontalMotion(event, speed);

            if (MovementPhysics.isLocalPlayerMoving()) {
               float forwardAmount = ClientAccess.zioMinecraft.player.input.moveVector.y;
               double forwardDirection = -Math.signum(forwardAmount);
               double pitchDegrees = ClientAccess.zioMinecraft.player.getXRot();
               double pitchRadians = Math.toRadians(pitchDegrees);
               double verticalVelocity = forwardDirection * Math.sin(pitchRadians) * speed;
               event.setY(verticalVelocity);
            } else {
               event.setY(0.0);
            }
         }
      };

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }

      public void handleSendPosition(
         FlightModule module, LocalPlayerSendPositionEvent event
      ) {
      }

      public void handleMovement(
         FlightModule module, LocalPlayerMoveEvent event
      ) {
      }
   }
}
