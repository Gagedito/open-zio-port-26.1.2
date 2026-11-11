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
import dev.zio.client.utils.math.MathConstants;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class AutoCrystalTargeting {
   private final List<LivingEntity> targets = new ArrayList<>();
   private final AutoCrystalModule module;

   AutoCrystalTargeting(AutoCrystalModule module) {
      this.module = module;
   }

   synchronized void refresh() {
      this.targets.clear();
      for (Entity entity : ClientAccess.zioMinecraft.level.entitiesForRendering()) {
         if (!(entity instanceof LivingEntity candidate) || this.module.isInvalidTarget(candidate)) {
            continue;
         }
         if (entity instanceof Player player) {
            this.targets.add(player);
         }

         if (this.module.bossesSetting.getValue() && (candidate instanceof WitherBoss || candidate instanceof EnderDragon || candidate instanceof Warden)) {
            this.targets.add(candidate);
         }
      }
   }

   synchronized List<LivingEntity> snapshot() {
      return new ArrayList<>(this.targets);
   }

   // Keep using the previous target for proximity checks when no target is selected.
   LivingEntity findProximityTarget() {
      LivingEntity proximityTarget = this.module.target == null && !this.module.isInvalidTarget(this.module.previousTarget)
         ? this.module.previousTarget : this.module.target;
      if (proximityTarget == null) {
         double nearestDistance = 1.7976931348623157e+308D;
         Iterator players = this.snapshot().iterator();
         while (players.hasNext()) {
            LivingEntity candidate = (LivingEntity)players.next();

            double distance = ClientAccess.zioMinecraft.player.distanceTo(candidate);
            if (!this.module.isInvalidTarget(candidate)
               && !(distance > nearestDistance)) {
               nearestDistance = distance;
               proximityTarget = candidate;
            }
         }

         if (proximityTarget == null) {
            return null;
         }

         this.module.target = proximityTarget;
      }
      return proximityTarget;
   }

   boolean hasObsidianDropNear(LivingEntity proximityTarget) {
      Iterator entities = ClientAccess.zioMinecraft.level.entitiesForRendering().iterator();
      while (entities.hasNext()) {
         Entity entity = (Entity)entities.next();
         if (entity instanceof ItemEntity item) {
            ItemStack itemStack = item.getItem();
            if (itemStack.getItem() == Items.OBSIDIAN) {
               ItemStack countStack = item.getItem();
               int itemCount = countStack.getCount();
               if (itemCount >= 8) {
                  if (entity.isAlive()) {
                     AABB targetBounds = proximityTarget.getBoundingBox();
                     AABB itemBounds = entity.getBoundingBox();
                     Vec3 targetCenter = targetBounds.getCenter();
                     double centerY = targetCenter.y();
                     AABB upperTargetBounds = targetBounds.setMinY(centerY);
                     double expandZ = MathConstants.ONE_HALF;
                     double expandX = MathConstants.ONE_HALF;
                     AABB proximityBox = upperTargetBounds.inflate(expandX, 0.0, expandZ);
                     if (itemBounds.intersects(proximityBox)) {
                        return true;
                     }
                  }
               }
            }
         }
      }
      return false;
   }
}
