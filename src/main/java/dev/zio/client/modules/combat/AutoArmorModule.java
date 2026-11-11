/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.modules.movement.ElytraFlyModule.InfDurabilityChoice;
import dev.zio.client.modules.movement.ElytraFlyModule.ModeChoice;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.ScreenChangeEvent;
import dev.zio.client.events.MinecraftTickEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.gui.screens.ClientScreen;
import dev.zio.client.utils.inventory.ArmorSlot;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.movement.WallsVelocityHandler;
import dev.zio.client.managers.InventoryStateManager;
import dev.zio.client.utils.combat.DamageUtils;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.inventory.EnchantmentUtils;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.utils.movement.NewGrimElytraFlyHandler;
import dev.zio.client.managers.HoleManager;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.modules.movement.NoSlowModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.AutoArmorBlastChoice;
import dev.zio.client.settings.options.AutoArmorHelmetChoice;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AutoArmorModule extends Module {
   private static ElytraFlyModule elytraFlyModule;
   private static NoSlowModule noSlowModule;
   private final Setting<Integer> frequencySetting;
   private final Setting<Integer> delaySetting;
   public final Setting<Boolean> alternativeSetting;
   final Setting<Boolean> safeSetting;
   private final Setting<Integer> safeAmountSetting;
   final Setting<Boolean> inRangeSetting;
   private final Setting<Float> rangeSetting;
   private final Setting<Boolean> allowFriendsSetting;
   private final Setting<Boolean> preferencesSetting;
   final Setting<AutoArmorHelmetChoice> helmetSetting;
   private final Setting<Boolean> goldenBootsSetting;
   private final Setting<Float> piglinRangeSetting;
   private final Setting<Boolean> onlySafeSetting;
   private final Setting<AutoArmorBlastChoice> blastSetting;
   private final Setting<Boolean> bindingCurseSetting;
   private final Setting<Boolean> thornsSetting;
   public final Setting<Boolean> elytraSetting;
   private final Setting<Boolean> onlyAirSetting;
   private final Setting<Boolean> blastBootsSetting;
   private final ElapsedTimer swapDelayTimer;
   private final ElapsedTimer lastGlideTimer;
   private final ElapsedTimer safetyPredicateChangeTimer;
   private final ElapsedTimer heightPredicateChangeTimer;
   private final ElapsedTimer jumpToggleTimer;
   private boolean jumpToggleActive;
   private boolean previousJumpDown;
   private boolean previousSafetyPredicate;
   private boolean previousHeightPredicate;
   private boolean skipNextArmorUpdate;
   private double nearestAdultPiglinDistance;

   private boolean shouldPreferGoldenHelmet() {
      return (this.helmetSetting.getValue()).isGold() && this.isPiglinWithinRange();
   }

   private boolean shouldPreferElytraForCurrentFlight() {
      if (elytraFlyModule.isToggled()) {
         Setting<ModeChoice> modeOption = elytraFlyModule.modeSetting;
         if (modeOption.getValue() == ElytraFlyModule.ModeChoice.BOUNCE) {
            Setting<InfDurabilityChoice> durabilityOption = elytraFlyModule.infDurabilitySetting;

            return (durabilityOption.getValue()).isDisabled();
         }
      }

      if (EntityUtils.hasCollisionBelow(ClientAccess.zioMinecraft.player)) {
         return false;
      }

      if (PlayerStateUtils.isInLiquid(ClientAccess.zioMinecraft.player)) {
         if (!PlayerStateUtils.isFallFlying()) {
            if (ClientAccess.zioMinecraft.player.isInLava()) {
               if (!this.jumpToggleTimer.hasElapsed(200L)) {
                  return true;
               }
            }

            return false;
         }
      }

      return this.jumpToggleActive;
   }

   private void updateNearestAdultPiglinDistance() {
      Vec3 eyePosition = ClientAccess.zioMinecraft.player.getEyePosition();

      double nearestDistance = Double.MAX_VALUE;

      Iterator entities = ClientAccess.zioMinecraft.level.entitiesForRendering().iterator();

      while (entities.hasNext()) {
         Entity entity = (Entity)entities.next();
         if (entity instanceof Piglin piglin) {
            if (!piglin.isBaby()) {
               AABB bounds = entity.getBoundingBox();

               double piglinDistance = WorldUtils.distanceToBounds(eyePosition, bounds);

               nearestDistance = Math.min(nearestDistance, piglinDistance);
            }
         }
      }

      this.nearestAdultPiglinDistance = nearestDistance;
   }


   private static boolean isGoldenBootsEnabled(AutoArmorModule owner) {
      return owner.goldenBootsSetting.getValue() && owner.goldenBootsSetting.isVisible();
   }

   private boolean shouldRemoveHelmet() {
      if (!(this.helmetSetting.getValue()).isNone()) return false;

      return !this.onlySafeSetting.getValue() || this.previousSafetyPredicate && this.safetyPredicateChangeTimer.hasElapsed(500L);
   }

   private void updateArmorSafetyState() {
      if (ClientAccess.zioMinecraft.player.isFallFlying()) {
         this.lastGlideTimer.reset();
      }

      double height = ClientAccess.zioMinecraft.player.getY();
      boolean heightPredicate = WallsVelocityHandler.hasPlayerCollisionAtHeight(height);
      boolean previousSafety = this.previousSafetyPredicate;
      HoleManager holeManager = ClientContext.holeManager;
      if (previousSafety != holeManager.isInHole()) {
         this.safetyPredicateChangeTimer.reset();
      }
      if (this.previousHeightPredicate != heightPredicate) {
         this.heightPredicateChangeTimer.reset();
      }
      this.previousHeightPredicate = heightPredicate;
      HoleManager updatedHoleManager = ClientContext.holeManager;
      this.previousSafetyPredicate = updatedHoleManager.isInHole();
   }

   private boolean isPiglinWithinRange() {
      if (this.piglinRangeSetting.isAtMaximum()) {
         return true;
      }

      double piglinDistance = this.nearestAdultPiglinDistance;

      Float range = this.piglinRangeSetting.getValue();
      return piglinDistance <= range.floatValue();
   }

   private boolean hasRelevantPlayerInRange() {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      for (AbstractClientPlayer player : level.players()) {
         if (player != ClientAccess.zioMinecraft.player) {
            float distance = player.distanceTo(ClientAccess.zioMinecraft.player);
            Float range = this.rangeSetting.getValue();
            if (distance <= range) {
               SocialRegistry socialRegistry = ClientContext.socialRegistry;
               GameProfile profile = player.getGameProfile();
               String playerName = profile.name();
               if (!socialRegistry.isFriendName(playerName)) return true;

               if (!this.allowFriendsSetting.getValue()) return true;
            }
         }
      }
      return false;
   }

   private static void updateJumpToggle(AutoArmorModule owner) {
      KeyMapping jumpKey = ClientAccess.zioMinecraft.options.keyJump;
      boolean jumpDown = jumpKey.isDown() && ClientAccess.zioMinecraft.screen == null;

      if (ClientContext.playerStateTracker.getGroundedTicks() > 1) {
         owner.jumpToggleActive = false;
      } else if (jumpDown && !owner.previousJumpDown) {
         if (owner.jumpToggleActive) {
            owner.jumpToggleTimer.reset();
         }
         owner.jumpToggleActive = true;

         owner.lastGlideTimer.reset();
      }
      owner.previousJumpDown = jumpDown;
   }

   public void skipNextArmorUpdate() {
      this.skipNextArmorUpdate = true;
   }

   static {
      elytraFlyModule = (ElytraFlyModule)ClientContext.getModule(ElytraFlyModule.class);
      noSlowModule = (NoSlowModule)ClientContext.getModule(NoSlowModule.class);
   }

   private void performArmorUpdates() {
      if (this.skipNextArmorUpdate) {
         this.skipNextArmorUpdate = false;
         return;
      }
      this.updateNearestAdultPiglinDistance();
      this.updateArmorSafetyState();
      Screen currentScreen = ClientAccess.zioMinecraft.screen;

      if (!this.isArmorScreenAllowed(currentScreen)) return;
      ElapsedTimer delayTimer = this.swapDelayTimer;

      long delayMillis = (this.delaySetting.getValue()).intValue();
      if (!delayTimer.hasElapsed(delayMillis)) return;

      if (this.inRangeSetting.getValue()) {
         if (!this.hasRelevantPlayerInRange()) return;
      }
      List<ArmorSwapAction> armorChanges = this.collectArmorChanges();
      if (armorChanges.isEmpty()) return;
      int appliedChanges = 0;

      noSlowModule.prepareForInventoryAction();
      for (ArmorSwapAction change : armorChanges) {
         change.execute();
         appliedChanges++;

         Integer frequency = this.frequencySetting.getValue();
         if (appliedChanges >= frequency) break;
      }
      this.swapDelayTimer.reset();
   }

   private List<ArmorSwapAction> collectArmorChanges() {
      List<ArmorSwapAction> armorChanges = new ArrayList<>();
      for (ArmorSlot armorKind : ArmorSlot.values()) {
         Inventory equippedInventory = ClientAccess.zioMinecraft.player.getInventory();
         int equippedSlot = armorKind.getInventoryIndex();
         ItemStack equippedStack = equippedInventory.getItem(equippedSlot);
         ResourceKey bindingCurse = Enchantments.BINDING_CURSE;
         if (EnchantmentUtils.hasEnchantment(bindingCurse, equippedStack)) continue;
         double bestScore = this.scoreArmor(equippedStack, armorKind);
         int bestSlot = armorKind.getInventoryIndex();
         int candidateSlot = 0;
         while (true) {
            if (candidateSlot >= 36) {
               if (bestSlot != armorKind.getInventoryIndex()) {
                  if (bestScore != -1.0D) {
                     ArmorSwapAction change = new ArmorSwapAction(armorKind, bestSlot);

                     boolean alternative = this.alternativeSetting.getValue();
                     change.setAlternative(alternative);
                     armorChanges.add(change);
                  }
               }
               break;
            }

            ItemStack candidateStack = ClientAccess.zioMinecraft.player.getInventory().getItem(candidateSlot);
            double candidateScore = this.scoreArmor(candidateStack, armorKind);
            if (candidateScore > bestScore) {
               bestScore = candidateScore;
               bestSlot = candidateSlot;
            }
            candidateSlot++;
         }
      }
      if (this.shouldRemoveHelmet() && armorChanges.isEmpty()) {
         Inventory helmetInventory = ClientAccess.zioMinecraft.player.getInventory();
         ArmorSlot helmetKind = ArmorSlot.HELMET;
         int helmetSlot = helmetKind.getInventoryIndex();
         ItemStack helmetStack = helmetInventory.getItem(helmetSlot);
         if (!helmetStack.isEmpty()) {
            ResourceKey bindingCurse = Enchantments.BINDING_CURSE;
            if (!EnchantmentUtils.hasEnchantment(bindingCurse, helmetStack)) {
               if (!InventoryUtils.isInventoryFull()) {
                  ArmorSwapAction removal = new ArmorSwapAction(ArmorSlot.HELMET, -1);
                  removal.setQuickMove(true);
                  armorChanges.add(removal);
               }
            }
         }
      }
      return armorChanges;
   }

   private boolean shouldUseBlastProtection(ArmorSlot armorKind) {
      if (this.elytraSetting.getValue()) {
         if (this.blastBootsSetting.getValue() && armorKind == ArmorSlot.BOOTS) return true;
      }
      boolean heightPredicateSettled = false;
      if (!this.previousHeightPredicate) {
         if (this.heightPredicateChangeTimer.hasElapsed(150L)) heightPredicateSettled = true;
      }

      AutoArmorBlastChoice blastChoice = this.blastSetting.getValue();
      return switch (blastChoice) {
         case NONE -> false;
         case ALL -> true;
         case LEGS -> armorKind == ArmorSlot.LEGGINGS;
         case SMART -> armorKind == ArmorSlot.LEGGINGS || armorKind == ArmorSlot.BOOTS && heightPredicateSettled;
      };
   }

   private double scoreArmor(ItemStack stack, ArmorSlot armorKind) {
      float durability = InventoryUtils.getDurabilityPercent(stack);

      if (this.safeSetting.getValue()) {
         Integer minimumDurability = this.safeAmountSetting.getValue();
         if (durability < minimumDurability.intValue()) {
            return -1.0D;
         }
      }
      Item shieldItem = Items.SHIELD;
      if (stack.is(shieldItem)) {
         return -1.0D;
      }

      if (!this.bindingCurseSetting.getValue()) {
         ResourceKey bindingCurse = Enchantments.BINDING_CURSE;
         if (EnchantmentUtils.hasEnchantment(bindingCurse, stack)) {
            return -1.0D;
         }
      }
      Item elytraItem = Items.ELYTRA;
      if (stack.is(elytraItem) && armorKind == ArmorSlot.CHESTPLATE) {
         if (DamageUtils.getDurabilityPercent(stack) <= 1) {
            return -1.0D;
         }
         double elytraScore = 1.0D;

         Boolean preferElytra = this.elytraSetting.getValue();
         elytraPreference:
         if (preferElytra) {
            if (this.onlyAirSetting.getValue()) {
               if (!this.shouldPreferElytraForCurrentFlight()) break elytraPreference;
            }
            elytraScore *= 9999.0D;
         }
         if (stack.isEnchanted()) {
            ResourceKey unbreaking = Enchantments.UNBREAKING;
            elytraScore += EnchantmentUtils.getLevel(unbreaking, stack);
            ResourceKey mending = Enchantments.MENDING;
            elytraScore += EnchantmentUtils.getLevel(mending, stack);
         }

         if (elytraFlyModule.getActiveMode() instanceof NewGrimElytraFlyHandler flightMode) {
            if (flightMode.shouldFallWithChestplate()) {
               return -1.0D;
            }
         }
         return elytraScore;
      }
      DataComponentType<Equippable> equippableType = DataComponents.EQUIPPABLE;
      Object equippableComponent = stack.get(equippableType);
      if (equippableComponent instanceof Equippable equippable) {
         EntityType playerType = EntityType.PLAYER;
         if (equippable.canBeEquippedBy(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(playerType))) {
            if (!(stack.getItem() instanceof BlockItem)) {
               EquipmentSlot equipmentSlot = equippable.slot();
               int equipmentIndex = equipmentSlot.getIndex();
               if (equipmentIndex == armorKind.getArmorIndex()) {
                  double armorScore = 0.0;
                  if (this.shouldRemoveHelmet()) {
                     if (equippable.slot() == EquipmentSlot.HEAD) {
                        return -1.0D;
                     }
                  }

                  if ((this.helmetSetting.getValue()).isTurtle()) {
                     Item turtleHelmet = Items.TURTLE_HELMET;
                     if (stack.is(turtleHelmet)) {
                        armorScore = 9999.0D;
                     }
                  }
                  if (this.shouldPreferGoldenHelmet()) {
                     Item goldenHelmet = Items.GOLDEN_HELMET;
                     if (stack.is(goldenHelmet)) {
                        armorScore = 9999.0D;
                     }
                  }
                  if (isGoldenBootsEnabled(this)) {
                     if (this.isPiglinWithinRange()) {
                        Item goldenBoots = Items.GOLDEN_BOOTS;
                        if (stack.is(goldenBoots)) {
                           armorScore = 9999.0D;
                        }
                     }
                  }
                  DataComponentType<ItemAttributeModifiers> modifiersType = DataComponents.ATTRIBUTE_MODIFIERS;
                  Object modifiersComponent = stack.get(modifiersType);
                  if (modifiersComponent instanceof ItemAttributeModifiers modifiers) {
                     Iterator modifierEntries = modifiers.modifiers().iterator();
                     while (modifierEntries.hasNext()) {
                        net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry = (net.minecraft.world.item.component.ItemAttributeModifiers.Entry)modifierEntries.next();
                        if (entry.attribute() != Attributes.ARMOR_TOUGHNESS) {
                           if (entry.attribute() != Attributes.ARMOR) continue;
                        }
                        AttributeModifier modifier = entry.modifier();
                        double amount = modifier.amount();
                        armorScore += amount * 10.0D;
                     }
                  }
                  if (stack.isEnchanted()) {
                     ResourceKey preferredProtection = this.shouldUseBlastProtection(armorKind) ? Enchantments.BLAST_PROTECTION : Enchantments.PROTECTION;
                     ItemEnchantments enchantments = stack.getEnchantments();
                     Iterator enchantmentEntries = enchantments.entrySet().iterator();
                     while (enchantmentEntries.hasNext()) {
                        Entry entry = (Entry)enchantmentEntries.next();
                        int level = entry.getIntValue();
                        Holder curseHolder = (Holder)entry.getKey();
                        TagKey curseTag = EnchantmentTags.CURSE;
                        if (!curseHolder.is(curseTag)) {
                           Holder thornsHolder = (Holder)entry.getKey();
                           ResourceKey thornsEnchantment = Enchantments.THORNS;
                           boolean thorns = thornsHolder.is(thornsEnchantment);

                           if (((Holder)entry.getKey()).is(preferredProtection)) {
                              armorScore += level * 3;
                           } else if (thorns) {
                              Boolean allowThorns = this.thornsSetting.getValue();
                              double penalty;
                              if (allowThorns) {
                                 penalty = 0.0;
                              } else {
                                 penalty = 1.0D;
                              }
                              armorScore -= penalty;
                           } else {
                              double enchantmentLevel = level;
                              armorScore += enchantmentLevel * 0.1D;
                           }
                        }
                     }
                  }
                  return armorScore;
               }
            }
         }
      }
      return -1.0D;
   }

   @EventHandler(priority = 100)
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      this.performArmorUpdates();

      if (PlayerStateUtils.isWearingElytra(ClientAccess.zioMinecraft.player)) {
         if (!ClientAccess.zioMinecraft.player.isFallFlying()) {
            if (!EntityUtils.hasCollisionBelow(ClientAccess.zioMinecraft.player)) {
               if (!this.swapDelayTimer.hasElapsed(300L)) {
                  if (!this.lastGlideTimer.hasElapsed(500L)) {
                     PacketSender.sendStartFallFlying();

                     ClientAccess.zioMinecraft.player.startFallFlying();
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onMinecraftSetScreenEvent(
      ScreenChangeEvent.Before event
   ) {
      if (!((ClientAccess)this).isWorldUnavailable()) {
         if (event.getNewScreen() == null) {
            if (RotationManager.is2b2tMode()) {
               if (!InventoryStateManager.isClickRateLimited(ClientContext.inventoryStateManager)) {
                  if (event.getPreviousScreen() instanceof AbstractContainerScreen previousContainer) {
                     AbstractContainerMenu previousMenu = previousContainer.getMenu();
                     if (previousMenu == ClientAccess.zioMinecraft.player.inventoryMenu) {
                        return;
                     }
                  }

                  ClientContext.taskScheduler.scheduleRotationTask(() -> {
                     InventoryUtils.clickSlot(0, 0, ContainerInput.PICKUP);
                     InventoryUtils.clickSlot(0, 0, ContainerInput.PICKUP);
                  }, 0);
               }
            }
         }
      }
   }

   @EventHandler
   private void onMinecraftTick(MinecraftTickEvent event) {
      if (!((ClientAccess)this).isWorldUnavailable()) {
         updateJumpToggle(this);
      }
   }

   private boolean isArmorScreenAllowed(Screen screen) {
      if (screen instanceof ClientScreen) return true;
      return screen instanceof AbstractContainerScreen ? screen instanceof InventoryScreen : true;
   }

   public static void bindModuleRegistry(ModuleRegistry registry) {
      elytraFlyModule = (ElytraFlyModule)registry.getModule(
         ElytraFlyModule.class
      );
      noSlowModule = (NoSlowModule)registry.getModule(NoSlowModule.class);
   }

   public AutoArmorModule() {
      super("AutoArmor", "Equips the best armor in your inventory for you.", ModuleCategory.COMBAT, new String[0]);
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 4));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 50, 0, 500).withUnit("ms"));
      this.alternativeSetting = this.add(new BooleanSetting("Alternative", false));
      this.safeSetting = this.add(new BooleanSetting("Safe", false).markVisibilityDependency());
      Predicate<Object> safeAmountVisibility = unused -> {
         Setting<Boolean> setting = this.safeSetting;
         return setting.isExpanded();
      };
      this.safeAmountSetting = this.add(new NumberSetting<>("SafeAmount", 5, 1, 100, safeAmountVisibility).withUnit("%"));
      this.inRangeSetting = this.add(new BooleanSetting("InRange", false).markVisibilityDependency());
      Predicate<Object> rangeVisibility = unused -> {
         Setting<Boolean> setting = this.inRangeSetting;
         return setting.isExpanded();
      };
      this.rangeSetting = this.add(new NumberSetting<>("Range", 8.0F, 1.0F, 16.0F, rangeVisibility).withUnit("m"));
      Predicate<Object> friendsVisibility = unused -> {
         Setting<Boolean> setting = this.inRangeSetting;
         return setting.isExpanded();
      };
      this.allowFriendsSetting = this.add(new BooleanSetting("AllowFriends", true, friendsVisibility));
      this.preferencesSetting = this.add(new BooleanSetting("Preferences", false).markAsGroup().markVisibilityDependency());
      this.helmetSetting = this.add(new EnumSetting<>("Helmet", AutoArmorHelmetChoice.BEST).dependsOn(this.preferencesSetting));
      Predicate<Object> goldenBootsVisibility = unused -> {
         Setting<AutoArmorHelmetChoice> helmetOption = this.helmetSetting;
         return helmetOption.getValue() != AutoArmorHelmetChoice.GOLD;
      };
      this.goldenBootsSetting = this.add(new BooleanSetting("GoldenBoots", false, goldenBootsVisibility).dependsOn(this.preferencesSetting));
      this.piglinRangeSetting = this.add(new NumberSetting<>("PiglinRange", 8.0F, 1.0F, 16.0F).withUnit("m").dependsOn(this.preferencesSetting));
      this.onlySafeSetting = this.add(new BooleanSetting("OnlySafe", false).dependsOn(this.preferencesSetting));
      this.blastSetting = this.add(new EnumSetting<>("Blast", AutoArmorBlastChoice.LEGS).dependsOn(this.preferencesSetting));
      this.bindingCurseSetting = this.add(new BooleanSetting("BindingCurse", false).dependsOn(this.preferencesSetting));
      this.thornsSetting = this.add(new BooleanSetting("Thorns", false).dependsOn(this.preferencesSetting));
      this.elytraSetting = this.add(new BooleanSetting("Elytra", false).markVisibilityDependency().dependsOn(this.preferencesSetting));
      this.onlyAirSetting = this.add(new BooleanSetting("OnlyAir", false).dependsOn(this.elytraSetting, this.preferencesSetting));
      this.blastBootsSetting = this.add(new BooleanSetting("BlastBoots", false).dependsOn(this.elytraSetting, this.preferencesSetting));
      this.swapDelayTimer = new ElapsedTimer();
      this.lastGlideTimer = new ElapsedTimer();
      this.safetyPredicateChangeTimer = new ElapsedTimer();
      this.heightPredicateChangeTimer = new ElapsedTimer();
      this.jumpToggleTimer = new ElapsedTimer();
      Setting<Float> piglinRange = this.piglinRangeSetting;
      SettingLabelCondition maximumBoundary = SettingLabelCondition.MAX;
      piglinRange.setBoundaryLabel("None", maximumBoundary);
      this.piglinRangeSetting.addVisibilityPredicate( unused -> {
         Setting<AutoArmorHelmetChoice> helmetOption = this.helmetSetting;
         return (helmetOption.getValue()).isGold() || isGoldenBootsEnabled(this);
      });
      this.onlySafeSetting.addVisibilityPredicate( unused -> {
         Setting<AutoArmorHelmetChoice> helmetOption = this.helmetSetting;
         return (helmetOption.getValue()).isNone();
      });
      this.blastSetting.setChangeCallback(this.swapDelayTimer::clear);
      this.elytraSetting.setChangeCallback(() -> {
         this.swapDelayTimer.clear();

         this.lastGlideTimer.reset();
      });
      this.helmetSetting.setChangeCallback(this.swapDelayTimer::clear);
   }
}
