/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.combat;

import dev.zio.client.utils.inventory.EnchantmentUtils;
import dev.zio.client.utils.inventory.EquipmentUtils;
import dev.zio.client.utils.math.Vec3Utils;

import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.combat.AutoCrystalModule;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class DamageUtils implements ClientAccess {
   private static AutoCrystalModule autoCrystal;

   private DamageUtils() {
   }

   public static float getCrystalDamage(Vec3 explosionPosition, LivingEntity target) {
      AABB bounds = target.getBoundingBox();
      return getCrystalDamage(explosionPosition, target, bounds);
   }

   public static float getCrystalDamage(
      Vec3 explosionPosition, LivingEntity target, AABB bounds
   ) {
      List ignoredPositions = Collections.emptyList();

      return getExplosionDamage(explosionPosition, target, bounds, 6.0D, true, ignoredPositions, null);
   }

   public static float getBedDamage(Vec3 explosionPosition, LivingEntity target) {
      AABB bounds = target.getBoundingBox();
      return getBedDamage(explosionPosition, target, bounds);
   }

   public static float getBedDamage(
      Vec3 explosionPosition, LivingEntity target, AABB bounds
   ) {
      List ignoredPositions = Collections.emptyList();

      return getExplosionDamage(explosionPosition, target, bounds, 5.0D, true, ignoredPositions, null);
   }

   public static float getExplosionDamage(
      Vec3 explosionPosition, LivingEntity target, AABB bounds, double power, boolean ignoreLowResistance, BlockPos ignoredPosition, BlockPos solidPosition
   ) {
      List ignoredPositions = Collections.singletonList(ignoredPosition);

      return getExplosionDamage(explosionPosition, target, bounds, power, ignoreLowResistance, ignoredPositions, solidPosition);
   }

   public static float getExplosionDamage(
      Vec3 explosionPosition, LivingEntity target, AABB bounds, double power, boolean ignoreLowResistance, Collection ignoredPositions, BlockPos solidPosition
   ) {
      if (ClientAccess.zioMinecraft.player == target) {
         if (ClientAccess.zioMinecraft.player.isCreative()) {
            return 0.0F;
         }
      }
      Vec3 bottomCenter = new Vec3(
         Mth.lerp(MathConstants.ONE_HALF, bounds.minX, bounds.maxX),
         bounds.minY,
         Mth.lerp(MathConstants.ONE_HALF, bounds.minZ, bounds.maxZ)
      );
      double diameter = power * 2.0D;
      double normalizedDistance = bottomCenter.distanceTo(explosionPosition) / diameter;
      if (normalizedDistance > 1.0D) {
         return 0.0F;
      }

      float exposure = getExposure(explosionPosition, target, bounds, ignoredPositions, solidPosition, ignoreLowResistance);
      double impact = (1.0D - normalizedDistance) * exposure;
      double quadraticImpact = impact * impact + impact;
      double scaledImpact = quadraticImpact / 2.0D;
      double rawDamage = scaledImpact * 7.0D * diameter;
      float damage = (float)(rawDamage + 1.0D);
      damage = scaleForDifficulty(damage);

      damage = applyDamageReduction(target, damage);
      damage = resolveBlockingDamage(target, explosionPosition, damage);
      return Math.max(damage, 0.0F);
   }

   public static float getExposure(
      Vec3 explosionPosition, Entity target, Collection ignoredPositions, boolean ignoreLowResistance
   ) {
      AABB bounds = target.getBoundingBox();

      return getExposure(explosionPosition, target, bounds, ignoredPositions, null, ignoreLowResistance);
   }

   public static float getExposure(
      Vec3 explosionPosition, Entity target, AABB bounds, Collection ignoredPositions, BlockPos solidPosition, boolean ignoreLowResistance
   ) {
      Vec3 samplePosition = new Vec3(0.0, 0.0, 0.0);
      double stepX = 1.0D / ((bounds.maxX - bounds.minX) * 2.0D + 1.0D);
      double stepY = 1.0D / ((bounds.maxY - bounds.minY) * 2.0D + 1.0D);
      double stepZ = 1.0D / ((bounds.maxZ - bounds.minZ) * 2.0D + 1.0D);
      double offsetX = (1.0D - Math.floor(1.0D / stepX) * stepX) / 2.0D;
      double offsetZ = (1.0D - Math.floor(1.0D / stepZ) * stepZ) / 2.0D;
      if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) {
         return 0.0F;
      }
      BlockRaycastContext.Builder builder = new BlockRaycastContext.Builder(samplePosition, explosionPosition);

      builder.withEntity(target);
      BlockRaycastClassifier[] classifierArray = new BlockRaycastClassifier[3];
      classifierArray[0] = BlockRaycastClassifier.ignorePositions(ignoredPositions);

      classifierArray[1] = BlockRaycastClassifier.solidPosition(solidPosition);
      classifierArray[2] = ignoreLowResistance
         ? BlockRaycastClassifier.IGNORE_LOW_RESISTANCE
         : BlockRaycastClassifier.NO_OVERRIDE;
      builder.withClassifiers(classifierArray);
      BlockRaycastContext context = builder.build();
      int visibleSamples = 0;
      int totalSamples = 0;
      for (double fractionX = 0.0; fractionX <= 1.0D; fractionX += stepX) {
         for (double fractionY = 0.0; fractionY <= 1.0D; fractionY += stepY) {
            for (double fractionZ = 0.0; fractionZ <= 1.0D; fractionZ += stepZ) {
               double maximumX = bounds.maxX;
               double minimumX = bounds.minX;
               double sampleX = Mth.lerp(fractionX, minimumX, maximumX);
               double maximumY = bounds.maxY;
               double minimumY = bounds.minY;
               double sampleY = Mth.lerp(fractionY, minimumY, maximumY);
               double maximumZ = bounds.maxZ;
               double minimumZ = bounds.minZ;
               double sampleZ = Mth.lerp(fractionZ, minimumZ, maximumZ);
               double offsetSampleX = sampleX + offsetX;
               double shiftedZ = sampleZ + offsetZ;
               Vec3Utils.setComponents(samplePosition, offsetSampleX, sampleY, shiftedZ);

               if (RaycastUtils.raycast(context).getType() == Type.MISS) {
                  visibleSamples++;
               }
               totalSamples++;
            }
         }
      }
      return (float)visibleSamples / totalSamples;
   }

   public static float applyDamageReduction(LivingEntity target, float damage) {
      AutoCrystalModule crystalModule = autoCrystal;

      boolean assumeBestArmor;
      if (crystalModule.isToggled()) {
         assumeBestArmor = autoCrystal.assumeBestArmorSetting.getValue();
      } else {
         assumeBestArmor = false;
      }

      DamageSources damageSources = ClientAccess.zioMinecraft.level.damageSources();
      DamageSource damageSource = damageSources.explosion((Explosion)null);

      float armor = target.getArmorValue();
      var toughnessAttribute = Attributes.ARMOR_TOUGHNESS;
      float toughness = (float)target.getAttributeValue(toughnessAttribute);
      damage = CombatRules.getDamageAfterAbsorb(target, damage, damageSource, armor, toughness);
      var resistanceEffect = MobEffects.RESISTANCE;
      if (target.hasEffect(resistanceEffect) || assumeBestArmor) {
         var activeResistanceEffect = MobEffects.RESISTANCE;

         MobEffectInstance resistance = target.getEffect(activeResistanceEffect);
         float resistanceAmplifier = 1.0F;
         if (resistance != null) {
            resistanceAmplifier = resistance.getAmplifier();
         }
         float resistanceLevel = resistanceAmplifier + 1.0F;
         float resistanceNumerator = damage * (25.0F - resistanceLevel * 5.0F);
         damage = resistanceNumerator / 25.0F;
      }
      int protectionScore = 0;
      if (target instanceof Player player) {
         int assumedArmorUnits = 1;
         List<ItemStack> armorStacks = EquipmentUtils.getArmorStacks(player);
         Iterator armorItems = armorStacks.iterator();
         while (armorItems.hasNext()) {
            ItemStack armorItem = (ItemStack)armorItems.next();

            if (!armorItem.isEmpty()) {
               TagKey headArmorTag = ItemTags.HEAD_ARMOR;
               if (armorItem.is(headArmorTag)) {
                  assumedArmorUnits--;
               }
               Map enchantments = EnchantmentUtils.getLevels(armorItem);
               ResourceKey blastProtectionKey = Enchantments.BLAST_PROTECTION;

               int blastContributionWord = ((Integer)enchantments.getOrDefault(blastProtectionKey, 0));
               blastContributionWord *= 2;
               ResourceKey protectionKey = Enchantments.PROTECTION;
               Integer protectionLevel = (Integer)enchantments.getOrDefault(protectionKey, 0);
               protectionScore += blastContributionWord + protectionLevel;
               assumedArmorUnits++;
            }
         }
         if (assumeBestArmor) {
            float armorUnitsAsFloat = assumedArmorUnits;
            float assumedArmorFraction = armorUnitsAsFloat / 4.0F;
            protectionScore = (int)(assumedArmorFraction * 20.0F);
         }
      }
      if (protectionScore > 20) {
         protectionScore = 20;
      }
      float protectionAsFloat = protectionScore;
      damage *= 1.0F - protectionAsFloat / 25.0F;
      return damage < 0.0F ? 0.0F : damage;
   }

   private static float resolveBlockingDamage(
      LivingEntity target, Vec3 explosionPosition, float damage
   ) {
      if (!target.isUsingItem()) {
         return damage;
      }

      ItemStack usedItem = target.getUseItem();
      var blockingType = DataComponents.BLOCKS_ATTACKS;

      BlocksAttacks blocksAttacks = (BlocksAttacks)usedItem.get(blockingType);
      if (blocksAttacks == null) {
         return damage;
      }

      DamageSources damageSources = ClientAccess.zioMinecraft.level.damageSources();
      DamageSource damageSource = damageSources.explosion((Explosion)null);
      float headYaw = target.getYHeadRot();
      Vec3 viewVector = target.calculateViewVector(0.0F, headYaw);

      Vec3 attackDirection = explosionPosition.subtract(target.position());
      Vec3 horizontalDirection = new Vec3(attackDirection.x, 0.0, attackDirection.z);
      attackDirection = horizontalDirection.normalize();
      double dot = attackDirection.dot(viewVector);
      double angle = Math.acos(dot);

      return blocksAttacks.resolveBlockedDamage(damageSource, damage, angle);
   }

   public static float scaleForDifficulty(float damage) {
      return switch (ClientAccess.zioMinecraft.level.getDifficulty()) {
         case PEACEFUL -> 0.0F;
         case EASY -> Math.min(damage / 2.0F + 1.0F, damage);
         case HARD -> damage * 1.5F;
         default -> damage;
      };
   }

   public static int getDurabilityPercent(ItemStack stack) {
      float remainingFraction = ((float)stack.getMaxDamage() - (float)stack.getDamageValue()) / (float)stack.getMaxDamage();
      float usedFraction = 1.0F - remainingFraction;
      return Mth.clamp(100 - (int)Math.ceil(usedFraction * 100.0F), 1, 100);
   }

   static {
      autoCrystal = (AutoCrystalModule)ClientContext.getModule(AutoCrystalModule.class);
   }
}
