/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;


import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.network.InteractPacketUtils;
import dev.zio.client.utils.movement.WallsVelocityHandler;
import dev.zio.client.utils.inventory.ItemUtils;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.Setting;

import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CriticalsModule extends Module {
   private static AuraModule auraModule;
   private final Setting<ModeChoice> modeSetting;
   private final Setting<Boolean> pauseOnMoveSetting;
   private final Setting<Boolean> onlyFullSetting;
   private final Setting<Boolean> onlyWeaponSetting;
   private boolean pendingPositionCorrection;

   private boolean isCriticalWeapon(ItemStack stack) {
      Item mace = Items.MACE;

      if (stack.is(mace)) {
         return true;
      }
      return ItemUtils.isSword(stack) || ItemUtils.isAxe(stack) || stack.is(ItemTags.PICKAXES);
   }

   private float clampPitch(float pitch) {
      float lowerBound = -MathConstants.NINETY_DEGREES;
      float upperBound = MathConstants.NINETY_DEGREES;
      return Math.clamp(pitch, lowerBound, upperBound);
   }

   static {
      auraModule = (AuraModule)ClientContext.getModule(AuraModule.class);
   }

   @Override
   public String getInfo() {
      return (this.modeSetting.getValue()).getName();
   }

   protected boolean canPerformCritical() {
      if (this.onlyWeaponSetting.getValue()) {
         ItemStack heldStack = ClientAccess.zioMinecraft.player.getMainHandItem();
         if (!this.isCriticalWeapon(heldStack)) {
            return false;
         }
      }

      movementGate: {
         if (!this.pauseOnMoveSetting.getValue()) {
            if (this.modeSetting.getValue() != CriticalsModule.ModeChoice.GRIMV3) {
               break movementGate;
            }
         }
         if (MovementPhysics.isLocalPlayerMoving()) {
            return false;
         }
      }

      if (this.modeSetting.getValue() == CriticalsModule.ModeChoice.GRIMV3) {
         if (this.onlyFullSetting.getValue()) {
            double eyeY = ClientAccess.zioMinecraft.player.getEyeY();
            if (!WallsVelocityHandler.hasPlayerCollisionAtHeight(eyeY)) {
               return false;
            }
         }

         if (ClientAccess.zioMinecraft.player.onGround()) {
            if (WallsVelocityHandler.hasUsableGroundCollision()) {
               return true;
            }
         }
         return false;
      } else {
         if (this.modeSetting.getValue() == CriticalsModule.ModeChoice.GRIM) {
            if (!ClientAccess.zioMinecraft.player.onGround()) {
               return true;
            }
         }

         if (ClientAccess.zioMinecraft.player.onGround() && ClientAccess.zioMinecraft.player.verticalCollision) {
            if (!ClientAccess.zioMinecraft.player.isInLava()) {
               if (!ClientAccess.zioMinecraft.player.isInWater()) {
                  return true;
               }
            }
         }
         return false;
      }
   }

   public CriticalsModule.ModeChoice getMode() {
      return this.modeSetting.getValue();
   }

   @EventHandler
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent ignoredEvent) {
      float[] rotations = new float[2];
      LocalPlayer yawSource = ClientAccess.zioMinecraft.player;

      rotations[0] = yawSource.getYRot();
      LocalPlayer pitchSource = ClientAccess.zioMinecraft.player;
      rotations[1] = pitchSource.getXRot();

      if (auraModule.getTarget() != null) {
         if (auraModule.isToggled()) {
            rotations = RotationManager.getRotationsToEntity(auraModule.getTarget());
         }
      }

      if (this.pendingPositionCorrection) {
         double playerX = ClientAccess.zioMinecraft.player.getX();
         double x = playerX + 9.999999747378752e-05D;

         double y = ClientAccess.zioMinecraft.player.getY();

         double z = ClientAccess.zioMinecraft.player.getZ();
         float yaw = rotations[0];
         float pitch = rotations[1];
         float pitchAdjustment = AntiCheatModule.ROTATION_JITTER_STEP;
         pitch -= pitchAdjustment * 2.0F;

         float clampedPitch = this.clampPitch(pitch);

         PacketSender.sendPositionRotation(x, y, z, yaw, clampedPitch, true);
         this.pendingPositionCorrection = false;
      }
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      // 26.1.2: el ATTACK viaja en ServerboundAttackPacket dedicado; el record
      // interact solo cubre interact/interact-at.
      if (event.getPacket() instanceof ServerboundAttackPacket attackPacket) {
         this.handleAttackForCritical(InteractPacketUtils.getEntity(attackPacket.entityId()), event);
      } else if (event.getPacket() instanceof ServerboundInteractPacket packet
         && InteractPacketUtils.getAction(packet) == InteractPacketUtils.Action.ATTACK) {
         this.handleAttackForCritical(InteractPacketUtils.getEntity(packet), event);
      }
   }

   private void handleAttackForCritical(Entity target, PacketEvent.Send event) {
      if (this.shouldApplyCritical(target)) {
         if (target == null) {
            return;
         }
         if (!event.isCancelled()) {
            this.performCritical();
         }
         ClientAccess.zioMinecraft.execute(() -> {
            ClientAccess.zioMinecraft.player.crit(target);
         });
      }
   }

   protected void performCritical() {
      float[] rotations = new float[2];
      LocalPlayer yawSource = ClientAccess.zioMinecraft.player;

      rotations[0] = yawSource.getYRot();
      LocalPlayer pitchSource = ClientAccess.zioMinecraft.player;
      rotations[1] = pitchSource.getXRot();

      if (auraModule.getTarget() != null) {
         if (auraModule.isToggled()) {
            rotations = RotationManager.getRotationsToEntity(auraModule.getTarget());
         }
      }

      switch ((this.modeSetting.getValue())) {
         case NCP:
            LocalPlayer ncpPlayer = ClientAccess.zioMinecraft.player;

            if (PlayerStateUtils.isInCobweb(ncpPlayer)) {
               double[] offsets = new double[3];
               offsets[0] = 1.1e-7F;
               offsets[1] = 1.1e-8F;
               offsets[2] = 0.0D;
               this.sendPositionOffsets(offsets);
            } else {
               double[] offsets = new double[2];
               offsets[0] = 1.1e-7F;
               offsets[1] = 1.1e-8F;
               this.sendPositionOffsets(offsets);
            }
            break;
         case GRIM:
            LocalPlayer grimXSource = ClientAccess.zioMinecraft.player;
            double grimX = grimXSource.getX();
            LocalPlayer grimYSource = ClientAccess.zioMinecraft.player;
            double grimPlayerY = grimYSource.getY();
            double grimY = grimPlayerY - 1e-06D;
            LocalPlayer grimZSource = ClientAccess.zioMinecraft.player;
            double grimZ = grimZSource.getZ();
            float grimYaw = rotations[0];
            float grimPitch = rotations[1];
            boolean grimOnGround = false;
            PacketSender.sendPositionRotation(grimX, grimY, grimZ, grimYaw, grimPitch, grimOnGround);
            break;
         case GRIMV3:
            LocalPlayer firstXSource = ClientAccess.zioMinecraft.player;
            double firstX = firstXSource.getX();
            LocalPlayer firstYSource = ClientAccess.zioMinecraft.player;
            double firstPlayerY = firstYSource.getY();
            double firstY = firstPlayerY + 0.0626D;
            LocalPlayer firstZSource = ClientAccess.zioMinecraft.player;
            double firstZ = firstZSource.getZ();
            float firstYaw = rotations[0];
            float firstPitch = rotations[1] + AntiCheatModule.ROTATION_JITTER_STEP;
            float firstClampedPitch = this.clampPitch(firstPitch);
            boolean firstOnGround = false;
            PacketSender.sendPositionRotation(firstX, firstY, firstZ, firstYaw, firstClampedPitch, firstOnGround);

            LocalPlayer secondXSource = ClientAccess.zioMinecraft.player;
            double secondX = secondXSource.getX();
            LocalPlayer secondYSource = ClientAccess.zioMinecraft.player;
            double secondPlayerY = secondYSource.getY();
            double secondY = secondPlayerY + 0.0455D;
            LocalPlayer secondZSource = ClientAccess.zioMinecraft.player;
            double secondZ = secondZSource.getZ();
            float secondYaw = rotations[0];
            float secondPitch = rotations[1] - AntiCheatModule.ROTATION_JITTER_STEP;
            float secondClampedPitch = this.clampPitch(secondPitch);
            boolean secondOnGround = false;
            PacketSender.sendPositionRotation(secondX, secondY, secondZ, secondYaw, secondClampedPitch, secondOnGround);
            this.pendingPositionCorrection = true;
            break;
         case PACKET:
            double[] offsets = new double[2];
            offsets[0] = 0.0625101D;
            offsets[1] = 0.0;
            this.sendPositionOffsets(offsets);
      }
   }

   private void sendPositionOffsets(double... offsets) {
      for (double offset : offsets) {
         double x = ClientAccess.zioMinecraft.player.getX();

         double y = ClientAccess.zioMinecraft.player.getY() + offset;

         double z = ClientAccess.zioMinecraft.player.getZ();

         PacketSender.sendPosition(x, y, z, false);
      }
   }

   private boolean shouldApplyCritical(Entity target) {
      if (this.canPerformCritical()) {
         if (auraModule.isToggled()
            && auraModule.criticalAttackFlag) {
            if (this.modeSetting.getValue() != CriticalsModule.ModeChoice.GRIM) {
               return false;
            }
         }
         if (target != null) {
            if (target.isAlive() && !(target instanceof EndCrystal)) {
               return true;
            }
         }
         return false;
      }
      return false;
   }

   public static void bindModules(ModuleRegistry registry) {
      auraModule = (AuraModule)registry.getModule(
         AuraModule.class
      );
   }

   public CriticalsModule() {
      super("Criticals", "Turns your hits into critical ones.", ModuleCategory.COMBAT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.PACKET));
      this.pauseOnMoveSetting = this.add(new BooleanSetting("PauseOnMove", false));
      this.onlyFullSetting = this.add(new BooleanSetting("OnlyFull", false));
      this.onlyWeaponSetting = this.add(new BooleanSetting("OnlyWeapon", true));
      this.pauseOnMoveSetting.addVisibilityPredicate((Predicate<Boolean>) unused -> {
         Setting<ModeChoice> modeSource = this.modeSetting;
         return modeSource.getValue() != ModeChoice.GRIMV3;
      });
      this.onlyFullSetting.addVisibilityPredicate((Predicate<Boolean>) unused -> {
         Setting<ModeChoice> modeSource = this.modeSetting;
         return modeSource.getValue() == ModeChoice.GRIMV3;
      });
   }

   public enum ModeChoice implements NamedEntry {
      PACKET("Packet"),
      NCP("NCP"),
      GRIM("Grim"),
      GRIMV3("2b2t");

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
