/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.client;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.ConnectScreenConnectEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.integrations.ProtocolCompatibility;
import dev.zio.client.utils.entity.PlayerStateTracker;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.PermanentModule;
import dev.zio.client.modules.movement.VelocityModule;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.AntiCheatMiningChoice;
import dev.zio.client.settings.options.AntiCheatPlacementsChoice;
import dev.zio.client.settings.options.AntiCheatRotationsChoice;
import dev.zio.client.settings.options.AntiCheatStrictDirectionChoice;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

public class AntiCheatModule extends PermanentModule {
   public static SpeedMineModule speedMineModule;
   public static VelocityModule velocityModule;
   public static final float ROTATION_JITTER_STEP;
   public final Setting<Boolean> value2b2tSetting;
   private final Setting<Boolean> limiterSetting;
   public final Setting<Boolean> noServerSlotSetting;
   public final Setting<AntiCheatStrictDirectionChoice> strictDirectionSetting;
   public final Setting<AntiCheatMiningChoice> miningModeSetting;
   public final Setting<AntiCheatPlacementsChoice> placementsSetting;
   public final Setting<Boolean> movementSyncSetting;
   public final Setting<AntiCheatRotationsChoice> rotationsSetting;
   public final Setting<Boolean> swingSetting;
   public final Setting<Boolean> placeSetting;
   public final Setting<Boolean> hitSetting;
   public final Setting<Boolean> miningSwingSetting;
   public final Setting<Boolean> assumeBlockBreakingSetting;
   private final AntiCheatMovementHandler movementHandler;
   private final PacketRateLimiter packetRateLimiter;
   private boolean miningStartedWithoutGrimStrict;
   private boolean silentRotationWarningShown;
   private boolean wasFallFlying;
   private int sneakResyncTicks;
   protected boolean movementYawOffsetEnabled;

   public boolean is2b2tEnabled() {
      return this.value2b2tSetting.getValue();
   }

   public boolean isMovementSyncEnabled() {
      return this.movementSyncSetting.getValue();
   }

   public boolean isPacketLimiterActive() {
      return this.is2b2tEnabled()
         && this.isMovementSyncEnabled()
         && this.limiterSetting.getValue();
   }

   public int getRecentPacketCount() {
      PacketRateLimiter limiter = this.packetRateLimiter;
      PacketRateLimiter globalLimiter = this.packetRateLimiter;
      return limiter.countRecentPackets(globalLimiter.getGlobalWindow());
   }

   public int getPacketLimit() {
      return this.packetRateLimiter.getGlobalWindow().limit();
   }

   public AntiCheatMovementHandler getMovementHandler() {
      return this.movementHandler;
   }

   public boolean isPaperPlacement() {
      return this.placementsSetting.getValue() == AntiCheatPlacementsChoice.PAPER;
   }

   public boolean isPacketLimitReached() {
      if (this.isPacketLimiterActive()) {
         PacketRateLimiter limiter = this.packetRateLimiter;
         PacketRateLimiter globalLimiter = this.packetRateLimiter;
         return limiter.isLimitReached(globalLimiter.getGlobalWindow());
      }
      return false;
   }

   private boolean shouldResyncSneakingAfterFlight() {
      if (!this.movementSyncSetting.getValue() || this.is2b2tEnabled()) {
         return false;
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      if (player.isShiftKeyDown()) {
         return false;
      }
      return this.wasFallFlying && !PlayerStateUtils.isFallFlying();
   }

   private void warnGrimStrictRequiresReconnect() {
      if (!this.isWorldUnavailable()) {
         if (this.is2b2tEnabled()) {
            Setting<AntiCheatMiningChoice> miningMode = this.miningModeSetting;
            if (miningMode.getValue() == AntiCheatMiningChoice.GRIM_STRICT) {
               if (!this.miningStartedWithoutGrimStrict || ClientAccess.zioMinecraft.isLocalServer()) {
                  return;
               }

               Component message = Component.literal(("To use GrimStrict mining you must reconnect to the server.\n"));
               MessageSignature signature = ChatUtils.DEFAULT_SIGNATURE;
               ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.HIGH;
               ChatUtils.printChat(message, signature, priority);
               return;
            }
         }
      }
   }

   private void warnSilentRotationsCompatibility() {
      if (this.rotationsSetting.getValue() != AntiCheatRotationsChoice.SILENT
         || !this.movementSyncSetting.getValue()
         || ClientAccess.zioMinecraft.isLocalServer()) {
         return;
      }
      if (ProtocolCompatibility.getTargetVersion().olderEq_1_20_6() || this.silentRotationWarningShown) {
         return;
      }
      String message = ClientContext.languageTable.getTranslationOrDefault(
         "anticheat.SilentRotationsWarning",
         "Rotations mode \"Silent\" works only when you have ViaFabric(Plus) set to version 1.17 - 1.20.6."
      );
      ChatUtils.printChat(Component.literal(message), ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.HIGH);
      this.silentRotationWarningShown = true;
   }

   static {

      speedMineModule = ClientContext.getModule(SpeedMineModule.class);
      velocityModule = ClientContext.getModule(VelocityModule.class);

      double exponent = 3.0D;
      double base = 0.20000000298023224D;
      double basePower = Math.pow(base, exponent);

      double scaledPower = basePower * 8.0D;

      double rotationStep = scaledPower * 0.15D;

      ROTATION_JITTER_STEP = (float)(rotationStep - 0.001D);
   }

   public boolean shouldCancelPacket(Packet packet) {
      return this.packetRateLimiter.shouldCancelPacket(packet);
   }

   private void reverseBundleContents(ItemStack stack) {
      if (stack != null) {
         if (!stack.isEmpty()) {
            DataComponentType<BundleContents> bundleType = DataComponents.BUNDLE_CONTENTS;
            BundleContents bundleContents = stack.get(bundleType);
            DataComponentType<ItemContainerContents> containerType = DataComponents.CONTAINER;
            ItemContainerContents containerContents = stack.get(containerType);
            if (bundleContents != null) {
               // 26.1.2: BundleContents stores ItemStackTemplates; reverse via live copies
               // (same visible behavior: bundles inside are flipped, then order reversed).
               List<ItemStack> copiedItems = bundleContents.itemCopyStream().toList();
               Consumer<ItemStack> action = this::reverseBundleContents;
               copiedItems.forEach(action);
               List<ItemStack> reversedItems = copiedItems.reversed();
               DataComponentType<BundleContents> replacementType = DataComponents.BUNDLE_CONTENTS;
               BundleContents replacement = new BundleContents(
                  reversedItems.stream().map(ItemStackTemplate::fromNonEmptyStack).toList()
               );
               stack.set(replacementType, replacement);
            } else if (containerContents != null) {
               List<ItemStack> copiedItems = containerContents.allItemsCopyStream().toList();
               Consumer<ItemStack> action = this::reverseBundleContents;
               copiedItems.forEach(action);
               stack.set(containerType, ItemContainerContents.fromItems(copiedItems));
            }

            return;
         }
      }
   }

   @EventHandler
   private void onLocalPlayerTick(LocalPlayerPreTickEvent event) {

      if (this.shouldResyncSneakingAfterFlight()) {

         this.sneakResyncTicks = 3;
      }

      if (this.sneakResyncTicks > 0) {
         this.sneakResyncTicks--;
         if (this.sneakResyncTicks == 0) {
            PlayerStateTracker playerStateSnapshot = ClientContext.playerStateTracker;

            playerStateSnapshot.setSneaking(true);
            playerStateSnapshot = ClientContext.playerStateTracker;

            playerStateSnapshot.setSneaking(false);
         }
      }

      this.wasFallFlying = PlayerStateUtils.isFallFlying();
   }

   private void handleMiningStartPacket(ServerboundPlayerActionPacket startPacket) {
      Setting<AntiCheatMiningChoice> miningMode = this.miningModeSetting;
      if (miningMode.getValue() == AntiCheatMiningChoice.GRIM_STRICT) {

         int sequence = PacketSender.nextPredictionSequence();
         ServerboundPlayerActionPacket stopPacket = new ServerboundPlayerActionPacket(
            Action.STOP_DESTROY_BLOCK, sourcePosition(startPacket), sourceDirection(startPacket), sequence
         );
         PacketSender.markImportant(startPacket);
         PacketSender.markImportant(stopPacket);
         PacketSender.send(stopPacket);
      } else {
         this.miningStartedWithoutGrimStrict = true;
      }
   }

   private static BlockPos sourcePosition(ServerboundPlayerActionPacket packet) {
      return packet.getPos();
   }

   private static Direction sourceDirection(ServerboundPlayerActionPacket packet) {
      return packet.getDirection();
   }

   @EventHandler(priority = -100)
   private void onMiningPacketSend(
      PacketEvent.Send event
   ) {
      if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket) {
         if (!event.isCancelled()) {
            if (actionPacket.getAction() == Action.START_DESTROY_BLOCK) {
               this.handleMiningStartPacket(actionPacket);
            }

            if (actionPacket.getAction() == Action.STOP_DESTROY_BLOCK) {
               Setting<AntiCheatMiningChoice> miningMode = this.miningModeSetting;
               if (miningMode.getValue() == AntiCheatMiningChoice.GRIM) {
                  ThreadLocalRandom randomSnapshot = ThreadLocalRandom.current();

                  int randomOffset = randomSnapshot.nextInt((50));

                  int offset = randomOffset + 500;
                  Action action = Action.ABORT_DESTROY_BLOCK;
                  BlockPos startPosition = actionPacket.getPos();
                  BlockPos offsetPosition = startPosition.offset(0, offset, 0);
                  Direction abortDirection = actionPacket.getDirection();
                  PacketSender.sendPlayerAction(action, offsetPosition, abortDirection);
               }
            }
         }
      }
   }

   @EventHandler
   private void onConnect(ConnectScreenConnectEvent event) {
      this.miningStartedWithoutGrimStrict = false;
      this.silentRotationWarningShown = false;
      ClientContext.taskScheduler.scheduleRotationTask(this::warnSilentRotationsCompatibility, 5);
   }

   @EventHandler(priority = 100)
   private void onPacketSend(
      PacketEvent.Send event
   ) {
      if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket) {
         if (actionPacket.getAction() == Action.START_DESTROY_BLOCK) {
            Setting<AntiCheatMiningChoice> miningMode = this.miningModeSetting;
            if (miningMode.getValue() == AntiCheatMiningChoice.GRIM_STRICT) {
               Packet importantPacket = event.getPacket();
               PacketSender.markImportant(importantPacket);
            }
         }
      }
      if (this.is2b2tEnabled()) {
         Setting<Boolean> packetLimiterSetting = this.limiterSetting;
         Boolean limiterEnabled = packetLimiterSetting.getValue();
         if (limiterEnabled) {
            PacketRateLimiter limiterSnapshot = this.packetRateLimiter;
            Packet packet = event.getPacket();
            if (limiterSnapshot.shouldCancelPacket(packet)) {
               event.cancel();
            }
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (!this.is2b2tEnabled()) {
         return;
      }
      if (event.getPacket() instanceof ClientboundContainerClosePacket closePacket) {
         if (closePacket.getContainerId() == 0) {
            event.cancel();
         }
      }

      if (event.getPacket() instanceof ClientboundContainerSetContentPacket contentsPacket) {
         List<ItemStack> inventoryItems = contentsPacket.items();
         Consumer<ItemStack> action = this::reverseBundleContents;
         inventoryItems.forEach(action);
         ItemStack carriedItem = contentsPacket.carriedItem();
         this.reverseBundleContents(carriedItem);
      }

      if (event.getPacket() instanceof ClientboundContainerSetSlotPacket slotPacket) {
         ItemStack slotItem = slotPacket.getItem();
         this.reverseBundleContents(slotItem);
      }

      if (event.getPacket() instanceof ClientboundSetPlayerInventoryPacket inventoryPacket) {
         try {
            inventoryPacket.slot();
         } catch (Throwable slotMatchFailure) {
            throw new MatchException(slotMatchFailure.toString(), slotMatchFailure);
         }

         ItemStack inventoryContents;
         try {
            inventoryContents = inventoryPacket.contents();
         } catch (Throwable inventoryContentsMatchFailure) {
            throw new MatchException(inventoryContentsMatchFailure.toString(), inventoryContentsMatchFailure);
         }
         this.reverseBundleContents(inventoryContents);
      }

      if (event.getPacket() instanceof ClientboundSetCursorItemPacket cursorPacket) {
         ItemStack cursorContents;
         try {
            cursorContents = cursorPacket.contents();
         } catch (Throwable cursorMatchFailure) {
            throw new MatchException(cursorMatchFailure.toString(), cursorMatchFailure);
         }
         this.reverseBundleContents(cursorContents);
      }
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      speedMineModule = (SpeedMineModule)modules.getModule(
         SpeedMineModule.class
      );
      velocityModule = (VelocityModule)modules.getModule(
         VelocityModule.class
      );
   }

   public AntiCheatModule() {
      super("AntiCheat", "Manages the client's behavior on different anti-cheats.", ModuleCategory.CLIENT, new String[0]);
      this.value2b2tSetting = this.add(new BooleanSetting("2b2t", false));
      Predicate limiterVisibility = ignored -> this.value2b2tSetting.getValue();
      this.limiterSetting = this.add(new BooleanSetting("Limiter", false, limiterVisibility));
      this.noServerSlotSetting = this.add(new BooleanSetting("NoServerSlot", true));
      this.strictDirectionSetting = this.add(new EnumSetting<>("StrictDirection", AntiCheatStrictDirectionChoice.NCP));
      this.miningModeSetting = this.add(new EnumSetting<>("Mining", AntiCheatMiningChoice.VANILLA));
      this.placementsSetting = this.add(new EnumSetting<>("Placements", AntiCheatPlacementsChoice.VANILLA));
      this.movementSyncSetting = this.add(new BooleanSetting("MovementSync", false).markVisibilityDependency());
      AntiCheatRotationsChoice rotationsDefault = AntiCheatRotationsChoice.SILENT;
      EnumSetting<AntiCheatRotationsChoice> rotationsOption = new EnumSetting<>(("Rotations"), rotationsDefault);
      this.rotationsSetting = this.add(rotationsOption.dependsOn(this.movementSyncSetting));
      this.swingSetting = this.add(new BooleanSetting("Swing", true).markVisibilityDependency());
      this.placeSetting = this.add(new BooleanSetting("Place", true).dependsOn(this.swingSetting));
      this.hitSetting = this.add(new BooleanSetting("Hit", true).dependsOn(this.swingSetting));
      this.miningSwingSetting = this.add(new BooleanSetting("Mining", false).dependsOn(this.swingSetting));
      Predicate assumeBreakingVisibility = ignored -> false;
      this.assumeBlockBreakingSetting = this.add(new BooleanSetting("AssumeBlockBreaking", true, assumeBreakingVisibility));
      this.movementHandler = new AntiCheatMovementHandler(this);
      this.packetRateLimiter = new PacketRateLimiter();
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
      this.rotationsSetting.setChangeCallback(this::warnSilentRotationsCompatibility);
      this.movementSyncSetting.setChangeCallback(this::warnSilentRotationsCompatibility);
      Setting<AntiCheatMiningChoice> callbackSetting = this.miningModeSetting;
      Runnable callback = this::warnGrimStrictRequiresReconnect;
      callbackSetting.setChangeCallback(callback);
      this.miningSwingSetting.setConfigName(("MiningSwing"));
   }

}
