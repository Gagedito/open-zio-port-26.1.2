/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.utils.inventory.ItemUtils;


import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.utils.math.MathConstants;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class AimAssistModule extends Module {
   private final Setting<Float> rangeSetting;
   private final Setting<Float> speedSetting;
   private final Setting<Float> fovSetting;
   private final Setting<Boolean> onlyWeaponSetting;
   private final Setting<Boolean> ignoreInvisibleSetting;
   private final Setting<Boolean> lockTargetSetting;
   private final Setting<Boolean> friendsSetting;
   private final Setting<Boolean> instantSetting;
   private AbstractClientPlayer targetPlayer;

   public AimAssistModule() {
      super("AimAssist", "Helps you aiming at players.", ModuleCategory.COMBAT, new String[0]);
      this.rangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 1.0F, 6.0F).withUnit("m"));
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.1F, 15.0F));
      this.fovSetting = this.add(new NumberSetting<>("Fov", 90.0F, 1.0F, 360.0F));
      this.onlyWeaponSetting = this.add(new BooleanSetting("OnlyWeapon", true));
      this.ignoreInvisibleSetting = this.add(new BooleanSetting("IgnoreInvisible", true));
      this.lockTargetSetting = this.add(new BooleanSetting("LockTarget", true));
      this.friendsSetting = this.add(new BooleanSetting("Friends", true));
      this.instantSetting = this.add(new BooleanSetting("Instant", false));
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      if (ClientAccess.zioMinecraft.screen != null) {
         return;
      }

      if (!(ClientAccess.zioMinecraft.player.getMainHandItem().getItem() instanceof AxeItem)) {
         if (!ItemUtils.isSword(ClientAccess.zioMinecraft.player.getMainHandItem())) {
            if (this.onlyWeaponSetting.getValue()) {
               return;
            }
         }
      }

      boolean keepTarget = false;
      if (this.targetPlayer != null) {
         keepTarget = this.lockTargetSetting.getValue();
      }
      if (!keepTarget) {
         Stream<AbstractClientPlayer> players = ClientAccess.zioMinecraft.level.players().stream();
         Comparator<AbstractClientPlayer> byDistance = Comparator.comparing(candidate -> {
            return ClientAccess.zioMinecraft.player.distanceTo(candidate);
         });
         this.targetPlayer = players.sorted(byDistance).filter(this::isValidTarget).findFirst().orElse(null);
      }

      AbstractClientPlayer checkedTarget = this.targetPlayer;

      if (!this.isValidTarget(checkedTarget)) {
         this.targetPlayer = null;
         return;
      }
      if (ClientAccess.zioMinecraft.crosshairPickEntity == this.targetPlayer) {
         return;
      }

      if (this.instantSetting.getValue()) {
         AbstractClientPlayer aimedTarget = this.targetPlayer;

         AABB bounds = aimedTarget.getBoundingBox();
         Vec3 center = bounds.getCenter();
         float[] rotations = RotationManager.getRotationsToPosition(center);
         float yaw = rotations[0];
         LocalPlayer yawPlayer = ClientAccess.zioMinecraft.player;
         yawPlayer.setYRot(yaw);
         float pitch = rotations[1];

         ClientAccess.zioMinecraft.player.setXRot(pitch);
      } else {
         AbstractClientPlayer aimedTarget = this.targetPlayer;
         double yawDifference = yawDifference(aimedTarget);
         if (!(yawDifference > 1.0D) && !(yawDifference < -1.0D)) {
            return;
         }

         Float speedValue = this.speedSetting.getValue();
         double scaledDifference = yawDifference * speedValue.floatValue();
         float yawStep = (float)(scaledDifference * 0.009999999776482582D);
         LocalPlayer yawPlayer = ClientAccess.zioMinecraft.player;
         LocalPlayer viewPlayer = ClientAccess.zioMinecraft.player;
         float partialTick = RenderUtils.getPartialTick();
         float nextYaw = viewPlayer.getViewYRot(partialTick) - yawStep;
         yawPlayer.setYRot(nextYaw);
      }
   }

   private boolean isValidTarget(Entity entity) {
      if (!(entity instanceof Player) || entity == ClientAccess.zioMinecraft.player) {
         return false;
      }

      if (!entity.isAlive()) {
         return false;
      }

      if (this.ignoreInvisibleSetting.getValue() && entity.isInvisible()) {
         return false;
      }

      double fieldOfView = (this.fovSetting.getValue()).floatValue();
      if (!isWithinFov(entity, fieldOfView)) {
         return false;
      }

      float distance = ClientAccess.zioMinecraft.player.distanceTo(entity);

      Float rangeValue = this.rangeSetting.getValue();
      if (!(distance <= rangeValue)) {
         return false;
      }

      if (this.friendsSetting.getValue()) {
         SocialRegistry socialRegistry = ClientContext.socialRegistry;

         String name = ((Player)entity).getGameProfile().name();

         if (socialRegistry.isFriendName(name)) {
            return false;
         }
      }
      return hasLineOfSight(entity);
   }

   private boolean hasLineOfSight(Entity entity) {
      Vec3 start = new Vec3(ClientAccess.zioMinecraft.player.getX(),
         ClientAccess.zioMinecraft.player.getY() + ClientAccess.zioMinecraft.player.getEyeHeight(),
         ClientAccess.zioMinecraft.player.getZ());
      Vec3 feet = new Vec3(entity.getX(), entity.getY(), entity.getZ());
      ClientLevel feetWorld = ClientAccess.zioMinecraft.level;
      ClipContext feetRay = new ClipContext(start, feet, Block.COLLIDER, Fluid.NONE, ClientAccess.zioMinecraft.player);

      BlockHitResult feetHit = ((BlockGetter)feetWorld).clip(feetRay);
      if (feetHit.getType() == Type.MISS) {
         return true;
      }
      Vec3 eyes = new Vec3(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
      ClientLevel eyesWorld = ClientAccess.zioMinecraft.level;
      ClipContext eyesRay = new ClipContext(start, eyes, Block.COLLIDER, Fluid.NONE, ClientAccess.zioMinecraft.player);
      BlockHitResult eyesHit = ((BlockGetter)eyesWorld).clip(eyesRay);
      return eyesHit.getType() == Type.MISS;
   }

   private static double yawDifference(Entity entity) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      float partialTick = RenderUtils.getPartialTick();

      float viewYaw = player.getViewYRot(partialTick);
      double difference = (viewYaw - yawToEntity(entity))
         % MathConstants.FULL_TURN_DEGREES;
      double wrapped = (difference + 540.0D)
         % MathConstants.FULL_TURN_DEGREES;
      return wrapped - 180.0D;
   }

   private static boolean isWithinFov(Entity entity, double fieldOfView) {
      fieldOfView = (float)(fieldOfView * MathConstants.ONE_HALF);
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      float partialTick = RenderUtils.getPartialTick();

      float viewYaw = player.getViewYRot(partialTick);
      double difference = (viewYaw - yawToEntity(entity))
         % MathConstants.FULL_TURN_DEGREES;
      double wrapped = (difference + 540.0D)
         % MathConstants.FULL_TURN_DEGREES;
      double yawDifference = wrapped - 180.0D;
      return yawDifference > 0.0 && yawDifference < fieldOfView || -fieldOfView < yawDifference && yawDifference < 0.0;
   }

   private static float yawToEntity(Entity entity) {
      double targetX = entity.getX();
      LocalPlayer xPlayer = ClientAccess.zioMinecraft.player;

      double deltaX = targetX - xPlayer.getX();
      double targetZ = entity.getZ();
      LocalPlayer zPlayer = ClientAccess.zioMinecraft.player;
      double deltaZ = targetZ - zPlayer.getZ();
      double angle = Math.atan2(deltaX, deltaZ);
      double degrees = angle * 57.2957795D;
      return (float)(degrees * -1.0D);
   }
}
