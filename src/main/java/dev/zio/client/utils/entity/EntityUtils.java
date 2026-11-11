/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.entity;

import dev.zio.client.utils.combat.ZombifiedPiglinAttackState;

import dev.zio.client.ClientAccess;
import dev.zio.client.utils.math.MathConstants;

import dev.zio.mixin.FireworkRocketEntityAccessor;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariant.AssetInfo;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EntityUtils implements ClientAccess {

   public static Set<BlockPos> getFireworkPositions() {
      HashSet<BlockPos> positions = new HashSet<>();
      ClientLevel level = ClientAccess.zioMinecraft.level;
      Iterator<Entity> iterator = level.entitiesForRendering().iterator();

      while (iterator.hasNext()) {
         Entity entity = iterator.next();
         if (entity instanceof FireworkRocketEntity) {
            positions.add(entity.blockPosition());
         }
      }

      return positions;
   }

   private EntityUtils() {
   }

   public static boolean isHostile(Entity entity) {
      if (entity instanceof EnderMan enderman) {
         return enderman.isCreepy();
      }
      if (entity instanceof ZombifiedPiglinAttackState attackingEntity) {
         return attackingEntity.zio$isAttacking();
      }
      if (entity instanceof Wolf wolf) {
         Identifier currentTexture = wolf.getTexture();
         Holder<WolfVariant> variantHolder = wolf.getVariant();
         WolfVariant variant = variantHolder.value();
         AssetInfo assets = variant.adultInfo();
         ResourceTexture angryTexture = assets.angry();
         return currentTexture == angryTexture.texturePath();
      }
      if (entity instanceof NeutralMob neutralMob) {
         return neutralMob.isAngry();
      }
      if (!(entity instanceof AbstractPiglin piglin)) {
         return entity instanceof PolarBear || entity instanceof Enemy;
      }
      if (entity instanceof PiglinBrute) {
         return true;
      }

      if (piglin.isBaby()) {
         return false;
      }

      PiglinArmPose armPose = piglin.getArmPose();
      return armPose == PiglinArmPose.CROSSBOW_CHARGE || armPose == PiglinArmPose.CROSSBOW_HOLD || armPose == PiglinArmPose.ATTACKING_WITH_MELEE_WEAPON;
   }

   public static boolean isNeutral(Entity entity) {
      return entity instanceof NeutralMob || entity instanceof AbstractPiglin && !(entity instanceof PiglinBrute);
   }

   public static boolean isLookingAtPlayer(Entity entity) {
      if (!(entity instanceof LivingEntity livingEntity)) {
         return false;
      }

      Vec3 eyePosition = livingEntity.getEyePosition();
      float pitch = livingEntity.getXRot();
      float headYaw = livingEntity.getYHeadRot();
      Vec3 viewDirection = livingEntity.calculateViewVector(pitch, headYaw);
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      double playerAlignment = getViewAlignment(eyePosition, viewDirection, player);
      ClientLevel level = ClientAccess.zioMinecraft.level;
      AABB bounds = livingEntity.getBoundingBox();
      AABB searchBounds = bounds.inflate(16.0D);
      Predicate<Entity> otherLivingEntities = candidate -> candidate instanceof LivingEntity
         && candidate != ClientAccess.zioMinecraft.player && candidate.isAlive();
      for (Entity nearby : level.getEntities(livingEntity, searchBounds, otherLivingEntities)) {
         if (getViewAlignment(eyePosition, viewDirection, nearby) > playerAlignment) {
            return false;
         }
      }

      return playerAlignment > MathConstants.HALF;
   }

   private static double getViewAlignment(Vec3 eyePosition, Vec3 viewDirection, Entity entity) {
      Vec3 toEntity = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ()).subtract(eyePosition);
      if (toEntity.lengthSqr() < 1.0E-4D) {
         return 1.0D;
      }

      return viewDirection.dot(toEntity.normalize());
   }

   public static float getPlayerHealthWithAbsorption() {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      return getHealthWithAbsorption(player);
   }

   public static float getHealthFraction(LivingEntity entity) {
      float healthFraction = getHealthWithAbsorption(entity);
      healthFraction /= entity.getMaxHealth();
      float maximum = 1.0F;
      return Mth.clamp(healthFraction, 0.0F, maximum);
   }

   public static boolean isPassive(Entity entity) {
      return entity instanceof AbstractFish || entity instanceof AgeableMob || entity instanceof Squid || entity instanceof Dolphin || entity instanceof Villager;
   }

   public static boolean hasCollisionBelow(LivingEntity entity) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      AABB boundsSnapshot = entity.getBoundingBox();
      AABB expandedBounds = boundsSnapshot.expandTowards(0.0D, -0.1D, 0.0D);
      return !level.noBlockCollision(entity, expandedBounds);
   }

   public static void setStepHeight(LivingEntity entity, float height) {
      Holder<Attribute> stepHeightAttribute = Attributes.STEP_HEIGHT;
      AttributeInstance attributeSnapshot = entity.getAttribute(stepHeightAttribute);
      attributeSnapshot.setBaseValue(height);
   }

   public static boolean isFirstPersonCameraEntity(Entity entity) {
      Options options = ClientAccess.zioMinecraft.options;
      if (options.getCameraType() != CameraType.FIRST_PERSON) {
         return false;
      }

      if (entity == ClientAccess.zioMinecraft.player) {
         return false;
      }

      return ClientAccess.zioMinecraft.getCameraEntity() == entity;
   }

   public static boolean hasFirework(Entity shooter) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      Iterator<Entity> iterator = level.entitiesForRendering().iterator();

      while (iterator.hasNext()) {
         Entity entity = iterator.next();
         if (entity instanceof FireworkRocketEntityAccessor firework && firework.zio$getShooter() == shooter) {
            return true;
         }
      }

      return false;
   }

   public static float getHealthWithAbsorption(Entity entity) {
      if (entity instanceof LivingEntity livingEntity) {
         float health = livingEntity.getHealth();
         return health + livingEntity.getAbsorptionAmount();
      }
      return 2.0F;
   }

   public static float getStepHeight(LivingEntity entity) {
      Holder<Attribute> stepHeightAttribute = Attributes.STEP_HEIGHT;
      AttributeInstance attribute = entity.getAttribute(stepHeightAttribute);
      return (float)attribute.getValue();
   }

}
