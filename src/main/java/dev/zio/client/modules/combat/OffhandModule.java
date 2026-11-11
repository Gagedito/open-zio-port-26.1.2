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
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.zio.client.managers.ServerStateManager;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.inventory.ItemUtils;
import dev.zio.client.utils.world.BlockStateUtils;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.movement.NoSlowModule;
import dev.zio.client.modules.movement.NoFallModule;
import dev.zio.client.modules.player.NoInteractModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.NoSlowStrictInventoryChoice;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
public class OffhandModule extends Module implements LethalDamageTracker.LethalCheckPolicy {
   private static AutoCrystalModule autoCrystalModule = (AutoCrystalModule)ClientContext.getModule(AutoCrystalModule.class);
   private static OffhandModule offhandModule = (OffhandModule)ClientContext.getModule(OffhandModule.class);
   private static NoInteractModule noInteractModule = (NoInteractModule)ClientContext.getModule(NoInteractModule.class);
   private static AntiCheatModule antiCheatModule = (AntiCheatModule)ClientContext.getModule(AntiCheatModule.class);
   private static NoSlowModule noSlowModule = (NoSlowModule)ClientContext.getModule(NoSlowModule.class);
   private static MainhandModule mainhandModule = (MainhandModule)ClientContext.getModule(MainhandModule.class);
   private static NoFallModule noFallModule = (NoFallModule)ClientContext.getModule(NoFallModule.class);
   public final Setting<OffhandItemChoice> itemSetting;
   final Setting<Set<Item>> customSetting;
   private final Setting<Integer> delaySetting;
   final Setting<Float> healthSetting;
   final Setting<Boolean> lethalSetting;
   final Setting<Boolean> elytraSetting;
   private final Setting<Boolean> directSwapSetting;
   private final Setting<Boolean> containersSetting;
   final Setting<Boolean> gappleBindSetting;
   private final Setting<Float> safeSetting;
   private final Setting<Float> safeInBlocksSetting;
   private final Setting<Boolean> axeSetting;
   private final Setting<Boolean> pickaxeSetting;
   private final Setting<Boolean> crystalsSetting;
   private final Setting<Boolean> totemsSetting;
   private final Setting<Boolean> crappleSetting;
   private static final ElapsedTimer RECENT_SWAP_TIMER = new ElapsedTimer();
   private final ElapsedTimer swapDelayTimer;
   private final ElapsedTimer totemPopTimer;
   private int previousSwapSlot;
   private Class previousMenuClass;
   private boolean tickAllowsSwap;
   private boolean deathReportSent;
   private boolean hasInventoryTotem;
   private final LethalDamageTracker lethalDamageTracker;

   public boolean isGappleBindSafeInBlocks() {
      return isGappleBindSafeInBlocks(this);
   }

   private static boolean isGappleBindSafeInBlocks(OffhandModule owner) {
      if (!owner.gappleBindSetting.getValue()) return false;

      float threshold = owner.safeInBlocksSetting.getValue();
      if (threshold >= EntityUtils.getPlayerHealthWithAbsorption()) return false;

      Set surroundingBlockPositions = PlayerStateUtils.getOccupiedBlockPositions((net.minecraft.world.entity.LivingEntity)ClientAccess.zioMinecraft.player);
      int qualifyingBlockCount = 0;
      Iterator blockIterator = surroundingBlockPositions.iterator();
      while (blockIterator.hasNext()) {
         BlockPos blockPos = (BlockPos)blockIterator.next();

         BlockState blockState = ClientAccess.zioMinecraft.level.getBlockState(blockPos);
         if (BlockStateUtils.isBlastResistantBreakable(blockState)) qualifyingBlockCount++;
         Block bedrock = Blocks.BEDROCK;
         if (blockState.is(bedrock)) return true;
      }
      return qualifyingBlockCount > 2;
   }
   @Override
   public String getInfo() {
      return (this.itemSetting.getValue()).toString();
   }
   @Override
   public void onToggle() {
      this.previousSwapSlot = -1;
   }
   public boolean canUseGappleBind(Item item) {
      return canUseGappleBind(this, item);
   }
   private static boolean canUseGappleBind(OffhandModule owner, Item item) {
      OffhandItemChoice gappleChoice = OffhandItemChoice.Gapple;

      boolean useCrapple = owner.crappleSetting.getValue();
      Item gappleItem = gappleChoice.getItem(useCrapple);

      if (ClientAccess.zioMinecraft.player.isUsingItem()) {
         if (ClientAccess.zioMinecraft.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            if (ClientAccess.zioMinecraft.player.getUseItem().is(gappleItem)) {
               if (owner.itemSetting.getValue() != OffhandItemChoice.Crystal) {
                  if (!owner.isMainhandManagingTotemOrFood()) return true;
               }
            }
         }
      }
      ItemStack defaultItemStack = item.getDefaultInstance();
      if (ItemUtils.isSword(defaultItemStack) && !(item instanceof AxeItem)) return true;
      if (item instanceof AxeItem) {
         if (owner.axeSetting.getValue()) return true;
      }
      ItemStack defaultToolStack = item.getDefaultInstance();
      if (defaultToolStack.is(ItemTags.PICKAXES)) {
         if (owner.pickaxeSetting.getValue()) return true;
      }
      if (item == Items.TOTEM_OF_UNDYING) {
         if (owner.totemsSetting.getValue()) return true;
      }
      if (item == Items.END_CRYSTAL) {
         if (owner.crystalsSetting.getValue()) return true;
      }
      return false;
   }
   public static boolean isRecentSwapWindowActive() {
      if (RECENT_SWAP_TIMER.hasElapsed(50L)) return false;

      return offhandModule.isToggled();
   }
   public OffhandItemChoice applyGappleBind(OffhandItemChoice incomingChoice) {
      return applyGappleBind(this, incomingChoice);
   }
   private static OffhandItemChoice applyGappleBind(OffhandModule owner, OffhandItemChoice incomingChoice) {
      if (incomingChoice != null) {
         boolean useCrapple = owner.crappleSetting.getValue();
         if (incomingChoice.getItem(useCrapple) instanceof ShieldItem) return incomingChoice;
      }
      HitResult hitResult = ClientAccess.zioMinecraft.hitResult;
      if (WorldUtils.isInteractive(hitResult)) {
         if (!ClientAccess.zioMinecraft.player.isShiftKeyDown()) {
            if (!noInteractModule.isToggled()) return incomingChoice;
         }
      }
      float currentHealthWithAbsorption = EntityUtils.getPlayerHealthWithAbsorption();

      Item mainhandItem = ClientAccess.zioMinecraft.player.getMainHandItem().getItem();

      boolean gappleHealthGate = owner.safeSetting.getValue() < currentHealthWithAbsorption;
      Float blockHealthThreshold = owner.safeInBlocksSetting.getValue();
      boolean blockSafetyApplied = false;
      if (blockHealthThreshold < currentHealthWithAbsorption) {
         if (!owner.safeInBlocksSetting.isAtMinimum()) {
            boolean blockSafe = owner.isGappleBindSafeInBlocks();
            if (!blockSafe) {
               if (ClientAccess.zioMinecraft.player.isUsingItem()) {
                  blockSafe = ClientAccess.zioMinecraft.player.getUsedItemHand() == InteractionHand.OFF_HAND;
               }
            }
            gappleHealthGate = blockSafe;
            blockSafetyApplied = true;
         }
      }
      if (!blockSafetyApplied && mainhandItem == Items.TOTEM_OF_UNDYING) gappleHealthGate = true;
      if (!owner.hasInventoryTotem) gappleHealthGate = true;

      if (owner.gappleBindSetting.getValue()) {
         KeyMapping useKey = ClientAccess.zioMinecraft.options.keyUse;
         if (useKey.isDown() && gappleHealthGate) {
            if (owner.canUseGappleBind(mainhandItem) && ClientAccess.zioMinecraft.screen == null) {
               if (owner.itemSetting.getValue() != OffhandItemChoice.Gapple) return OffhandItemChoice.Gapple;
            }
         }
      }
      return incomingChoice;
   }
   @EventHandler
   private void onPlayerTickStart(LocalPlayerPreTickEvent event) {
      updateTotemAvailability(this);
      if (!shouldUseStrictTick()) return;
      this.tickAllowsSwap = true;
      this.updateOffhand();
   }
   void swapToChosenItem() {
      Class menuClass = ClientAccess.zioMinecraft.player.containerMenu.getClass();
      OffhandItemChoice chosenItemChoice = this.selectOffhandChoice();
      int containerId = ClientAccess.zioMinecraft.player.containerMenu.containerId;

      boolean allowCrapple = this.crappleSetting.getValue();
      int chosenContainerSlot = chosenItemChoice.findContainerSlot(allowCrapple);
      boolean canReusePreviousSlot = !(ClientAccess.zioMinecraft.screen instanceof AbstractContainerScreen)
         || ClientAccess.zioMinecraft.screen instanceof InventoryScreen;
      if (this.previousSwapSlot != -1 && chosenContainerSlot != -1 && canReusePreviousSlot
         && menuClass == this.previousMenuClass) {
         int previousSlot = this.previousSwapSlot;
         AbstractContainerMenu previousMenu = ClientAccess.zioMinecraft.player.containerMenu;
         Slot previousMenuSlot = previousMenu.getSlot(previousSlot);
         ItemStack previousSlotStack = previousMenuSlot.getItem();
         AbstractContainerMenu chosenMenu = ClientAccess.zioMinecraft.player.containerMenu;
         Slot chosenMenuSlot = chosenMenu.getSlot(chosenContainerSlot);
         ItemStack chosenSlotStack = chosenMenuSlot.getItem();
         Item previousItem = previousSlotStack.getItem();
         if (chosenSlotStack.is(previousItem)) chosenContainerSlot = this.previousSwapSlot;
      }
      if (chosenContainerSlot == -1) return;

      if (!this.containersSetting.getValue() && (ClientAccess.zioMinecraft.screen instanceof ShulkerBoxScreen
         || ClientAccess.zioMinecraft.screen instanceof ContainerScreen)) return;
      NonNullList menuSlots = ClientAccess.zioMinecraft.player.containerMenu.slots;
      if (chosenContainerSlot >= menuSlots.size()) return;

      ItemStack carriedStackBeforeSwap = InventoryUtils.getCarriedStack();
      AbstractContainerMenu targetMenu = ClientAccess.zioMinecraft.player.containerMenu;
      Slot targetSlot = targetMenu.getSlot(chosenContainerSlot);
      ItemStack targetSlotStack = targetSlot.getItem();
      DataComponentType targetFoodComponent = DataComponents.FOOD;
      if (targetSlotStack.has(targetFoodComponent) && !this.tickAllowsSwap) return;

      this.beginInventorySwap();
      if (!carriedStackBeforeSwap.isEmpty()) {
         if (!InventoryUtils.isInventoryFull()) {
            Item emptyItem = Items.AIR;
            int emptyContainerSlot = InventoryUtils.findMenuSlot(emptyItem);
            ContainerInput pickupClick = ContainerInput.PICKUP;
            InventoryUtils.clickSlot(emptyContainerSlot, 0, pickupClick);
         }
      }

      Boolean directSwapEnabled = this.directSwapSetting.getValue();
      boolean useDirectSwap = false;
      if (directSwapEnabled) {
         ItemStack carriedStackForDirectSwap = InventoryUtils.getCarriedStack();
         useDirectSwap = carriedStackForDirectSwap.isEmpty();
      }
      if (useDirectSwap) {
         this.previousSwapSlot = chosenContainerSlot;
         MultiPlayerGameMode swapGameMode = ClientAccess.zioMinecraft.gameMode;
         ContainerInput swapClick = ContainerInput.SWAP;
         LocalPlayer swapPlayer = ClientAccess.zioMinecraft.player;
         swapGameMode.handleContainerInput(containerId, chosenContainerSlot, 40, swapClick, swapPlayer);

         this.swapDelayTimer.reset();
      } else {
         this.swapSlotToOffhand(chosenContainerSlot);
         this.previousSwapSlot = chosenContainerSlot;
      }
      this.previousMenuClass = menuClass;

      RECENT_SWAP_TIMER.reset();
      this.endInventorySwap();
      KeyMapping useKey = ClientAccess.zioMinecraft.options.keyUse;
      if (KeyUtils.isPhysicallyDown(useKey)) {
         DataComponentType swapFoodComponent = DataComponents.FOOD;
         if (targetSlotStack.has(swapFoodComponent)) {
            if (ClientAccess.zioMinecraft.player.isUsingItem()) {
               if (ClientAccess.zioMinecraft.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                  ClientAccess.zioMinecraft.player.releaseUsingItem();
               }
            }
            MultiPlayerGameMode gameModeForUse = ClientAccess.zioMinecraft.gameMode;
            LocalPlayer playerForUse = ClientAccess.zioMinecraft.player;
            InteractionHand offhand = InteractionHand.OFF_HAND;
            gameModeForUse.useItem(playerForUse, offhand);
         }
      }
   }

   private boolean canUseCurrentMenu() {
      if (this.directSwapSetting.getValue()) return true;
      return !(ClientAccess.zioMinecraft.screen instanceof CreativeModeInventoryScreen)
         && ClientAccess.zioMinecraft.player.inventoryMenu == ClientAccess.zioMinecraft.player.containerMenu
         && !(ClientAccess.zioMinecraft.screen instanceof ContainerScreen);
   }
   private void swapSlotToOffhand(int containerSlot) {
      ElapsedTimer delayTimer = this.swapDelayTimer;

      long delayMillis = (this.delaySetting.getValue()).intValue();
      if (!delayTimer.hasElapsed(delayMillis)) return;
      NonNullList menuSlots = ClientAccess.zioMinecraft.player.containerMenu.slots;
      int menuSlotCount = menuSlots.size();
      if (menuSlotCount <= 45) return;
      InventoryUtils.swapSlotsWithPickup(containerSlot, 45);

      this.swapDelayTimer.reset();
   }
   private static boolean shouldUseStrictTick() {
      if (RotationManager.isSilentRotationMode()) return false;

      return noSlowModule.getStrictInventoryMode() == NoSlowStrictInventoryChoice.GRIM_STRICT;
   }
   private MutableComponent withGameLogTooltip(MutableComponent component) {
      return component.withStyle(style -> style.withHoverEvent(new ShowText(Component.literal("More info in the game log."))));
   }
   @EventHandler(priority = 100)
   private void onClientTick(MinecraftRunTickEvent event) {
      if (shouldUseStrictTick()) return;
      this.tickAllowsSwap = event.hasPendingTicks();
      this.updateOffhand();
   }

   @EventHandler
   private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      if (event.getHand() != InteractionHand.MAIN_HAND) return;
      Object noIncomingChoice = null;
      if (this.applyGappleBind((OffhandItemChoice)noIncomingChoice) == null) return;

      if (antiCheatModule.is2b2tEnabled()) return;
      MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      InteractionHand offhand = InteractionHand.OFF_HAND;
      gameMode.useItem(player, offhand);
      event.cancel();
   }
   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundEntityEventPacket entityEventPacket) {
         if (!this.shouldDiscardTotem()) {
            if (this.canUseCurrentMenu()) {
               if (entityEventPacket.getEntity(ClientAccess.zioMinecraft.level) == ClientAccess.zioMinecraft.player) {
                  byte entityEventId = entityEventPacket.getEventId();
                  if (entityEventId == 35) {
                     ItemStack mainhandStack = ClientAccess.zioMinecraft.player.getMainHandItem();
                     Item mainhandTotemItem = Items.TOTEM_OF_UNDYING;
                     if (mainhandStack.is(mainhandTotemItem)) {
                        LocalPlayer shrinkingPlayer = ClientAccess.zioMinecraft.player;

                        shrinkingPlayer.getMainHandItem().shrink(1);
                     } else {
                        ItemStack offhandStack = ClientAccess.zioMinecraft.player.getOffhandItem();
                        Item offhandTotemItem = Items.TOTEM_OF_UNDYING;
                        if (offhandStack.is(offhandTotemItem)) {
                           LocalPlayer shrinkingPlayer = ClientAccess.zioMinecraft.player;

                           shrinkingPlayer.getOffhandItem().shrink(1);
                        }
                     }

                     this.totemPopTimer.reset();
                     if (!shouldUseStrictTick()) {
                        this.swapToChosenItem();
                     }
                  }
               }
            }
         }
      }
      if (event.getPacket() instanceof ClientboundAddEntityPacket addEntityPacket) {
         if (this.lethalDamageTracker.isLethalCheckEnabled()) {
            if (addEntityPacket.getType() == EntityType.END_CRYSTAL) {
               Vec3 crystalPosition = new Vec3(addEntityPacket.getX(), addEntityPacket.getY(), addEntityPacket.getZ());

               if (this.lethalDamageTracker.isCrystalLethal(crystalPosition)) {
                  if (!shouldUseStrictTick()) {
                     this.swapToChosenItem();
                  }
               }
            }
         }
      }
      if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket) {
         ItemStack offhandStackAtDeath = ClientAccess.zioMinecraft.player.getOffhandItem();
         Item totemItem = Items.TOTEM_OF_UNDYING;
         if (!offhandStackAtDeath.is(totemItem)) return;
         this.reportDeathDiagnostics();
      }
   }
   private boolean shouldDiscardTotem() {
      if (!autoCrystalModule.isToggled()) return false;

      if (!autoCrystalModule.forceSuicideSetting.getValue()) return false;

      return this.itemSetting.getValue() == OffhandItemChoice.Totem;
   }
   private void reportDeathDiagnostics() {
      String moduleName = this.getName();
      int moduleNameHash = moduleName.hashCode();
      int messageId = Math.abs(moduleNameHash) * -1 - 1;
      MessageSignature messageSignature = ChatUtils.createSignature(messageId);

      long millisSinceTotemPop = this.totemPopTimer.elapsedMillis();

      int latencyMillis = ClientContext.serverStateManager.getLatencyMillis();
      if (this.deathReportSent) return;
      String diagnosticText = "Since last pop: %dms. Latency: %dms.".formatted(millisSinceTotemPop, latencyMillis);
      MutableComponent diagnosticComponent = Component.literal(diagnosticText);
      if (latencyMillis <= millisSinceTotemPop) {
         if (millisSinceTotemPop >= 25L) {
            if (this.isServerLagging()) {
               if (millisSinceTotemPop < 200L) {
                  MutableComponent serverLagComponent = Component.literal(" Possible death reasons: Server lag*");
                  Component serverLagReason = this.withGameLogTooltip(serverLagComponent);
                  diagnosticComponent.append(serverLagReason);
                  ChatUtils.MessagePriority serverLagPriority = ChatUtils.MessagePriority.HIGH;
                  ChatUtils.printChat(diagnosticComponent, messageSignature, serverLagPriority);
                  Logger serverLagLogger = ClientAccess.logger;
                  serverLagLogger.warn("*Death was most likely caused by server lagging for too long.");
                  this.deathReportSent = true;
                  return;
               }
            }
            return;
         }
      }

      MutableComponent damageOverrideComponent = Component.literal(" Possible death reasons: Damage override/Lag spike.*");
      Component damageOverrideReason = this.withGameLogTooltip(damageOverrideComponent);
      diagnosticComponent.append(damageOverrideReason);
      ChatUtils.MessagePriority damageOverridePriority = ChatUtils.MessagePriority.HIGH;
      ChatUtils.printChat(diagnosticComponent, messageSignature, damageOverridePriority);
      Logger damageOverrideLogger = ClientAccess.logger;
      damageOverrideLogger.warn("*Death was most likely caused by you taking damage before the server receives the update slot packet, making you die holding a totem client-side.");
      this.deathReportSent = true;
   }
   void endInventorySwap() {
      ClientContext.inventoryStateManager.setClickBatching(false);
      if (!(ClientAccess.zioMinecraft.screen instanceof AbstractContainerScreen)) {
         ClientContext.inventoryStateManager.close();
      }
   }
   void beginInventorySwap() {
      ClientContext.inventoryStateManager.setClickBatching(true);

      noSlowModule.prepareForInventoryAction();
   }
   private boolean isServerLagging() {
      int latencyMillis = ClientContext.serverStateManager.getLatencyMillis();
      ServerStateManager packetTimestampTracker = ClientContext.serverStateManager;
      ServerStateManager.PacketActivity anyPacketType = ServerStateManager.PacketActivity.ANY;
      long lastPacketTimestamp = packetTimestampTracker.getLastPacketTime(anyPacketType);
      long timeSinceAnyPacket = System.currentTimeMillis() - lastPacketTimestamp;
      long latencyForComparison = latencyMillis;
      if (timeSinceAnyPacket > latencyForComparison * 2L) return true;

      return ClientContext.serverStateManager.hasStablePacketFlowFor(500L);
   }
   boolean isFallDistanceSafe() {
      if (!this.lethalSetting.getValue()) return true;

      if (ClientAccess.zioMinecraft.player.getAbilities().invulnerable) return true;

      if (ClientAccess.zioMinecraft.player.hasEffect(MobEffects.SLOW_FALLING)) return true;

      if (noFallModule.isToggled()) return true;
      float trackedDownwardDistance = PlayerStateUtils.getTrackedFallDistance();
      return trackedDownwardDistance < 16.0F;
   }

   @Override
   public boolean isLethalCheckEnabled() {
      ItemStack mainhandStack = ClientAccess.zioMinecraft.player.getMainHandItem();
      Item totemItem = Items.TOTEM_OF_UNDYING;
      if (mainhandStack.is(totemItem)) return false;

      return this.lethalSetting.getValue();
   }
   private void updateOffhand() {
      if (this.isWorldUnavailable()) return;
      if (ClientAccess.zioMinecraft.player.tickCount <= 1) return;

      if (ClientAccess.zioMinecraft.player.isAlive()) this.deathReportSent = false;

      this.lethalDamageTracker.update();
      if (this.shouldDiscardTotem()) {
         ItemStack offhandStack = ClientAccess.zioMinecraft.player.getOffhandItem();
         Item totemItem = Items.TOTEM_OF_UNDYING;
         if (offhandStack.is(totemItem) && this.tickAllowsSwap) {
            int discardContainerId = ClientAccess.zioMinecraft.player.containerMenu.containerId;
            MultiPlayerGameMode discardGameMode = ClientAccess.zioMinecraft.gameMode;
            ContainerInput discardClickType = ContainerInput.THROW;
            LocalPlayer playerForDiscard = ClientAccess.zioMinecraft.player;
            discardGameMode.handleContainerInput(discardContainerId, 45, 1, discardClickType, playerForDiscard);
         }
         return;
      }
      if (!this.canUseCurrentMenu()) return;
      ElapsedTimer delayTimerForCheck = this.swapDelayTimer;

      long delayMillis = (this.delaySetting.getValue()).intValue();
      if (!delayTimerForCheck.hasElapsed(delayMillis)) return;
      this.swapToChosenItem();
   }
   private OffhandItemChoice selectOffhandChoice() {
      if (autoCrystalModule.isToggled()) {
         if (autoCrystalModule.forceSuicideSetting.getValue()) {
            if (this.itemSetting.getValue() != OffhandItemChoice.Totem) return OffhandItemChoice.Crystal;
         }
      }

      boolean useCrapple = this.crappleSetting.getValue();
      OffhandItemChoice selectedChoice = OffhandItemChoice.Totem;

      if (this.lethalDamageTracker.shouldHoldTotem() && this.hasInventoryTotem) return OffhandItemChoice.Totem;
      float currentHealthWithAbsorption = EntityUtils.getPlayerHealthWithAbsorption();
      Float healthThreshold = this.healthSetting.getValue();
      if (currentHealthWithAbsorption > healthThreshold) {
         if (this.isFallDistanceSafe()) {
            Predicate configuredItemPredicate = (java.util.function.Predicate<net.minecraft.world.item.ItemStack>)(stack -> {
               Item configuredItem = (this.itemSetting.getValue()).getItem(useCrapple);
               return stack.is(configuredItem);
            });
            if (InventoryUtils.countItems(configuredItemPredicate) > 0) {
               selectedChoice = this.itemSetting.getValue();
            }
         }
      }

      if (this.elytraSetting.getValue()) {
         if (PlayerStateUtils.isLocalWearingElytra()) selectedChoice = OffhandItemChoice.Totem;
      }
      if (!this.hasInventoryTotem) {
         selectedChoice = this.itemSetting.getValue();
      }
      OffhandItemChoice choiceBeforeGappleBind = selectedChoice;
      return this.applyGappleBind(choiceBeforeGappleBind);
   }
   boolean isMainhandManagingTotemOrFood() {
      if (!mainhandModule.isToggled()) return false;

      int selectedHotbarSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
      MainhandModule mainhandForSlot = mainhandModule;
      if (selectedHotbarSlot != mainhandForSlot.getConfiguredHotbarSlot()) return false;

      ItemStack mainhandStack = ClientAccess.zioMinecraft.player.getMainHandItem();
      Item totemItem = Items.TOTEM_OF_UNDYING;
      if (mainhandStack.is(totemItem)) return true;
      DataComponentType mainhandFoodComponent = DataComponents.FOOD;
      return mainhandStack.has(mainhandFoodComponent);
   }
   private static void updateTotemAvailability(OffhandModule owner) {
      if (owner.isWorldUnavailable()) return;
      boolean totemFound = false;
      NonNullList inventorySlots = ClientAccess.zioMinecraft.player.inventoryMenu.slots;
      Iterator slotIterator = inventorySlots.iterator();
      while (slotIterator.hasNext()) {
         ItemStack slotStack = ((Slot)slotIterator.next()).getItem();
         Item totemItem = Items.TOTEM_OF_UNDYING;
         if (slotStack.is(totemItem)) {
            totemFound = true;
            break;
         }
      }
      owner.hasInventoryTotem = totemFound;
   }
   public static void bindModuleConnections(ModuleRegistry modules) {
      autoCrystalModule = (AutoCrystalModule)modules.getModule(
         AutoCrystalModule.class
      );
      offhandModule = (OffhandModule)modules.getModule(
         OffhandModule.class
      );
      noInteractModule = (NoInteractModule)modules.getModule(
         NoInteractModule.class
      );
      antiCheatModule = (AntiCheatModule)modules.getModule(
         AntiCheatModule.class
      );
      noSlowModule = (NoSlowModule)modules.getModule(
         NoSlowModule.class
      );
      mainhandModule = (MainhandModule)modules.getModule(
         MainhandModule.class
      );
      noFallModule = (NoFallModule)modules.getModule(
         NoFallModule.class
      );
      OffhandItemChoice.bindModuleRegistry(modules);
   }
   public OffhandModule() {
      super("Offhand", "Manages your offhand automatically.", ModuleCategory.COMBAT, new String[]{"autototem"});
      this.itemSetting = this.add(new EnumSetting<>("Item", OffhandItemChoice.Totem));
      this.customSetting = this.add(new RegistrySetSetting<>("Custom", BuiltInRegistries.ITEM, new Item[0]));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 25, 0, 500).withUnit("ms"));
      this.healthSetting = this.add(new NumberSetting<>("Health", 16.0F, 0.0F, 36.0F).withUnit("hp"));
      this.lethalSetting = this.add(new BooleanSetting("Lethal", true));
      this.elytraSetting = this.add(new BooleanSetting("Elytra", true));
      this.directSwapSetting = this.add(new BooleanSetting("1.17+", true));
      this.containersSetting = this.add(new BooleanSetting("Containers", true));
      this.gappleBindSetting = this.add(new BooleanSetting("GappleBind", true).markVisibilityDependency());
      Predicate<Object> safeVisibility = unused -> {
         Setting<Boolean> gappleBindOption = this.gappleBindSetting;
         return gappleBindOption.isExpanded();
      };
      this.safeSetting = this.add(new NumberSetting<>("Safe", 8.0F, 0.0F, 36.0F, safeVisibility).withUnit("hp"));
      this.safeInBlocksSetting = this.add(new NumberSetting<>("SafeInBlocks", 0.0F, 0.0F, 36.0F).withUnit("hp").dependsOn(this.gappleBindSetting));
      this.axeSetting = this.add(new BooleanSetting("Axe", true).dependsOn(this.gappleBindSetting));
      Predicate<Object> pickaxeVisibility = unused -> {
         Setting<Boolean> gappleBindOption = this.gappleBindSetting;
         return gappleBindOption.isExpanded();
      };
      this.pickaxeSetting = this.add(new BooleanSetting("Pickaxe", true, pickaxeVisibility));
      Predicate<Object> crystalsVisibility = unused -> {
         Setting<Boolean> gappleBindOption = this.gappleBindSetting;
         return gappleBindOption.isExpanded();
      };
      this.crystalsSetting = this.add(new BooleanSetting("Crystals", true, crystalsVisibility));
      this.totemsSetting = this.add(new BooleanSetting("Totems", false).dependsOn(this.gappleBindSetting));
      Predicate<Object> crappleVisibility = unused -> {
         Setting<OffhandItemChoice> itemOption = this.itemSetting;
         return itemOption.getValue() == OffhandItemChoice.Gapple || this.gappleBindSetting.isExpanded();
      };
      this.crappleSetting = this.add(new BooleanSetting("Crapple", false, crappleVisibility));
      this.swapDelayTimer = new ElapsedTimer();
      this.totemPopTimer = new ElapsedTimer();
      this.previousSwapSlot = -1;
      this.lethalDamageTracker = new LethalDamageTracker(this);
      Setting<Boolean> gappleBindOption = this.gappleBindSetting;
      gappleBindOption.setConfigName("GappleBind");
      this.safeSetting.setConfigName("SafeHealth");
      Setting<Set<Item>> customOption = this.customSetting;
      Runnable customItemCallback = () -> {
         RegistrySetSetting<Item> registryOption = (RegistrySetSetting<Item>)customOption;
         if (registryOption.getLastChangedEntry() == null) return;

         (customOption.getValue()).clear();

         (customOption.getValue()).add(((Item)registryOption.getLastChangedEntry()));
      };
      customOption.setChangeCallback(customItemCallback);
      this.safeInBlocksSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.customSetting.addVisibilityPredicate( unused -> {
         Setting<OffhandItemChoice> itemOption = this.itemSetting;
         return itemOption.getValue() == OffhandItemChoice.Custom;
      });
   }
}
