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
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.modules.PlacementModule;
import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.managers.HoleManager;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.managers.MovementPredictionManager;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.exploit.MultiTaskModule;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WebAuraModule extends PlacementModule {
   private static MultiTaskModule multiTask;
   private static SpeedMineModule speedMine;
   private final Setting<Float> rangeSetting;
   private final Setting<Boolean> alternativeSetting;
   private final Setting<Boolean> ignoreNakedsSetting;
   private final Setting<Boolean> ignoreHoleSetting;
   private final Setting<Boolean> onlyFishSetting;
   private final Setting<Boolean> selfSafetySetting;
   private final Setting<Boolean> extrapolationSetting;
   private final Setting<Integer> ticksSetting;
   private final Setting<Boolean> positionsSetting;
   private final Setting<Boolean> headSetting;
   private final Setting<Boolean> legsSetting;
   private Player target;

   @Override
   protected boolean disableForJumpOrSleep() {
      return false;
   }

   private Vec3 getTargetPoint(Player target) {
      AABB bounds = target.getBoundingBox();

      if (this.extrapolationSetting.getValue()) {
         MovementPredictionManager extrapolatorSnapshot = ClientContext.movementPredictionManager;

         int extrapolationTicks = BoundingBoxUtils.getPredictionTicks(this.ticksSetting);

         bounds = extrapolatorSnapshot.getPredictedBoundingBox(target, extrapolationTicks);
      }

      return BoundingBoxUtils.getBottomCenter(bounds);
   }

   @Override

   protected InventoryUtils.SwapSlot findPlacementSwapSlot() {
      return InventoryUtils.findSwapSlot(Items.COBWEB, this.alternativeSetting.getValue());
   }

   static {
      multiTask = (MultiTaskModule)ClientContext.getModule(MultiTaskModule.class);
      speedMine = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
   }

   @Override
   public String getInfo() {
      if (this.target != null) {
         return this.target.getGameProfile().name();
      } else {
         return null;
      }
   }

   private boolean isSafeForSelf(BlockPos position) {
      if (!this.selfSafetySetting.getValue()) {
         return true;
      }

      AABB placementBounds = new AABB(position);

      AABB boundsSnapshot = ClientAccess.zioMinecraft.player.getBoundingBox();
      boolean intersectsSelf = boundsSnapshot.intersects(placementBounds);

      if (this.extrapolationSetting.getValue()) {
         Integer ticks = this.ticksSetting.getValue();

         for (int remainingTicks = ticks; remainingTicks >= 0; remainingTicks--) {
            MovementPredictionManager extrapolatorSnapshot = ClientContext.movementPredictionManager;
            LocalPlayer playerSnapshot = ClientAccess.zioMinecraft.player;
            boundsSnapshot = extrapolatorSnapshot.getPredictedBoundingBox(playerSnapshot, remainingTicks);
            if (boundsSnapshot.intersects(placementBounds)) {
               intersectsSelf = true;
               break;
            }
         }
      }

      return !intersectsSelf;
   }

   @Override

   protected void onPlacementAttempt(BlockPos position, boolean ignoredResult) {
      if (position.equals(speedMine.getRebreakPos())) {
         SpeedMineModule.clearRebreakAndReset(speedMine);
      }
   }


   protected Player findNearestTarget() {
      Optional<AbstractClientPlayer> selectedSnapshot = ClientAccess.zioMinecraft.level.players().stream().filter(
            (Predicate<AbstractClientPlayer>)candidate -> {
               if (this.ignoreHoleSetting.getValue()) {
                  HoleManager holeTrackerSnapshot = ClientContext.holeManager;

                  BlockPos holePositionSnapshot = PlayerStateUtils.getRoundedBlockPosition(
                     (net.minecraft.world.entity.LivingEntity)(candidate));

                  if (holeTrackerSnapshot.intersectsHole(holePositionSnapshot)) {
                     return false;
                  }
               }

               if (this.onlyFishSetting.getValue()) {
                  if (!candidate.isVisuallySwimming()) {
                     return false;
                  }
               }
               if (candidate == ClientAccess.zioMinecraft.player) {
                  return false;
               }

               String candidateName = candidate.getName().getString();

               GameProfile profile = ClientAccess.zioMinecraft.player.getGameProfile();
               if (Objects.equals(candidateName, profile.name())) {
                  return false;
               }

               SocialRegistry friendManagerSnapshot = ClientContext.socialRegistry;
               String friendNameSnapshot = candidate.getName().getString();

               if (friendManagerSnapshot.isFriendName(friendNameSnapshot)) {
                  return false;
               }
               if (!candidate.isAlive()) {
                  return false;
               }
               if (candidate.isDeadOrDying()) {
                  return false;
               }

               if (this.ignoreNakedsSetting.getValue()) {
                  if (!PlayerStateUtils.hasArmor(candidate)) {
                     return false;
                  }
               }

               double distance = this.getTargetPoint(candidate).distanceTo(ClientAccess.zioMinecraft.player.getEyePosition());

               Float range = this.rangeSetting.getValue();
               return distance <= range.floatValue();
            }
      ).min(
         Comparator.comparing((Function<AbstractClientPlayer, Double>)candidate -> {
            LocalPlayer distancePlayer = ClientAccess.zioMinecraft.player;

            double squaredDistance = candidate.distanceToSqr(distancePlayer);
            return squaredDistance;
         })
      );
      return selectedSnapshot.orElse(null);
   }

   @Override

   public List getPlacementPositions() {
      this.target = this.findNearestTarget();
      if (this.target != null) {
         if (!multiTask.shouldPauseModule(this)) {
            ArrayList positions = new ArrayList();

            AABB bounds = this.target.getBoundingBox();

            if (this.extrapolationSetting.getValue()) {
               MovementPredictionManager extrapolatorSnapshot = ClientContext.movementPredictionManager;
               Player targetSnapshot = this.target;
               Setting<Integer> ticksSettingSnapshot = this.ticksSetting;

               int extrapolationTicks = BoundingBoxUtils.getPredictionTicks(ticksSettingSnapshot);

               bounds = extrapolatorSnapshot.getPredictedBoundingBox(targetSnapshot, extrapolationTicks);
            }

            BlockPos basePosition = BlockPos.containing(
               BoundingBoxUtils.getBottomCenter(bounds)
            );

            if (this.legsSetting.getValue()) {
               double minimumY = bounds.minY;
               if (minimumY - basePosition.getY()
                  <= MathConstants.ONE_HALF) {
                  positions.add(basePosition.below());
                  positions.add(basePosition);
               }
            }

            if (this.headSetting.getValue()) {
               positions.add(basePosition.above());
            }

            positions.removeIf((Predicate<BlockPos>)candidatePosition -> {
               if (!WorldUtils.isPlacementOccupied(candidatePosition)) {
                  FluidState fluid = ClientAccess.zioMinecraft.level.getFluidState(candidatePosition);
                  if (fluid.isEmpty()) {
                     if (this.isSafeForSelf(candidatePosition)) {
                        return false;
                     }
                  }
               }

               return true;
            });
            return positions;
         }
      }

      return Collections.emptyList();
   }

   public static void bindModules(ModuleRegistry modules) {
      multiTask = (MultiTaskModule)modules.getModule(
         MultiTaskModule.class
      );
      speedMine = (SpeedMineModule)modules.getModule(
         SpeedMineModule.class
      );
   }

   public WebAuraModule() {
      super("WebAura", "Traps your enemies with cobwebs.", ModuleCategory.COMBAT, new String[]{"autoweb"});
      this.alternativeSetting = this.add(new BooleanSetting("Alternative", false));
      this.rangeSetting = this.add(new NumberSetting<>("Range", 5.0F, 0.0F, 6.0F).withUnit("m"));
      this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false));
      this.ignoreHoleSetting = this.add(new BooleanSetting("IgnoreHole", false));
      this.onlyFishSetting = this.add(new BooleanSetting("OnlyFish", false));
      this.selfSafetySetting = this.add(new BooleanSetting("SelfSafety", false));
      this.extrapolationSetting = this.add(new BooleanSetting("Extrapolation", true).markVisibilityDependency());
      this.ticksSetting = this.add(new NumberSetting<>("Ticks", 4, 0, 8)
         .setBoundaryLabel("Auto", SettingLabelCondition.MIN).dependsOn(this.extrapolationSetting));
      this.positionsSetting = this.add(new BooleanSetting("Positions", false).markAsGroup());
      this.headSetting = this.add(new BooleanSetting("Head", false).dependsOn(this.positionsSetting));
      this.legsSetting = this.add(new BooleanSetting("Legs", true).dependsOn(this.positionsSetting));
      this.placementJumpSetting.setValue(false);
      this.placementAttackSetting.setValue(false);
      this.unregister(this.placementAutoDisableSetting);
      this.unregister(this.placementJumpSetting);
      this.unregister(this.placementChorusSetting);
      this.unregister(this.placementPearlSetting);
      this.unregister(this.placementAttackSetting);
   }
}
