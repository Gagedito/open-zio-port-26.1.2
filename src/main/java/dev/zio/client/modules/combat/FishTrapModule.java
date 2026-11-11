/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;


import dev.zio.client.ClientContext;
import dev.zio.client.utils.entity.PlayerState;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.managers.MovementPredictionManager;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class FishTrapModule extends AutoTrapModule {
   private Setting<CrawlTrapMode> modeSetting;
   private Setting<Integer> extrapolationSetting;

   @Override
   protected Vec3 getTargetPosition(Player player) {
      if (!this.extrapolationSetting.isAtMinimum()) {
         MovementPredictionManager predictionManagerSnapshot = ClientContext.movementPredictionManager;

         int predictionTicks = this.extrapolationSetting.getValue();

         AABB predictedBounds = predictionManagerSnapshot.getPredictedBoundingBox(player, predictionTicks);
         return BoundingBoxUtils.getBottomCenter(predictedBounds);
      } else {
         return super.getTargetPosition(player);
      }
   }

   public FishTrapModule() {
      super("FishTrap", "Traps your enemies in fish (crawling) position.", ModuleCategory.COMBAT);

      this.initializeCrawlTrapSettings();

      this.setDisableOnRespawn(false);
      Setting<Boolean> targetsSettingSnapshot = this.targetsSetting;
      this.unregister(targetsSettingSnapshot);
      Setting<Boolean> headSettingSnapshot = this.headSetting;
      this.unregister(headSettingSnapshot);
      Setting<Boolean> faceSettingSnapshot = this.faceSetting;
      this.unregister(faceSettingSnapshot);
      Setting<Boolean> legsSettingSnapshot = this.legsSetting;
      this.unregister(legsSettingSnapshot);
      Setting<Boolean> belowSettingSnapshot = this.belowSetting;
      this.unregister(belowSettingSnapshot);
      Setting<Boolean> logoutsSettingSnapshot = this.logoutsSetting;
      this.unregister(logoutsSettingSnapshot);
      Setting<Boolean> ignoreCrawlSettingSnapshot = this.ignoreCrawlSetting;
      this.unregister(ignoreCrawlSettingSnapshot);
      Setting<Boolean> onlyPhasedSettingSnapshot = this.onlyPhasedSetting;
      this.unregister(onlyPhasedSettingSnapshot);
      Setting<Boolean> headSettingForValue = this.headSetting;
      headSettingForValue.setValue(true);
      Setting<Boolean> faceSettingForValue = this.faceSetting;
      faceSettingForValue.setValue(false);
      Setting<Boolean> legsSettingForValue = this.legsSetting;
      legsSettingForValue.setValue(false);
   }

   @Override
   protected boolean isValidTarget(Player player) {
      if (player instanceof PlayerState crawlState && !crawlState.zio$wasCrawling()) {
         return false;
      }

      return !player.isFallFlying() && super.isValidTarget(player);
   }

   @Override
   protected boolean includeLowerSideOffsets() {
      return false;
   }

   @Override
   protected boolean isHeadOnly() {
      return true;
   }

   private void initializeCrawlTrapSettings() {
      NumberSetting<Integer> ticksEditor = new NumberSetting<>(
         "Extrapolation", 0, 0,
         6
      );
      Setting<Integer> extrapolationEditor = this.add(ticksEditor);

      SettingLabelCondition minimumMode = SettingLabelCondition.MIN;

      extrapolationEditor.setBoundaryLabel("None", minimumMode);
      this.extrapolationSetting = extrapolationEditor;

      EnumSetting<CrawlTrapMode> modeEditor = new EnumSetting<>("Mode", FishTrapModule.CrawlTrapMode.HEAD);
      this.modeSetting = this.add(modeEditor);
   }

   @Override
   protected List<Vec3> collectTargetOffsets(BlockPos targetPos, int baseYOffset, float targetHeight) {
      return new ArrayList<>(collectModeOffsets(this));
   }

   private static Collection<Vec3> collectModeOffsets(FishTrapModule owner) {
      return (owner.modeSetting.getValue()).getOffsets();
   }

   enum CrawlTrapMode implements NamedEntry {
      HEAD("Head", new Vec3(0.0, 1.0, 0.0)),
      BELOW("Below", new Vec3(0.0, -1.0D, 0.0)),
      BOTH("Both", new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, -1.0D, 0.0));

      private final String displayName;
      private final Vec3[] offsets;

      @Override
      public String getName() {
         return this.displayName;
      }

      CrawlTrapMode(String displayName, Vec3... offsets) {
         this.displayName = displayName;
         this.offsets = offsets;
      }

      public Collection<Vec3> getOffsets() {
         return Arrays.asList(this.offsets);
      }
   }
}
