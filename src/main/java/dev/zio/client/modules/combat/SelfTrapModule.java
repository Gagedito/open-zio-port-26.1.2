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
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class SelfTrapModule extends AutoTrapModule {
   private Setting<Boolean> onlyHoleSetting;
   private Setting<Boolean> onlyStandingSetting;
   private Setting<Boolean> smartSetting;
   protected Setting<Float> enemyRangeSetting;

   @Override
   public String getInfo() {
      return null;
   }

   private void initializeSelfTrapSettings() {
      BooleanSetting onlyHoleEditor = new BooleanSetting("OnlyHole", false);
      this.onlyHoleSetting = this.add(onlyHoleEditor);

      BooleanSetting onlyStandingEditor = new BooleanSetting("OnlyStanding", false);
      this.onlyStandingSetting = this.add(onlyStandingEditor);

      BooleanSetting smartEditor = new BooleanSetting("Smart", false);
      Setting<Boolean> smartDependency = smartEditor.markVisibilityDependency();
      this.smartSetting = this.add(smartDependency);

      NumberSetting<Float> enemyRange = new NumberSetting<>(
         "EnemyRange",
         3.0F,
         1.0F,
         6.0F, unused -> this.smartSetting.isExpanded()
      );
      NumberSetting<Float> enemyRangeWithUnit = enemyRange.withUnit("m");
      this.enemyRangeSetting = this.add(enemyRangeWithUnit);
   }

   @Override
   protected boolean shouldPlaceBelow() {
      return false;
   }

   private Entity findNearestEnemy() {
      Iterable<Entity> entities = ClientAccess.zioMinecraft.level.entitiesForRendering();
      Spliterator<Entity> entitySpliterator = entities.spliterator();
      Stream<Entity> candidateStream = StreamSupport.stream(entitySpliterator, false).filter(candidate -> {
         if (candidate == ClientAccess.zioMinecraft.player) {
            return false;
         }

         float candidateDistance = ClientAccess.zioMinecraft.player.distanceTo(candidate);
         if (!(candidateDistance <= this.enemyRangeSetting.getValue() && candidate instanceof Player)) {
            return false;
         }
         SocialRegistry friends = ClientContext.socialRegistry;
         Component candidateName = candidate.getName();
         String name = candidateName.getString();

         return !friends.isFriendName(name) && candidate.isAlive();
      });
      LocalPlayer distancePlayer = ClientAccess.zioMinecraft.player;
      Comparator<Entity> nearestFirst = Comparator.comparing(distancePlayer::distanceToSqr);
      Optional<Entity> enemy = candidateStream.min(nearestFirst);
      return enemy.orElse(null);
   }

   private boolean shouldSkipUnsupportedPlacement(BlockPos blockPos) {
      boolean attackCheckFlag = true;

      if (!BlockInteractionUtils.canPlaceAt(blockPos, true, attackCheckFlag)) {
         return true;
      }

      Setting<Boolean> airPlaceSettingRef = this.placementAirPlaceSetting;

      if (airPlaceSettingRef.getValue()) {
         return false;
      }

      boolean strictDirectionSnapshot = BlockInteractionUtils.isStrictDirectionEnabled();
      Setting<Boolean> raytraceSettingRef = this.placementRaytraceSetting;

      boolean raytrace = raytraceSettingRef.getValue();
      return BlockInteractionUtils.findSupportDirection(blockPos, strictDirectionSnapshot, raytrace) == null;
   }

   @Override
   protected Player selectTarget() {
      return ClientAccess.zioMinecraft.player;
   }

   @Override
   public List getPlacementPositions() {
      List placements = super.getPlacementPositions();
      placements.removeIf((java.util.function.Predicate<net.minecraft.core.BlockPos>)this::shouldSkipUnsupportedPlacement);

      if (this.onlyStandingSetting.getValue() && PlayerStateUtils.isLocalHeightBelowOne()) {
         return Collections.emptyList();
      }
      if (this.smartSetting.getValue()) {
         if (this.findNearestEnemy() == null) {
            return Collections.emptyList();
         }
      }
      if (this.onlyHoleSetting.getValue()) {
         if (!ClientContext.holeManager.isInHole()) {
            return Collections.emptyList();
         }
      }
      return placements;
   }

   public SelfTrapModule() {
      super("SelfTrap", "Covers you with blocks.", ModuleCategory.COMBAT);

      this.setDisableOnRespawn(true);

      this.initializeSelfTrapSettings();
      Setting<Float> range = this.rangeSetting;
      this.unregister(range);
      this.unregister(this.ignoreNakedsSetting);
      this.unregister(this.ignoreCrawlSetting);
      this.unregister(this.onlyPhasedSetting);
      this.unregister(this.logoutsSetting);
      this.unregister(this.belowSetting);
   }
}
