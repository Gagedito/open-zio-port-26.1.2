/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;


import dev.zio.client.utils.math.MathConstants;
import java.util.function.Predicate;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.combat.RaycastUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.Iterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

public class AntiAimModule extends Module {
   private final Setting<Boolean> yawEnabledSetting;
   final Setting<YawChoice> yawModeSetting;
   private final Setting<Float> spinSpeedSetting;
   private final Setting<Float> staticYawSetting;
   private final Setting<Float> relativeYawSetting;
   private final Setting<Float> flipSetting;
   private final Setting<Float> jitterRangeSetting;
   private final Setting<Float> baseSetting;
   private final Setting<Boolean> pitchEnabledSetting;
   final Setting<PitchChoice> pitchModeSetting;
   private final Setting<Float> staticPitchSetting;
   private final Setting<Float> relativePitchSetting;
   public final Setting<Boolean> endermanSetting;
   private final Setting<LookChoice> endermanLookSetting;
   private boolean rotationApplied;
   private float spinYaw;
   private int pauseTicks;

   private EnderMan findVisibleEnderman() {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      Iterator<Entity> entities = level.entitiesForRendering().iterator();
      while (entities.hasNext()) {
         Entity entity = entities.next();
         if (!(entity instanceof EnderMan enderman)) {
            continue;
         }
         if (enderman.isCreepy() || !enderman.isAlive()) {
            continue;
         }
         Setting<LookChoice> lookSetting = this.endermanLookSetting;
         if (lookSetting.getValue() == AntiAimModule.LookChoice.AVOID
            && !this.isLookingAtEnderman(enderman)) {
            continue;
         }

         if (this.endermanLookSetting.getValue() == AntiAimModule.LookChoice.STARE) {
            Vec3 endermanEyePosition = enderman.getEyePosition();
            Vec3 playerEyePosition = ClientAccess.zioMinecraft.player.getEyePosition();
            double distance = endermanEyePosition.distanceTo(playerEyePosition);
            if (Double.compare(distance, 48.0D) > 0) {
               continue;
            }
         }

         if (RaycastUtils.hasVisibleEntitySample(entity)) {
            return enderman;
         }
      }
      return null;
   }

   public void pauseRotations() {

      this.pauseTicks = (int)(2L);
   }

   @EventHandler(priority = -100)
   private void onRotationEvent(RotationRequestEvent event) {
      this.rotationApplied = false;
      RotationManager rotationManagerSnapshot = ClientContext.rotationManager;
      if (rotationManagerSnapshot.getRequest() != null) {
         return;
      }
      if (this.pauseTicks > 0) {
         this.pauseTicks--;
         return;
      }
      float yaw = event.getPlayerYaw();
      float pitch = event.getPlayerPitch();
      Setting<Boolean> yawEnabledSnapshot = this.yawEnabledSetting;
      Boolean yawEnabled = yawEnabledSnapshot.getValue();
      if (yawEnabled) {
         Setting<YawChoice> yawModeSnapshot = this.yawModeSetting;
         AntiAimModule.YawChoice yawMode = yawModeSnapshot.getValue();
         LocalPlayer yawPlayer = ClientAccess.zioMinecraft.player;
         float playerYaw = yawPlayer.getYRot();
         yaw = yawMode.getYaw(this, playerYaw);

         this.rotationApplied = true;
      }

      Setting<Boolean> pitchEnabledSnapshot = this.pitchEnabledSetting;
      Boolean pitchEnabled = pitchEnabledSnapshot.getValue();
      if (pitchEnabled) {
         Setting<PitchChoice> pitchModeSnapshot = this.pitchModeSetting;
         AntiAimModule.PitchChoice pitchMode = pitchModeSnapshot.getValue();
         LocalPlayer pitchPlayer = ClientAccess.zioMinecraft.player;
         float playerPitch = pitchPlayer.getXRot();
         pitch = pitchMode.getPitch(this, playerPitch);

         this.rotationApplied = true;
      }

      Setting<Boolean> endermanEnabledSnapshot = this.endermanSetting;
      Boolean endermanEnabled = endermanEnabledSnapshot.getValue();
      if (endermanEnabled) {

         EnderMan enderman = this.findVisibleEnderman();
         if (enderman != null) {
            Setting<LookChoice> avoidModeSnapshot = this.endermanLookSetting;
            if (avoidModeSnapshot.getValue() == AntiAimModule.LookChoice.AVOID) {

               pitch = 9e+01F;

               this.rotationApplied = true;
            }

            Setting<LookChoice> stareModeSnapshot = this.endermanLookSetting;
            if (stareModeSnapshot.getValue() == AntiAimModule.LookChoice.STARE) {
               Vec3 endermanEyePosition = enderman.getEyePosition();

               float[] stareAngles = RotationManager.getRotationsToPosition(
                  endermanEyePosition
               );
               yaw = stareAngles[0];
               pitch = stareAngles[1];

               this.rotationApplied = true;
            }
         }
      }

      if (this.rotationApplied) {
         rotationManagerSnapshot = ClientContext.rotationManager;

         float[] rotationAngles = new float[(2)];
         rotationAngles[0] = yaw;
         rotationAngles[1] = pitch;

         rotationManagerSnapshot.requestRotation(rotationAngles, -999, (true));
      }
   }

   private boolean isLookingAtEnderman(EnderMan enderman) {
      LocalPlayer viewPlayer = ClientAccess.zioMinecraft.player;

      Vec3 viewVector = viewPlayer.getViewVector(1.0F);
      Vec3 lookDirection = viewVector.normalize();
      Vec3 endermanDirection = new Vec3(
         enderman.getX() - ClientAccess.zioMinecraft.player.getX(),
         enderman.getEyeY() - ClientAccess.zioMinecraft.player.getEyeY(),
         enderman.getZ() - ClientAccess.zioMinecraft.player.getZ()
      );
      double distance = endermanDirection.length();
      endermanDirection = endermanDirection.normalize();
      double dotProduct = lookDirection.dot(endermanDirection);

      return dotProduct > (1.0D) - 0.025D / distance;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      LocalPlayer mainHandPlayer = ClientAccess.zioMinecraft.player;
      ItemStack mainHandStack = mainHandPlayer.getMainHandItem();
      Item mainHandItem = mainHandStack.getItem();
      boolean projectileItem = mainHandItem instanceof ProjectileWeaponItem || mainHandItem instanceof TridentItem;
      if (projectileItem) {
         LocalPlayer usingPlayer = ClientAccess.zioMinecraft.player;
         if (usingPlayer.isUsingItem()) {

            this.pauseTicks = 2;
         }
      }
   }

   @EventHandler
   private void onConnectionSendPacketEvent(
      PacketEvent.Send event
   ) {
      if (event.getPacket() instanceof ServerboundUseItemPacket useItemPacket) {
         InteractionHand useItemHand = useItemPacket.getHand();
         this.pauseForItemUse(useItemHand);
      }

      if (event.getPacket() instanceof ServerboundUseItemOnPacket useOnPacket) {
         InteractionHand useOnHand = useOnPacket.getHand();
         this.pauseForItemUse(useOnHand);
      }
   }

   private void pauseForItemUse(InteractionHand hand) {
      if (ClientAccess.zioMinecraft.player == null) {
         return;
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      ItemStack stack = player.getItemInHand(hand);
      if (stack.isEmpty()) {
         return;
      }
      Item item = stack.getItem();
      if (!(item instanceof BlockItem) && !BlockInteractionUtils.isThrowableItem(item)) {
         return;
      }

      this.pauseTicks = 2;
   }

   public AntiAimModule() {
      super("AntiAim", "Sets your rotations server-side.", ModuleCategory.MISC, new String[0]);
      this.yawEnabledSetting = this.add(new BooleanSetting("Yaw", false).markVisibilityDependency());
      this.yawModeSetting = this.add(new EnumSetting<>("Yaw", AntiAimModule.YawChoice.JITTER).dependsOn(this.yawEnabledSetting));
      Predicate<Object> showSpinSpeed = ignored -> this.yawModeSetting.getValue() == AntiAimModule.YawChoice.SPIN;
      this.spinSpeedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.1F, 90.0F, showSpinSpeed).withUnit("°").dependsOn(this.yawEnabledSetting));
      this.staticYawSetting = this.add(new NumberSetting<>("Static", 0.0F, 0.0F, 360.0F,
         ignored -> this.yawModeSetting.getValue() == AntiAimModule.YawChoice.STATIC).withUnit("°").dependsOn(this.yawEnabledSetting));
      this.relativeYawSetting = this.add(new NumberSetting<>("Relative", 0.0F, -180.0F, 180.0F,
         ignored -> this.yawModeSetting.getValue() == AntiAimModule.YawChoice.RELATIVE).withUnit("°").dependsOn(this.yawEnabledSetting));
      this.flipSetting = this.add(new NumberSetting<>("Flip", 0.0F, 0.0F, 90.0F,
         ignored -> this.yawModeSetting.getValue() == AntiAimModule.YawChoice.FLIP).withUnit("°").dependsOn(this.yawEnabledSetting));
      this.jitterRangeSetting = this.add(new NumberSetting<>("JitterRange", 0.0F, 0.0F, 180.0F,
         ignored -> this.yawModeSetting.getValue() == AntiAimModule.YawChoice.JITTER).withUnit("°").dependsOn(this.yawEnabledSetting));
      this.baseSetting = this.add(new NumberSetting<>("Base", 0.0F, 0.0F, 360.0F,
         ignored -> this.yawModeSetting.getValue() == AntiAimModule.YawChoice.FLIP
            || this.yawModeSetting.getValue() == AntiAimModule.YawChoice.JITTER).withUnit("°").dependsOn(this.yawEnabledSetting));
      this.pitchEnabledSetting = this.add(new BooleanSetting("Pitch", false).markVisibilityDependency());
      this.pitchModeSetting = this.add(new EnumSetting<>("Pitch", AntiAimModule.PitchChoice.STATIC).dependsOn(this.pitchEnabledSetting));
      this.staticPitchSetting = this.add(new NumberSetting<>("Static", 0.0F, -90.0F, 90.0F,
         ignored -> this.pitchModeSetting.getValue() == AntiAimModule.PitchChoice.STATIC).withUnit("°").dependsOn(this.pitchEnabledSetting));
      this.relativePitchSetting = this.add(new NumberSetting<>("Relative", 0.0F, -90.0F, 90.0F,
         ignored -> this.pitchModeSetting.getValue() == AntiAimModule.PitchChoice.RELATIVE).withUnit("°").dependsOn(this.pitchEnabledSetting));
      this.endermanSetting = this.add(new BooleanSetting("Enderman", false).markVisibilityDependency());
      this.endermanLookSetting = this.add(new EnumSetting<>("Look", AntiAimModule.LookChoice.AVOID).dependsOn(this.endermanSetting));
      this.staticYawSetting.setConfigName("StaticYaw");
      this.staticPitchSetting.setConfigName("StaticPitch");
      this.relativeYawSetting.setConfigName("RelativeYaw");
      this.relativePitchSetting.setConfigName("RelativePitch");
      this.yawModeSetting.setConfigName("YawMode");
      this.pitchModeSetting.setConfigName("PitchMode");
   }

   public enum LookChoice implements NamedEntry {
      AVOID("Avoid"),
      STARE("Stare");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      LookChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   enum PitchChoice implements NamedEntry {
      STATIC("Static") {
         @Override
         public float getPitch(AntiAimModule module, float playerPitch) {
            return module.staticPitchSetting.getValue();
         }
      },
      RELATIVE("Relative") {
         @Override
         public float getPitch(AntiAimModule module, float playerPitch) {
            float pitch = module.relativePitchSetting.getValue() + playerPitch;
            float minimumPitch = -MathConstants.NINETY_DEGREES;
            float maximumPitch = MathConstants.NINETY_DEGREES;
            return Mth.clamp(pitch, minimumPitch, maximumPitch);
         }
      },
      RANDOM("Random") {
         @Override
         public float getPitch(AntiAimModule module, float playerPitch) {
            return (float)(
               Math.random() * 180.0 - MathConstants.NINETY_DEGREES
            );
         }
      };

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      PitchChoice(String displayName) {
         this.displayName = displayName;
      }

      public abstract float getPitch(AntiAimModule module, float playerPitch);
   }

   enum YawChoice implements NamedEntry {
      JITTER("Jitter") {
         @Override
         public float getYaw(AntiAimModule module, float playerYaw) {
            double jitterSample = Math.random();
            jitterSample = jitterSample * module.jitterRangeSetting.getValue() * 2.0;
            jitterSample -= module.jitterRangeSetting.getValue();
            return (float)Mth.wrapDegrees(jitterSample + module.baseSetting.getValue());
         }
      },
      SPIN("Spin") {
         @Override
         public float getYaw(AntiAimModule module, float playerYaw) {
            float previousSpinYaw = module.spinYaw;
            module.spinYaw = previousSpinYaw + module.spinSpeedSetting.getValue();
            float yawToWrap = module.spinYaw;
            return Mth.wrapDegrees(yawToWrap);
         }
      },
      FLIP("Flip") {
         @Override
         public float getYaw(AntiAimModule module, float playerYaw) {
            float baseYaw = module.baseSetting.getValue();
            float flipOffset;
            if (ClientAccess.zioMinecraft.player.tickCount % 2
               == 0) {
               flipOffset = -module.flipSetting.getValue();
            } else {
               flipOffset = module.flipSetting.getValue();
            }

            return Mth.wrapDegrees(baseYaw + flipOffset);
         }
      },
      STATIC("Static") {
         @Override
         public float getYaw(AntiAimModule module, float playerYaw) {
            return module.staticYawSetting.getValue();
         }
      },
      RELATIVE("Relative") {
         @Override
         public float getYaw(AntiAimModule module, float playerYaw) {
            return module.relativeYawSetting.getValue() + playerYaw;
         }
      },
      RANDOM("Random") {
         @Override
         public float getYaw(AntiAimModule module, float playerYaw) {
            return (float)(Math.random() * MathConstants.FULL_TURN_DEGREES);
         }
      };

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      YawChoice(String displayName) {
         this.displayName = displayName;
      }

      public abstract float getYaw(AntiAimModule module, float playerYaw);
   }
}
