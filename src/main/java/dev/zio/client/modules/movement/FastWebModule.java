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
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerAiStepEvent;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.utils.network.PacketSender;
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
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class FastWebModule extends Module {
   private static AntiCheatModule antiCheatModule;
   final Setting<ModeChoice> modeSetting;
   private final Setting<Boolean> horizontalSetting;
   private final Setting<Float> speedSetting;
   private final Setting<Boolean> verticalSetting;
   private final Setting<Float> vSpeedSetting;
   private final Setting<Boolean> upwardsSetting;
   private boolean usingStrictTimer;

   public FastWebModule() {
      super("FastWeb", "Makes you faster in cobwebs.", ModuleCategory.MOVEMENT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", FastWebModule.ModeChoice.PLAIN));
      Predicate<Object> showMovement = ignored -> this.modeSetting.getValue() != FastWebModule.ModeChoice.GRIM;
      this.horizontalSetting = this.add(new BooleanSetting("Horizontal", false, showMovement).markVisibilityDependency());
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 1.0F, 2.0F, showMovement).dependsOn(this.horizontalSetting));
      this.verticalSetting = this.add(new BooleanSetting("Vertical", false, showMovement).markVisibilityDependency());
      this.vSpeedSetting = this.add(new NumberSetting<>("VSpeed", 1.0F, 1.0F, 2.0F, showMovement).dependsOn(this.verticalSetting));
      Predicate<Object> showUpwards = ignored -> this.modeSetting.getValue() == FastWebModule.ModeChoice.PLAIN;
      this.upwardsSetting = this.add(new BooleanSetting("Upwards", false, showUpwards).dependsOn(this.verticalSetting));
      this.usingStrictTimer = false;
      this.setDrawn(false);
   }

   public void reset() {
      ClientContext.playerTickController.releaseMultiplier(this);
      this.usingStrictTimer = false;
   }

   public boolean isUsingStrictTimer() {
      return this.usingStrictTimer;
   }

   private boolean isStrictMode() {
      return this.modeSetting.getValue() == FastWebModule.ModeChoice.STRICT;
   }

   public boolean isGrimEnabled() {
      if (!this.isToggled()) {
         return false;
      }
      return this.modeSetting.getValue() == FastWebModule.ModeChoice.GRIM;
   }

   @EventHandler(priority = -100)
   private void onAiStep(LocalPlayerAiStepEvent event) {
      if (!this.isGrimEnabled()) {
         return;
      }
      if (!ClientAccess.zioMinecraft.player.input.keyPresses.jump()) {
         return;
      }
      if (PlayerStateUtils.isInCobweb(ClientAccess.zioMinecraft.player)) {
         ClientAccess.zioMinecraft.player.setSprinting(false);
      }
   }

   @EventHandler
   private void onMove(LocalPlayerMoveEvent event) {
      if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
         this.reset();
         return;
      }
      if (!PlayerStateUtils.isInCobweb(ClientAccess.zioMinecraft.player)) {
         this.reset();
         return;
      }
      if (this.modeSetting.getValue() == FastWebModule.ModeChoice.GRIM) {
         this.reset();
         return;
      }
      float horizontalMultiplier = this.speedSetting.getValue();
      if (this.verticalSetting.getValue()) {
         horizontalMultiplier = this.applyVerticalDescent(event, horizontalMultiplier);
         if (this.upwardsSetting.getValue()) {
            Input jumpKeys = ClientAccess.zioMinecraft.player
               .input
               .keyPresses;
            if (jumpKeys.jump()) {
               if (!this.isStrictMode()) {
                  LocalPlayer ascentPlayer = ClientAccess.zioMinecraft.player;
                  Vec3 ascentVelocity = ascentPlayer.getDeltaMovement();
                  Axis ascentAxis = Axis.Y;
                  double upwardVelocity = this.vSpeedSetting.getValue();
                  Vec3 updatedAscentVelocity = ascentVelocity.with(ascentAxis, upwardVelocity);
                  ascentPlayer.setDeltaMovement(updatedAscentVelocity);
                  double upwardEventY = this.vSpeedSetting.getValue();
                  event.setY(upwardEventY);
               }
            }
         }
      }
      if (MovementPhysics.isLocalPlayerMoving()) {
         if (this.horizontalSetting.getValue()) {
            boolean includeEffects = true;
            double horizontalSpeed = MovementPhysics.getBaseMoveSpeed(
                  includeEffects
               )
               * horizontalMultiplier;
            LocalPlayerMoveEvent horizontalEvent = event;
            double[] horizontalMotion = MovementPhysics.applyHorizontalMotion(
               horizontalEvent, horizontalSpeed
            );
            LocalPlayer horizontalReceiverSnapshot = ClientAccess.zioMinecraft.player;
            double horizontalXSnapshot = horizontalMotion[0];
            LocalPlayer yVelocityPlayer = ClientAccess.zioMinecraft.player;
            double horizontalZ = horizontalMotion[1];
            double preservedY = yVelocityPlayer.getDeltaMovement().y;
            horizontalReceiverSnapshot.setDeltaMovement(horizontalXSnapshot, preservedY, horizontalZ);
         }
      }
   }

   private float applyVerticalDescent(LocalPlayerMoveEvent event, float horizontalMultiplier) {
      if (!ClientAccess.zioMinecraft.player.input.keyPresses.shift() || ClientAccess.zioMinecraft.player.onGround()) {
         this.reset();
         return horizontalMultiplier;
      }
      if (this.isStrictMode()) {
         this.usingStrictTimer = true;
         ClientContext.playerTickController.requestMultiplier(this, this.vSpeedSetting.getValue() * 6.0F);
         return horizontalMultiplier;
      }
      this.reset();
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, -this.vSpeedSetting.getValue()));
      event.setY(-this.vSpeedSetting.getValue());
      return Math.min(horizontalMultiplier, 1.2F);
   }

   @EventHandler
   private void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (EventStage.Pre == event.getStage()) {
         Vec3 displacement = new Vec3(
            currentXDisplacement(),
            ClientAccess.zioMinecraft.player.getY() - ClientAccess.zioMinecraft.player.yo,
            ClientAccess.zioMinecraft.player.getZ() - ClientAccess.zioMinecraft.player.zo
         );
         if (this.isGrimEnabled()) {
            if (displacement.lengthSqr() > 0.0) {
               AABB bounds = ClientAccess.zioMinecraft.player.getBoundingBox();
               BlockPos.betweenClosedStream(bounds)
                  .map(BlockPos::immutable)
                  .filter(webPos -> ClientAccess.zioMinecraft.level.getBlockState(webPos).is(Blocks.COBWEB))
                  .forEach(webPos -> {
                     if (!antiCheatModule.is2b2tEnabled()) {
                        PacketSender.sendPlayerAction(Action.ABORT_DESTROY_BLOCK, webPos, Direction.UP);
                     }
                     PacketSender.sendPlayerAction(Action.STOP_DESTROY_BLOCK, webPos, Direction.UP);
                  });
            }
         }
         if (this.modeSetting.getValue() == FastWebModule.ModeChoice.GRIM) {
            if (ClientAccess.zioMinecraft.player.input.keyPresses.shift()) {
               if (!ClientAccess.zioMinecraft.player.onGround()) {
                  if (PlayerStateUtils.isInCobweb(ClientAccess.zioMinecraft.player)) {
                     double playerY = ClientAccess.zioMinecraft.player.getY();
                     double randomizedY = playerY + Math.random();
                     double spoofedY = randomizedY + 1.0D;
                     event.setY(spoofedY);
                  }
               }
            }
         }
      }
   }
   private static double currentXDisplacement() {
      return ClientAccess.zioMinecraft.player.getX() - ClientAccess.zioMinecraft.player.xo;
   }

   public static void bindModules(ModuleRegistry registry) {
      antiCheatModule = (AntiCheatModule)registry.getModule(AntiCheatModule.class);
   }

   public enum ModeChoice implements NamedEntry {
      PLAIN("Plain"),
      STRICT("Strict"),
      LAGBACK("LagBack"),
      GRIM("Grim");
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
