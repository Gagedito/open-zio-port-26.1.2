/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.utils.entity.PlayerStateTracker;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.movement.FlightModule;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.AntiCheatRotationsChoice;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;

public final class AuraAttackReadiness implements ClientAccess {
   private static AntiCheatModule antiCheat;
   private static FlightModule flight;
   private static CriticalsModule criticals;
   private final AuraModule aura;

   private boolean shouldWaitForCriticalMovement() {
      boolean onGround = ClientAccess.zioMinecraft.player.onGround();

      if (!PlayerStateUtils.isInCobweb(ClientAccess.zioMinecraft.player)) {
         if (!ClientAccess.zioMinecraft.player.isPassenger()) {
            Setting<AntiCheatRotationsChoice> rotations = antiCheat.rotationsSetting;
            boolean motionSynchronized = false;
            if (rotations.getValue() == AntiCheatRotationsChoice.MOTION) {
               Setting<Boolean> movementSync = antiCheat.movementSyncSetting;

               motionSynchronized = movementSync.getValue();
            }

            if (motionSynchronized) {
               return this.shouldWaitForMotionCritical(onGround);
            }

            return this.shouldWaitForAirborneCritical(onGround);
         }
      }

      return false;
   }

   private boolean shouldWaitForCritical(Entity target) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;

      if (flight.isToggled() || player.getAbilities().flying || player.isFallFlying()
         || player.onClimbable() || PlayerStateUtils.isInLiquid(player)) {
         return false;
      }

      if (criticals.isToggled()) {
         if (criticals.getMode() == CriticalsModule.ModeChoice.GRIM) {
            return false;
         }
      }
      Holder<MobEffect> levitation = MobEffects.LEVITATION;
      if (player.hasEffect(levitation)) {
         return false;
      }
      return target instanceof LivingEntity && this.shouldWaitForCriticalMovement();
   }

   public AuraAttackReadiness(AuraModule aura) {
      this.aura = aura;
   }

   static {
      antiCheat = (AntiCheatModule)ClientContext.getModule(AntiCheatModule.class);
      flight = (FlightModule)ClientContext.getModule(FlightModule.class);
      criticals = (CriticalsModule)ClientContext.getModule(CriticalsModule.class);
   }

   private boolean shouldWaitForAirborneCritical(boolean onGround) {
      if (PlayerStateUtils.getTrackedFallDistance() <= 0.0F && !onGround) {
         return true;
      }

      if (onGround) {
         if (ClientContext.playerStateTracker.getGroundedTicks() <= 1) {
            return true;
         }
      }

      return false;
   }

   private boolean shouldWaitForMotionCritical(boolean onGround) {
      double currentY = ClientContext.playerStateTracker.getCurrentTickY();
      PlayerStateTracker previousYSource = ClientContext.playerStateTracker;
      float verticalMovement = (float)(currentY - previousYSource.getPreviousTickY());

      AABB collisionBounds = ClientAccess.zioMinecraft.player.getBoundingBox();
      if (verticalMovement > 0.0F) {
         double top = collisionBounds.maxY;
         AABB upperSlice = collisionBounds.setMinY(top);
         double upperY = verticalMovement;
         collisionBounds = upperSlice.deflate(0.0, upperY, 0.0);
      } else {
         double bottom = collisionBounds.minY;
         AABB lowerSlice = collisionBounds.setMaxY(bottom);
         double lowerY = verticalMovement;
         collisionBounds = lowerSlice.deflate(0.0, lowerY, 0.0);
      }

      CollisionGetter descendingLevel = ClientAccess.zioMinecraft.level;
      LocalPlayer descendingPlayer = ClientAccess.zioMinecraft.player;
      AABB descendingBounds = collisionBounds;
      if (!descendingLevel.noBlockCollision(descendingPlayer, descendingBounds)
         && verticalMovement < 0.0F) {
         return true;
      }

      CollisionGetter ascendingLevel = ClientAccess.zioMinecraft.level;
      LocalPlayer ascendingPlayer = ClientAccess.zioMinecraft.player;
      AABB ascendingBounds = collisionBounds;
      if (!ascendingLevel.noBlockCollision(ascendingPlayer, ascendingBounds)
         && verticalMovement > 0.0F) {
         return false;
      }

      return PlayerStateUtils.getTrackedFallDistance() <= 0.0F && !onGround;
   }

   public boolean canAttack(Entity target, float attackStrength) {
      if (target instanceof EndCrystal) {
         return true;
      }

      if (this.shouldWaitForCritical(target)) {
         return false;
      }

      if (!(target instanceof Player) && target instanceof LivingEntity livingTarget) {
         if (!antiCheat.is2b2tEnabled()) {
            double health = EntityUtils.getHealthWithAbsorption(livingTarget);
            if (health <= 1.0D) {
               float maximumHealth = livingTarget.getMaxHealth();
               if (maximumHealth <= 4.0F) {
                  double strength = attackStrength;
                  return strength >= 0.2D;
               }
            }
         }
      }

      AuraModule delaySource = this.aura;
      return attackStrength >= delaySource.getAttackDelay();
   }

   public static void bindModuleRegistry(ModuleRegistry modules) {
      antiCheat = (AntiCheatModule)modules.getModule(
         AntiCheatModule.class
      );
      flight = (FlightModule)modules.getModule(
         FlightModule.class
      );
      criticals = (CriticalsModule)modules.getModule(
         CriticalsModule.class
      );
   }
}
