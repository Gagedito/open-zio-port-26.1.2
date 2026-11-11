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
import dev.zio.client.ClientContext;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class AutoBreedModule extends Module {
   private final Setting<Float> rangeSetting;
   private final Setting<Integer> frequencySetting;
   private final Setting<Float> delaySetting;
   private final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> targetsSetting;
   private final Setting<Boolean> dogsSetting;
   private final Setting<Boolean> catsSetting;
   private final Setting<Boolean> parrotsSetting;
   private final Setting<Boolean> donkeysSetting;
   private final Setting<Boolean> horsesSetting;
   private final Setting<Boolean> allaysSetting;
   private final Setting<Boolean> othersSetting;
   private final ElapsedTimer interactionTimer;
   private final List<BreedingCooldown> breedingCooldowns;

   @Override
   public void onEnable() {
      this.breedingCooldowns.clear();
   }

   private static Predicate<ItemStack> getBreedingFoodPredicate(Entity entity) {
      if (entity instanceof Allay) {
         return stack -> {
            Item foodItem = Items.AMETHYST_SHARD;
            return stack.is(foodItem);
         };
      } else if (entity instanceof Parrot) {
         return stack -> {
            return List.of(Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS).contains(stack.getItem());
         };
      } else if (entity instanceof Animal animal) {
         return animal::isFood;
      } else {
         return stack -> false;
      }
   }

   @EventHandler
   private void onRotationRequest(RotationRequestEvent event) {
      this.breedingCooldowns.forEach(cooldown -> cooldown.elapsedTicks++);
      this.breedingCooldowns.removeIf(cooldownEntry -> {
            int elapsedTicks = cooldownEntry.elapsedTicks;

            return elapsedTicks >= (6000);
         }
      );
      ElapsedTimer delayTimer = this.interactionTimer;
      double delaySecondsValue = (this.delaySetting.getValue()).floatValue();
      TimeUnit delayUnit = TimeUnit.SECONDS;

      if (delayTimer.hasElapsed(delaySecondsValue, delayUnit)) {
         int interactions = 0;
         ClientLevel level = ClientAccess.zioMinecraft.level;
         Iterator<Entity> entities = level.entitiesForRendering().iterator();

         entityScan:
         while (entities.hasNext()) {
            if (entities.next() instanceof LivingEntity livingEntity) {
               Iterator<BreedingCooldown> cooldowns = this.breedingCooldowns.iterator();

               while (cooldowns.hasNext()) {
                  BreedingCooldown cooldown = cooldowns.next();

                  if (livingEntity == cooldown.entity) {
                     continue entityScan;
                  }
               }

               if (this.isTargetEnabled(livingEntity)) {
                  LocalPlayer player = ClientAccess.zioMinecraft.player;
                  float distance = livingEntity.distanceTo(player);
                  Float rangeValue = this.rangeSetting.getValue();
                  if (distance <= rangeValue) {
                     Predicate<ItemStack> foodPredicate = getBreedingFoodPredicate(livingEntity);

                     int foodSlot = InventoryUtils.findHotbarSlot(foodPredicate);
                     if (foodSlot == -1) {
                        return;
                     }

                     Vec3 targetCenter = livingEntity.getBoundingBox().getCenter();
                     if (targetCenter != null) {
                        InventoryUtils.selectHotbarSlotForPlacement(foodSlot);
                        InteractionHand hand = InteractionHand.MAIN_HAND;
                        BlockInteractionUtils.interactWithEntity(livingEntity, hand);
                        this.breedingCooldowns.add(new BreedingCooldown(livingEntity));
                        interactions++;
                        if (this.rotateSetting.getValue()) {
                           RotationManager rotationManagerForRequest = ClientContext.rotationManager;

                           float[] rotations = RotationManager.getRotationsToPosition(
                              targetCenter);

                           rotationManagerForRequest.requestRotation(rotations, 5);
                        }

                        Integer frequencyValue = this.frequencySetting.getValue();
                        if (interactions >= frequencyValue) {
                           break;
                        }
                     }
                  }
               }
            }
         }

         delayTimer = this.interactionTimer;

         delayTimer.reset();
      }
   }

   private boolean isTargetEnabled(LivingEntity entity) {
      if (entity.isBaby()) {
         return false;
      }

      boolean enabled;
      switch (entity) {
         case Wolf wolf:
            enabled = this.dogsSetting.getValue();
            break;
         case Cat cat:
            enabled = this.catsSetting.getValue();
            break;
         case Parrot parrot:
            enabled = this.parrotsSetting.getValue();
            break;
         case Donkey donkey:
            enabled = this.donkeysSetting.getValue();
            break;
         case Horse horse:
            enabled = this.horsesSetting.getValue();
            break;
         case Allay allay:
            enabled = this.allaysSetting.getValue();
            break;
         case Animal animal:
            enabled = this.othersSetting.getValue();
            break;
         default:
            enabled = false;
      }

      return enabled;
   }

   public AutoBreedModule() {
      super("AutoBreed", "Breeds animals in between each other automatically.", ModuleCategory.PLAYER, new String[0]);
      this.rangeSetting = this.add(new NumberSetting<>("Range", 3.0F, 1.0F, 6.0F).withUnit("m"));
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 8));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 0.0F, 0.0F, 5.0F).withUnit("s"));
      Boolean rotateDefault = true;
      this.rotateSetting = this.add(new BooleanSetting("Rotate", rotateDefault));
      Boolean targetsDefault = false;
      BooleanSetting targetsOption = new BooleanSetting("Targets", targetsDefault);
      targetsOption.markAsGroup();
      targetsOption.markVisibilityDependency();
      this.targetsSetting = this.add(targetsOption);
      Boolean dogsDefault = true;
      BooleanSetting dogsOption = new BooleanSetting("Dogs", dogsDefault);
      this.dogsSetting = this.add(dogsOption.dependsOn(this.targetsSetting));
      Boolean catsDefault = true;
      BooleanSetting catsOption = new BooleanSetting("Cats", catsDefault);
      this.catsSetting = this.add(catsOption.dependsOn(this.targetsSetting));
      Boolean parrotsDefault = true;
      BooleanSetting parrotsOption = new BooleanSetting("Parrots", parrotsDefault);
      this.parrotsSetting = this.add(parrotsOption.dependsOn(this.targetsSetting));
      Boolean donkeysDefault = true;
      BooleanSetting donkeysOption = new BooleanSetting("Donkeys", donkeysDefault);
      this.donkeysSetting = this.add(donkeysOption.dependsOn(this.targetsSetting));
      Boolean horsesDefault = true;
      BooleanSetting horsesOption = new BooleanSetting("Horses", horsesDefault);
      this.horsesSetting = this.add(horsesOption.dependsOn(this.targetsSetting));
      Boolean allaysDefault = false;
      BooleanSetting allaysOption = new BooleanSetting("Allays", allaysDefault);
      this.allaysSetting = this.add(allaysOption.dependsOn(this.targetsSetting));
      Boolean othersDefault = true;
      BooleanSetting othersOption = new BooleanSetting("Others", othersDefault);
      this.othersSetting = this.add(othersOption.dependsOn(this.targetsSetting));
      this.interactionTimer = new ElapsedTimer();
      this.breedingCooldowns = new ArrayList<>();
   }

   private static final class BreedingCooldown {
      private final LivingEntity entity;
      private int elapsedTicks;

      private BreedingCooldown(LivingEntity entity) {
         this.entity = entity;
      }
   }

}
