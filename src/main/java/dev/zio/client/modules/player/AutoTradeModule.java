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
import dev.zio.client.events.ClientLevelAddEntityEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.utils.inventory.TradeOfferSnapshot;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.utils.network.InteractPacketUtils;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.managers.TradeRegistry;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import dev.zio.mixin.MerchantScreenAccessor;
import dev.zio.mixin.ServerboundInteractPacketAccessor;
import java.util.function.Predicate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class AutoTradeModule extends Module {
   private final Setting<Integer> delaySetting;
   private final Setting<Integer> frequencySetting;
   private final Setting<Boolean> closeSetting;
   private final Setting<Boolean> dropSetting;
   private final Setting<Boolean> autoOpenSetting;
   final Setting<Boolean> onSightSetting;
   private final Setting<Float> rangeSetting;
   private final Setting<Boolean> rotateSetting;
   private final VillagerOfferCache offerCache;
   private final ElapsedTimer tradeDelayTimer;
   private final ElapsedTimer closeQuietTimer;
   private final ElapsedTimer openTimer;
   private final ElapsedTimer cacheRefreshTimer;
   private boolean tradedInMenu;
   private boolean resetOpenTimer;
   private int villagerId;

   private Entity findVillagerToOpen() {
      Camera camera = ClientAccess.zioMinecraft.gameRenderer.getMainCamera();
      Entity selectedVillager = null;

      float smallestYawDifference = 3.6e+02F;
      ClientLevel level = ClientAccess.zioMinecraft.level;
      for (Entity entity : level.entitiesForRendering()) {
         if (!(entity instanceof Villager)) {
            continue;
         }
         Vec3 villagerPosition = entity.position();
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         Vec3 eyePosition = player.getEyePosition();
         double openRange = (this.rangeSetting.getValue()).floatValue();
         if (!villagerPosition.closerThan(eyePosition, openRange)) {
            continue;
         }
         int candidateId = entity.getId();
         if (!this.shouldOpenVillager(candidateId)) {
            continue;
         }
         if (this.onSightSetting.getValue()) {
            if (ClientAccess.zioMinecraft.hitResult instanceof EntityHitResult hitResult
               && hitResult.getEntity() == entity) {
               return entity;
            }
            continue;
         }
         float villagerYaw = RotationManager.getRotationsToEntity(entity)[0];
         float cameraYaw = camera.yRot();
         float yawDifference = Mth.degreesDifferenceAbs(villagerYaw, cameraYaw);
         if (yawDifference < smallestYawDifference) {
            smallestYawDifference = yawDifference;
            selectedVillager = entity;
         }
      }

      return selectedVillager;
   }

   private AbstractVillager getCurrentVillager() {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      int currentVillagerId = this.villagerId;
      return level.getEntity(currentVillagerId) instanceof AbstractVillager villager ? villager : null;
   }

   private boolean isTradeExperienceOrb(Entity entity) {
      if (entity.getType() == EntityType.EXPERIENCE_ORB
         && this.villagerId != -1) {
         AABB orbBounds = entity.getBoundingBox();

         Vec3 position = entity.position();

         return orbBounds.contains(position);
      } else {
         return false;
      }
   }

   @EventHandler
   private void onSendPacket(
      PacketEvent.Send event
   ) {
      if (event.getPacket() instanceof ServerboundInteractPacket interactPacket) {
         if (InteractPacketUtils.getEntity(interactPacket) instanceof Villager) {
            if (InteractPacketUtils.getAction(interactPacket)
               == InteractPacketUtils.Action.INTERACT) {
               this.villagerId = interactPacket.entityId();
            }
         }
      }
   }

   private void tradeOnce(MerchantMenu menu) {
      AbstractVillager villager = this.getCurrentVillager();
      if (villager == null) {
         return;
      }
      VillagerOfferCache cacheReceiver = this.offerCache;

      int queriedVillagerId = villager.getId();

      List<TradeOfferSnapshot> cachedOffers = cacheReceiver.getOffers(queriedVillagerId);
      if (cachedOffers == null || cachedOffers.isEmpty()) {
         return;
      }
      HashMap<TradeItemKey, Integer> offerIndices = new HashMap<>();
      int offerIndex = 0;

      MerchantOffers menuOffers = menu.getOffers();

      for (MerchantOffer menuOffer : menuOffers) {
         TradeItemKey offerKey = TradeItemKey.fromOffer(menuOffer);
         offerIndices.put(offerKey, offerIndex);
         offerIndex++;
      }

      for (TradeOfferSnapshot cachedOffer : cachedOffers) {
         if (cachedOffer == TradeOfferSnapshot.OUT_OF_STOCK) {
            continue;
         }
         TradeRegistry trades = ClientContext.tradeRegistry;
         if (!trades.matches(cachedOffer)) {
            continue;
         }
         if (!this.canAffordOffer(cachedOffer)) {
            continue;
         }
         int selectedIndex = offerIndices.get(
            TradeItemKey.fromCachedOffer(cachedOffer)
         );

         this.selectTrade(selectedIndex);

         ItemStack outputStack = menu.getSlot(2).getItem();

         ItemStack expectedResult = cachedOffer.result();

         if (ItemStack.isSameItem(outputStack, expectedResult)) {
            int containerId = menu.containerId;
            this.collectTradeResult(containerId);
         }

         this.tradedInMenu = true;

         this.tradeDelayTimer.reset();
         break;
      }
      offerIndices.clear();
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerPreTickEvent event) {
      if (!(
         ClientAccess.zioMinecraft.player.containerMenu instanceof MerchantMenu menu
      )) {
         this.tradedInMenu = false;
         return;
      }
      ElapsedTimer timer = this.cacheRefreshTimer;

      if (timer.hasElapsedAndReset(500L)
         && ClientAccess.zioMinecraft.player.containerMenu instanceof MerchantMenu refreshMenu
         && this.villagerId != -1) {
         ClientLevel level = ClientAccess.zioMinecraft.level;
         int currentVillagerId = this.villagerId;
         if (level.getEntity(currentVillagerId) instanceof AbstractVillager villager) {
            VillagerOfferCache cache = this.offerCache;
            MerchantOffers refreshedOffers = refreshMenu.getOffers();
            cache.updateOffers(villager, refreshedOffers);
         }
      }

      timer = this.tradeDelayTimer;
      long requiredDelayMillis = (this.delaySetting.getValue()).intValue();
      if (!timer.hasElapsed(requiredDelayMillis)) {
         return;
      }
      this.resetOpenTimer = true;
      for (int tradeAttempt = 0; ; tradeAttempt++) {
         Integer frequency = this.frequencySetting.getValue();
         if (tradeAttempt >= frequency) {
            break;
         }

         this.tradeOnce(menu);
      }

      int closeDelay;
      if (this.tradedInMenu) {
         closeDelay = 150;
      } else {
         closeDelay = 500;
      }

      long closeDelayMillis = closeDelay;
      if (this.closeQuietTimer.hasElapsed(closeDelayMillis)) {
         if (this.closeSetting.getValue()) {
            Screen screen = ClientAccess.zioMinecraft.screen;
            screen.onClose();
         }
      }
   }

   @EventHandler
   private void onEntityAdded(ClientLevelAddEntityEvent event) {
      Entity addedEntity = event.getEntity();

      if (this.isTradeExperienceOrb(addedEntity)) {
         this.closeQuietTimer.reset();
      }
   }

   private boolean shouldOpenVillager(int entityId) {
      long cacheAgeMillis = System.currentTimeMillis();
      VillagerOfferCache villagerCache = this.offerCache;

      cacheAgeMillis -= villagerCache.getLastUpdatedMillis(entityId);

      if ((cacheAgeMillis > 30000L)) {
         return true;
      }

      List<TradeOfferSnapshot> offers = this.offerCache.getOffers(entityId);
      if (offers == null || offers.isEmpty()) {
         return true;
      }
      for (TradeOfferSnapshot offer : offers) {
         if (offer != TradeOfferSnapshot.OUT_OF_STOCK) {
            if (ClientContext.tradeRegistry.matches(offer) && this.canAffordOffer(offer)) {
               return true;
            }
         }
      }

      return false;
   }

   private void collectTradeResult(int containerId) {
      if (this.dropSetting.getValue()) {
         MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;

         LocalPlayer dropPlayer = ClientAccess.zioMinecraft.player;
         ContainerInput dropClick = ContainerInput.THROW;
         byte dropButton = 0;

         gameMode.handleContainerInput(containerId, 2, dropButton, dropClick, dropPlayer);
      } else {
         MultiPlayerGameMode quickMoveGameMode = ClientAccess.zioMinecraft.gameMode;

         LocalPlayer movePlayer = ClientAccess.zioMinecraft.player;
         ContainerInput moveClick = ContainerInput.QUICK_MOVE;
         byte moveButton = 0;

         quickMoveGameMode.handleContainerInput(containerId, 2, moveButton, moveClick, movePlayer);
      }
   }

   private void selectTrade(int offerIndex) {
      if (ClientAccess.zioMinecraft.screen instanceof MerchantScreen screen) {
         ((MerchantScreenAccessor)screen).zio$setSelected(offerIndex);
         ((MerchantScreenAccessor)screen).zio$syncRecipe();
      } else {
         ServerboundSelectTradePacket selectedTrade = new ServerboundSelectTradePacket(offerIndex);
         PacketSender.send(selectedTrade);
      }
   }

   @EventHandler
   private void onRotationRequest(RotationRequestEvent event) {
      Boolean autoOpen = this.autoOpenSetting.getValue();
      if (!autoOpen) {
         return;
      }
      if (ClientAccess.zioMinecraft.screen != null) {
         return;
      }
      if (this.resetOpenTimer) {
         ElapsedTimer timer = this.openTimer;
         timer.reset();
         this.resetOpenTimer = false;
         return;
      }
      Entity villager = this.findVillagerToOpen();
      if (villager == null) {
         return;
      }
      ElapsedTimer openDelayTimer = this.openTimer;
      if (!openDelayTimer.hasElapsedAndReset(500L)) {
         return;
      }
      InteractionHand hand = InteractionHand.MAIN_HAND;
      BlockInteractionUtils.interactWithEntity(villager, hand);
      Boolean rotate = this.rotateSetting.getValue();
      if (!rotate) {
         return;
      }
      if (this.onSightSetting.getValue()) {
         return;
      }
      float[] rotations = RotationManager.getRotationsToEntity(villager);
      ClientContext.rotationManager.requestRotation(rotations, 100);
   }

   private boolean canAffordOffer(TradeOfferSnapshot offer) {
      int firstCostCount = 0;
      int secondCostCount = 0;
      NonNullList<Slot> slots = ClientAccess.zioMinecraft.player.containerMenu.slots;
      for (Slot slot : slots) {
         ItemStack stack = slot.getItem();

         ItemStack firstCostStack = offer.firstCost();

         Item inventoryItem = stack.getItem();

         if (firstCostStack.is(inventoryItem)) {
            firstCostCount += stack.getCount();
         }

         Optional optionalSecondCost = offer.secondCost();
         if (!optionalSecondCost.isEmpty()) {
            ItemCost secondCost = (ItemCost)offer.secondCost().get();

            if (secondCost.test(stack)) {
               secondCostCount += secondCost.count();
            }
         }
      }

      ItemStack requiredFirstCost = offer.firstCost();
      if (firstCostCount < requiredFirstCost.getCount()) {
         return false;
      }

      Optional requiredSecondCost = offer.secondCost();
      if (!requiredSecondCost.isEmpty()) {
         ItemStack requiredCostStack = ((ItemCost)offer.secondCost().get()).itemStack();
         if (secondCostCount < requiredCostStack.getCount()) {
            return false;
         }
      }

      return true;
   }

   @EventHandler
   private void onReceivePacket(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundMerchantOffersPacket offersPacket) {
         VillagerOfferCache cache = this.offerCache;

         AbstractVillager villager = this.getCurrentVillager();
         MerchantOffers packetOffers = offersPacket.getOffers();
         cache.updateOffers(villager, packetOffers);

         this.closeQuietTimer.reset();
      }
   }

   public AutoTradeModule() {
      super("AutoTrade", "Trades with villagers for you.\n"
         + "%sUse ;trade command to access the trade gui".formatted(ChatFormatting.YELLOW), ModuleCategory.PLAYER, new String[0]);
      this.delaySetting = this.add(new NumberSetting<>("Delay", 150, 25, 1500).withUnit("ms"));
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 6));
      Boolean closeDefault = false;
      this.closeSetting = this.add(new BooleanSetting("Close", closeDefault));
      Boolean dropDefault = false;
      this.dropSetting = this.add(new BooleanSetting("Drop", dropDefault));
      Boolean autoOpenDefault = false;
      BooleanSetting autoOpenOption = new BooleanSetting("AutoOpen", autoOpenDefault);
      autoOpenOption.markVisibilityDependency();
      this.autoOpenSetting = this.add(autoOpenOption);
      Boolean sightDefault = false;
      BooleanSetting sightOption = new BooleanSetting("OnSight", sightDefault);
      this.onSightSetting = this.add(sightOption.dependsOn(this.autoOpenSetting));
      Predicate rangeVisibility = ignoredValue -> {
         Setting<Boolean> onSight = this.onSightSetting;
         return !onSight.getValue();
      };
      NumberSetting<Float> rangeOption = new NumberSetting<>("Range", 3.0F, 0.1F, 6.0F, rangeVisibility);
      rangeOption.withUnit("m");
      this.rangeSetting = this.add(rangeOption.dependsOn(this.autoOpenSetting));
      Predicate rotateVisibility = ignoredValue -> {
         Setting<Boolean> onSight = this.onSightSetting;
         return !onSight.getValue();
      };
      Boolean rotateDefault = true;
      BooleanSetting rotateOption = new BooleanSetting("Rotate", rotateDefault, rotateVisibility);
      this.rotateSetting = this.add(rotateOption.dependsOn(this.autoOpenSetting));
      this.offerCache = new VillagerOfferCache();
      this.tradeDelayTimer = new ElapsedTimer();
      this.closeQuietTimer = new ElapsedTimer();
      this.openTimer = new ElapsedTimer();
      this.cacheRefreshTimer = new ElapsedTimer();
      this.villagerId = -1;
   }

}
