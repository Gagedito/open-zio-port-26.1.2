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
import dev.zio.client.modules.PlacementModule;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.SpeedMineRebreakChoice;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class AutoDropModule extends PlacementModule {
   private static SpeedMineModule speedMineModule = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
   private static final MutableBlockPos supportProbePosition = new MutableBlockPos();
   protected final Setting<Float> targetRangeSetting;
   protected final Setting<Boolean> ignoreNakedsSetting;
   protected final Setting<Boolean> blocksSetting;
   protected final Setting<Boolean> anvilsSetting;
   protected final Setting<Boolean> sandSetting;
   protected final Setting<Boolean> gravelSetting;
   protected final Setting<Boolean> concreteSetting;
   protected final Setting<Boolean> dragonEggSetting;
   private final Setting<Boolean> completeSetting;
   private boolean placingGravityBlock;

   @Override
   protected InventoryUtils.SwapSlot findPlacementSwapSlot() {
      if (this.placingGravityBlock) {
         Predicate<ItemStack> gravityStackPredicate = this::isAllowedGravityStack;

         return InventoryUtils.findSwapSlot(gravityStackPredicate, false);
      } else {
         return super.findPlacementSwapSlot();
      }
   }

   private DropTargetPlan selectDropTarget() {
      double nearestDistance = 1.7976931348623157e+308D;
      DropTargetPlan selectedTarget = null;

      Iterator players = ClientAccess.zioMinecraft.level.players().iterator();

      while (players.hasNext()) {
         AbstractClientPlayer candidate = (AbstractClientPlayer)players.next();

         if (candidate.isAlive() && ClientAccess.zioMinecraft.player != candidate) {
            if (!ClientContext.socialRegistry.isFriendPlayer(candidate)) {
               if (this.ignoreNakedsSetting.getValue()) {
                  if (!PlayerStateUtils.hasArmor(candidate)) {
                     continue;
                  }
               }

               if (!this.isPlayerInCobweb(candidate)) {
                  BlockPos dropPosition = this.getDropPosition(candidate);

                  Vec3 playerEyes = ClientAccess.zioMinecraft.player.getEyePosition();
                  Vec3 candidatePosition = candidate.position();
                  double distance = playerEyes.distanceTo(candidatePosition);

                  Float targetRange = this.targetRangeSetting.getValue();
                  if (!(distance > targetRange.floatValue())) {
                     ClientLevel levelForBelowSnapshot = ClientAccess.zioMinecraft.level;
                     BlockPos belowPosition = dropPosition.below();

                     BlockState belowState = levelForBelowSnapshot.getBlockState(belowPosition);
                     if (belowState.canBeReplaced()) {
                        if (BlockInteractionUtils.canPlaceAt(dropPosition, false)) {
                           if (!this.isInstantRebreakPosition(dropPosition)) {
                              DropTargetPlan candidatePlan;
                              selectPlan: {
                                 boolean directionCheck = BlockInteractionUtils.isStrictDirectionEnabled();
                                 Direction placementDirection = BlockInteractionUtils.findSupportDirection(
                                    dropPosition, directionCheck
                                 );
                                 if (placementDirection == null) {
                                    Setting<Boolean> airPlaceSettingRef = this.placementAirPlaceSetting;

                                    if (!airPlaceSettingRef.getValue()) {
                                       Direction cageDirection = this.findCageDirection(
                                          dropPosition
                                       );
                                       if (cageDirection == null) {
                                          continue;
                                       }

                                       candidatePlan = new DropTargetPlan(candidate, DropPlacementMode.CAGE, cageDirection);
                                       break selectPlan;
                                    }
                                 }

                                 candidatePlan = new DropTargetPlan(candidate, DropPlacementMode.HEAD, null);
                              }

                              if (distance < nearestDistance) {
                                 selectedTarget = candidatePlan;
                                 nearestDistance = distance;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return selectedTarget;
   }

   public AutoDropModule() {
      super("AutoDrop", "Drops gravity blocks on your enemies.", ModuleCategory.COMBAT, new String[]{"dropaura"});
      this.targetRangeSetting = this.add(new NumberSetting<>("TargetRange", 4.0F, 1.0F, 6.0F).withUnit("m"));
      this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false));
      this.blocksSetting = this.add(new BooleanSetting("Blocks", false).markAsGroup());
      this.anvilsSetting = this.add(new BooleanSetting("Anvils", true).dependsOn(this.blocksSetting));
      this.sandSetting = this.add(new BooleanSetting("Sand", false).dependsOn(this.blocksSetting));
      this.gravelSetting = this.add(new BooleanSetting("Gravel", false).dependsOn(this.blocksSetting));
      this.concreteSetting = this.add(new BooleanSetting("Concrete", false).dependsOn(this.blocksSetting));
      this.dragonEggSetting = this.add(new BooleanSetting("DragonEgg", false).dependsOn(this.blocksSetting));
      this.completeSetting = this.add(new BooleanSetting("Complete", false)
         .dependsOn(this.placementAutoDisableSetting), this.placementAutoDisableSetting);
      this.unregister(this.placementChorusSetting);
   }

   private boolean isAllowedGravityStack(ItemStack stack) {
      if (!(stack.getItem() instanceof BlockItem blockItem)) {
         return false;
      } else {
         Block block = blockItem.getBlock();

         if (this.anvilsSetting.getValue() && block instanceof AnvilBlock) {
            return true;
         }

         if (this.sandSetting.getValue() && block instanceof SandBlock) {
            return true;
         }

         if (this.gravelSetting.getValue() && block == Blocks.GRAVEL) {
            return true;
         }

         if (this.concreteSetting.getValue() && block instanceof ConcretePowderBlock) {
            return true;
         }

         return this.dragonEggSetting.getValue() && block instanceof DragonEggBlock;
      }
   }

   private boolean isPlayerInCobweb(Player player) {
      Iterator intersectingPositions = PlayerStateUtils.getOccupiedBlockPositions(player).iterator();

      while (intersectingPositions.hasNext()) {
         if (WorldUtils.getBlock(((BlockPos)intersectingPositions.next()))
            == Blocks.COBWEB) {
            return true;
         }
      }

      return false;
   }

   private BlockPos getDropPosition(LivingEntity entity) {
      BlockPos entityPositionSnapshot = PlayerStateUtils.getRoundedBlockPosition(entity);
      double height = entity.getBbHeight();

      return entityPositionSnapshot.above(((int)Math.ceil(height)));
   }

   @Override
   public List getPlacementPositions() {
      Predicate<ItemStack> gravityStackPredicate = this::isAllowedGravityStack;

      int gravitySlot = InventoryUtils.findHotbarSlot(gravityStackPredicate);
      ArrayList positions = new ArrayList();
      this.placingGravityBlock = false;

      DropTargetPlan targetPlan = this.selectDropTarget();
      if (targetPlan == null) {
         return positions;
      }

      if (targetPlan.mode() == DropPlacementMode.CAGE && gravitySlot != -1) {
         int heightOffset = 0;

         while (true) {
            if (heightOffset >= 3) {
               break;
            }

            Player cagePlayerSnapshot = targetPlan.player();

            BlockPos cageOriginSnapshot = PlayerStateUtils.getRoundedBlockPosition((net.minecraft.world.entity.LivingEntity)(cagePlayerSnapshot));

            Direction cageDirection = targetPlan.cageDirection();
            BlockPos cageBaseSnapshot = cageOriginSnapshot.relative(cageDirection);

            positions.add(cageBaseSnapshot.above(heightOffset));
            heightOffset++;
         }
      } else {
         Player headPlayerSnapshot = targetPlan.player();

         positions.add(this.getDropPosition(headPlayerSnapshot));
         this.placingGravityBlock = true;
      }

      if (this.completeSetting.getValue() && positions.isEmpty()) {
         this.disable();
      }

      return positions;
   }

   private boolean isInstantRebreakPosition(BlockPos position) {
      float miningDamage = speedMineModule.getMiningProgress();
      Setting<Float> damageSettingRef = speedMineModule.damageSetting;
      Float damageSettingValue = damageSettingRef.getValue();
      float damageFraction = miningDamage / damageSettingValue;

      if (speedMineModule.isToggled()) {
         if (damageFraction >= 1.0F) {
            Setting<SpeedMineRebreakChoice> rebreakSettingRef = speedMineModule.rebreakSetting;
            if (rebreakSettingRef.getValue() == SpeedMineRebreakChoice.INSTANT) {
               if (position.equals(speedMineModule.getMiningPos())) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private Direction findCageDirection(BlockPos dropPosition) {
      List placeableDirections = BlockInteractionUtils.getStrictDirections(dropPosition);

      int nearestSupportDepth = 1337;
      Direction selectedDirection = null;
      for (Direction direction : Direction.values()) {
         if (!placeableDirections.contains(direction.getOpposite())) {
            if (BlockInteractionUtils.isStrictDirectionEnabled()) {
               continue;
            }
         }

         int supportDepth = 0;
         int depth = 0;

         while (true) {
            if (depth >= 3) {
               break;
            }

            MutableBlockPos mutableProbeSnapshot = supportProbePosition;
            int probeX = dropPosition.getX() + direction.getStepX();
            int probeY = dropPosition.getY() - depth;
            int probeZ = dropPosition.getZ() + direction.getStepZ();
            mutableProbeSnapshot.set(probeX, probeY, probeZ);
            MutableBlockPos probePosition = supportProbePosition;

            BlockState probeState = ClientAccess.zioMinecraft.level.getBlockState(probePosition);
            if (!probeState.canBeReplaced()) {
               supportDepth = depth;
               break;
            }

            supportDepth = -1;
            depth++;
         }

         if (supportDepth != -1 && supportDepth < nearestSupportDepth) {
            nearestSupportDepth = supportDepth;
            selectedDirection = direction;
         }
      }

      return selectedDirection;
   }

   public static void bindModules(ModuleRegistry modules) {
      speedMineModule = (SpeedMineModule)modules.getModule(
         SpeedMineModule.class
      );
   }
}
