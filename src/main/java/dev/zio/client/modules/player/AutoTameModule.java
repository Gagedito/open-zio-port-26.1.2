/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;


import dev.zio.client.ClientAccess;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AutoTameModule extends Module {
   private final Setting<ActionChoice> actionSetting;
   private final Setting<Float> rangeSetting;
   private final Setting<Integer> frequencySetting;
   private final Setting<Float> delaySetting;
   private final Setting<Boolean> rotateSetting;
   final Setting<Boolean> targetsSetting;
   private final Setting<Boolean> dogsSetting;
   private final Setting<Boolean> catsSetting;
   private final Setting<Boolean> parrotsSetting;
   private final ElapsedTimer interactionTimer;

   private boolean isTargetEnabled(TamableAnimal animal) {
      return (animal instanceof Cat && this.catsSetting.getValue())
         || (animal instanceof Wolf && this.dogsSetting.getValue())
         || (animal instanceof Parrot && this.parrotsSetting.getValue());
   }

   @EventHandler
   private void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (event.getStage() == EventStage.Post) {
         return;
      }
      ElapsedTimer timerSnapshot = this.interactionTimer;
      double delaySeconds = (this.delaySetting.getValue()).floatValue();
      TimeUnit delayUnit = TimeUnit.SECONDS;

      if (!timerSnapshot.hasElapsed(delaySeconds, delayUnit)) {
         return;
      }
      int interactionCount = 0;
      ClientLevel level = ClientAccess.zioMinecraft.level;
      for (Entity entity : level.entitiesForRendering()) {
         if (!(entity instanceof TamableAnimal animal)) {
            continue;
         }
         if (!this.isTargetEnabled(animal)) {
            continue;
         }
         LocalPlayer distancePlayer = ClientAccess.zioMinecraft.player;

         float distance = entity.distanceTo(distancePlayer);
         Float range = this.rangeSetting.getValue();
         if (!(distance <= range)) {
            continue;
         }

         Vec3 interactionPosition = (this.actionSetting.getValue()).interact(animal);
         if (interactionPosition == null) {
            continue;
         }
         interactionCount++;
         if (this.rotateSetting.getValue()) {
            float[] rotations = RotationManager.getRotationsToPosition(
               interactionPosition);

            event.setRotation(rotations);
         }
         Integer frequency = this.frequencySetting.getValue();
         if (interactionCount >= frequency) {
            break;
         }

      }
      timerSnapshot = this.interactionTimer;
      timerSnapshot.reset();
   }

   private static Predicate<ItemStack> createTamingFoodPredicate(TamableAnimal animal) {
      if (animal instanceof Wolf) {
         return stack -> {
            Item bone = Items.BONE;
            return stack.is(bone);
         };
      }
      if (animal instanceof Parrot) {
         return stack -> {
            Item beetrootSeeds = Items.BEETROOT_SEEDS;
            Item pumpkinSeeds = Items.PUMPKIN_SEEDS;
            Item melonSeeds = Items.MELON_SEEDS;
            Item wheatSeeds = Items.WHEAT_SEEDS;
            return List.of(wheatSeeds, melonSeeds, pumpkinSeeds, beetrootSeeds).contains(stack.getItem());
         };
      }
      return animal::isFood;
   }

   public AutoTameModule() {
      super("AutoTame", "Automatically interacts with tame-able entities.", ModuleCategory.PLAYER, new String[0]);
      AutoTameModule.ActionChoice actionDefault = AutoTameModule.ActionChoice.TAME;
      this.actionSetting = this.add(new EnumSetting<>("Action", actionDefault));
      this.rangeSetting = this.add(new NumberSetting<>("Range", 3.0F, 1.0F, 6.0F).withUnit("m"));
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 8));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 0.1F, 0.1F, 5.0F).withUnit("s"));
      Boolean rotateDefault = true;
      this.rotateSetting = this.add(new BooleanSetting("Rotate", rotateDefault));
      Boolean targetsDefault = false;
      BooleanSetting targetsBuilder = new BooleanSetting("Targets", targetsDefault);
      targetsBuilder.markAsGroup();
      targetsBuilder.markVisibilityDependency();
      this.targetsSetting = this.add(targetsBuilder);
      Predicate dogsVisibility = ignoredValue -> {
         Setting<Boolean> targets = this.targetsSetting;
         return targets.isExpanded();
      };
      Boolean dogsDefault = true;
      this.dogsSetting = this.add(new BooleanSetting("Dogs", dogsDefault, dogsVisibility));
      Predicate catsVisibility = ignoredValue -> {
         Setting<Boolean> targets = this.targetsSetting;
         return targets.isExpanded();
      };
      Boolean catsDefault = true;
      this.catsSetting = this.add(new BooleanSetting("Cats", catsDefault, catsVisibility));
      Predicate parrotsVisibility = ignoredValue -> {
         Setting<Boolean> targets = this.targetsSetting;
         return targets.isExpanded();
      };
      Boolean parrotsDefault = true;
      this.parrotsSetting = this.add(new BooleanSetting("Parrots", parrotsDefault, parrotsVisibility));
      this.interactionTimer = new ElapsedTimer();
   }

   private enum ActionChoice implements NamedEntry {
      TAME("Tame") {
         @Override
         Vec3 interact(TamableAnimal animal) {
            if (animal.getOwner() != null) {
               return null;
            }

            Predicate<ItemStack> foodPredicate = AutoTameModule.createTamingFoodPredicate(animal);
            int slot = InventoryUtils.findHotbarSlot(foodPredicate);
            if (slot == -1) {
               return null;
            }

            InventoryUtils.selectHotbarSlotForPlacement(slot);
            InteractionHand hand = InteractionHand.MAIN_HAND;

            BlockInteractionUtils.interactWithEntity(animal, hand);

            AABB bounds = animal.getBoundingBox();
            return bounds.getCenter();
         }

      },
      SIT("Sit") {
         @Override
         Vec3 interact(TamableAnimal animal) {
            if (animal.isInSittingPose()) {
               return null;
            }
            if (animal.isOrderedToSit() || ClientAccess.zioMinecraft.player != animal.getOwner()) {
               return null;
            }
            return interactUnlessHoldingFood(animal);
         }

      },
      STAND("Stand") {
         @Override
         Vec3 interact(TamableAnimal animal) {
            if (!animal.isInSittingPose() && !animal.isOrderedToSit()) {
               return null;
            }
            if (ClientAccess.zioMinecraft.player != animal.getOwner()) {
               return null;
            }
            return interactUnlessHoldingFood(animal);
         }

      };
      final Vec3 interactUnlessHoldingFood(TamableAnimal animal) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         ItemStack heldStack = player.getMainHandItem();
         if (animal.isFood(heldStack)) {
            return null;
         }
         InteractionHand hand = InteractionHand.MAIN_HAND;
         BlockInteractionUtils.interactWithEntity(animal, hand);
         return animal.getBoundingBox().getCenter();
      }

      private final String displayName;
      @Override
      public String getName() {
         return this.displayName;
      }
      ActionChoice(String displayName) {
         this.displayName = displayName;
      }
      abstract Vec3 interact(TamableAnimal animal);
   }

}
