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
import dev.zio.client.commands.FakeplayerCommand;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.utils.world.Waypoint;
import dev.zio.client.utils.combat.DamageUtils;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.exploit.IllegalDisconnectModule;
import dev.zio.client.modules.misc.AutoReconnectModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AutoLogModule extends Module {
   private static IllegalDisconnectModule illegalDisconnect;
   private static AutoReconnectModule autoReconnect;
   private final Setting<Integer> healthSetting;
   private final Setting<Float> invincTimeoutSetting;
   private final Setting<Boolean> reconnectSetting;
   private final Setting<Boolean> autoDisableSetting;
   private final Setting<Boolean> gearedOnlySetting;
   private final Setting<Boolean> unfocusedOnlySetting;
   private final Setting<Boolean> onRenderSetting;
   private final Setting<Boolean> crystalSetting;
   private final Setting<Boolean> tNTMinecartSetting;
   final Setting<Boolean> totemsSetting;
   private final Setting<Integer> totemCountSetting;
   private final Setting<Boolean> elytraSetting;
   private final Setting<Integer> durabilitySetting;
   private final Setting<Boolean> assumeInventorySetting;
   public final Setting<Boolean> yLevelSetting;
   private final Setting<Float> ySetting;
   private final Setting<Boolean> waypointsSetting;
   private final Setting<Float> distanceSetting;
   private Runnable pendingDisconnect;
   private final ElapsedTimer disconnectTimer;
   private long disconnectTimeMillis;

   private void requestDisconnect(String reason) {
      if (this.autoDisableSetting.getValue()) {
         this.disable();
      }

      ClientPacketListener packetListener = ClientAccess.zioMinecraft.player.connection;

      Connection connection = packetListener.getConnection();
      if (connection != null) {
         this.disconnectTimeMillis = System.currentTimeMillis();

         if (!this.reconnectSetting.getValue()) {
            ClientContext.serverStateManager.autoLogged = true;

            autoReconnect.disable();
         }

         Setting<Boolean> ignoreProxySettingSource = illegalDisconnect.ignoreProxySetting;

         boolean allowIllegalDisconnect = !ignoreProxySettingSource.getValue() || !IllegalDisconnectModule.isIgnoredProxy();

         if (illegalDisconnect.isToggled() && allowIllegalDisconnect) {
            IllegalDisconnectModule.sendInvalidChatPacket();
            if (this.pendingDisconnect == null) {
               this.disconnectTimer.reset();
            }

            this.pendingDisconnect = () -> {
               connection.disconnect(this.buildDisconnectReason(reason));
            };
            return;
         }

         connection.disconnect(this.buildDisconnectReason(reason));
      }
   }

   private Component buildDisconnectReason(String reason) {
      SimpleDateFormat formatterSnapshot = new SimpleDateFormat("HH:mm");
      Date disconnectDate = new Date(this.disconnectTimeMillis);
      String formattedTime = formatterSnapshot.format(disconnectDate);
      MutableComponent message = Component.empty();
      MutableComponent componentSnapshot = Component.literal("[AutoLog] ");
      ChatFormatting prefixColor = ChatFormatting.GRAY;
      message.append(componentSnapshot.withStyle(prefixColor));
      componentSnapshot = Component.literal(reason);
      ChatFormatting reasonColor = ChatFormatting.RED;
      message.append(componentSnapshot.withStyle(reasonColor));
      message.append("\n\n");
      String timeText = "Logged out at " + formattedTime;
      componentSnapshot = Component.literal(timeText);
      ChatFormatting timeColor = ChatFormatting.GRAY;
      message.append(componentSnapshot.withStyle(timeColor));
      return message;
   }

   static {
      illegalDisconnect = (IllegalDisconnectModule)ClientContext.getModule(IllegalDisconnectModule.class);
      autoReconnect = (AutoReconnectModule)ClientContext.getModule(AutoReconnectModule.class);
   }

   @Override
   public String getInfo() {
      return formatHealthInfo(this);
   }

   private static String formatHealthInfo(AutoLogModule owner) {
      Object[] values = new Object[1];
      Setting<Integer> healthSettingSource = owner.healthSetting;
      values[0] = healthSettingSource.getValue();
      return "%d.0".formatted(values);
   }

   @Override
   public void onEnable() {
      this.pendingDisconnect = null;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (this.isWorldUnavailable()) {
         return;
      }

      if (ClientAccess.zioMinecraft.player.isSpectator()) {
         return;
      }

      if (ClientAccess.zioMinecraft.player.hasInfiniteMaterials()) {
         return;
      }

      if (this.gearedOnlySetting.getValue()) {
         if (!PlayerStateUtils.hasArmor(ClientAccess.zioMinecraft.player)) {
            return;
         }
      }

      if (this.elytraSetting.getValue()) {
         if (this.isElytraDurabilityUnsafe()) {
            this.requestDisconnect("Your elytra durability is too low!");
            return;
         }
      }

      if (this.yLevelSetting.getValue()) {
         double currentY = ClientAccess.zioMinecraft.player.getY();
         Float heightLimit = this.ySetting.getValue();
         if (currentY < heightLimit.floatValue()) {
            Object heightMessageValue = this.ySetting.getValue();
            this.requestDisconnect("You are below Y level " + String.valueOf(heightMessageValue));
            return;
         }
      }

      List visibleWaypoints = ClientContext.waypointRegistry.getVisible();

      if (this.waypointsSetting.getValue() && !visibleWaypoints.isEmpty()) {
         double waypointDistance = ((Waypoint)visibleWaypoints.getFirst()).distanceTo(ClientAccess.zioMinecraft.player.getEyePosition());
         Float distanceLimit = this.distanceSetting.getValue();
         if (waypointDistance <= distanceLimit.floatValue()) {
            Object[] waypointNameArguments = new Object[1];
            Waypoint nameWaypoint = (Waypoint)visibleWaypoints.getFirst();
            waypointNameArguments[0] = nameWaypoint.getName();
            String waypointMessage = "You are close to %s waypoint!".formatted(waypointNameArguments);
            this.requestDisconnect(waypointMessage);
            return;
         }
      }

      float ticksSinceSpawn = ClientAccess.zioMinecraft.player.tickCount;
      float secondsSinceSpawn = ticksSinceSpawn / 20.0F;
      Float invincibilityTimeout = this.invincTimeoutSetting.getValue();
      if (secondsSinceSpawn < invincibilityTimeout) {
         return;
      }

      if (this.onRenderSetting.getValue()) {
         Iterator players = ClientAccess.zioMinecraft.level.players().iterator();

         while (players.hasNext()) {
            Player renderedPlayer = (Player)players.next();
            if (!(renderedPlayer instanceof FakeplayerCommand.SimulatedPlayer)) {
               SocialRegistry friendManagerSnapshot = ClientContext.socialRegistry;

               String renderedPlayerName = renderedPlayer.getName().getString();

               if (!friendManagerSnapshot.isFriendName(renderedPlayerName)
                  && renderedPlayer != ClientAccess.zioMinecraft.player) {
                  String renderMessage = renderedPlayer.getName().getString() + " has entered your render distance!";
                  this.requestDisconnect(renderMessage);
               }
            }
         }
      }

      ClientLevel minecartLevel = ClientAccess.zioMinecraft.level;

      AABB playerBounds = ClientAccess.zioMinecraft.player.getBoundingBox();
      Class<MinecartTNT> minecartType = MinecartTNT.class;
      AABB expandedMinecartBounds = playerBounds.inflate(8.0D);
      Predicate<MinecartTNT> primedMinecart = MinecartTNT::isPrimed;

      if (!minecartLevel.getEntitiesOfClass(minecartType, expandedMinecartBounds, primedMinecart).isEmpty()) {
         if (this.tNTMinecartSetting.getValue()) {
            this.requestDisconnect("You are at risk of getting blown up by a TNT minecart!");
         }
      }

      if (this.crystalSetting.getValue()) {
         ClientAccess.zioMinecraft.level.entitiesForRendering().forEach(
            (Consumer<Entity>)entity -> {
               if (entity instanceof EndCrystal) {
                  Entity distanceCrystal = entity;

                  double crystalDistanceSquared = ClientAccess.zioMinecraft.player.distanceToSqr(distanceCrystal);
                  if (crystalDistanceSquared <= 128.0D) {
                     Entity positionCrystal = entity;
                     Vec3 crystalPosition = positionCrystal.position();
                     LocalPlayer damagePlayer = ClientAccess.zioMinecraft.player;

                     double explosionDamage = DamageUtils.getCrystalDamage(
                        crystalPosition, damagePlayer
                     );

                     if (explosionDamage
                        >= EntityUtils.getPlayerHealthWithAbsorption()
                        )
                      {
                        this.requestDisconnect("You are at risk of getting blown up by an end crystal!");
                     }
                  }
               }
            }
         );
      }

      int health = (int)EntityUtils.getPlayerHealthWithAbsorption();

      if (ClientAccess.zioMinecraft.player.isDeadOrDying() || health == 0) {
         return;
      }

      if (ClientAccess.zioMinecraft.isWindowActive()) {
         if (this.unfocusedOnlySetting.getValue()) {
            return;
         }
      }

      int totemCount = ClientAccess.zioMinecraft.player.getInventory().getNonEquipmentItems().stream().filter((Predicate<ItemStack>)stack -> {
            Item totem = Items.TOTEM_OF_UNDYING;
            ItemStack totemStack = stack;

            return totemStack.is(totem);
         }).mapToInt((ToIntFunction<ItemStack>)ItemStack::getCount).sum();
      InventoryMenu inventoryMenu = ClientAccess.zioMinecraft.player.inventoryMenu;

      totemCount += inventoryMenu.getInputGridSlots().stream()
         .filter((Predicate<Slot>)this::containsTotem)
         .mapToInt((ToIntFunction<Slot>)slot -> {
               Slot countSlot = slot;
               ItemStack slotStack = countSlot.getItem();
               return slotStack.getCount();
         }).sum();

      if (ClientAccess.zioMinecraft.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
         ItemStack offhandCountStack = ClientAccess.zioMinecraft.player.getOffhandItem();
         totemCount += offhandCountStack.getCount();
      }

      Integer totemLimit = this.totemCountSetting.getValue();
      boolean totemConditionMet = totemCount <= totemLimit;

      if (!this.totemsSetting.getValue()) {
         totemConditionMet = true;
      }

      Integer healthLimit = this.healthSetting.getValue();
      if (health <= healthLimit && totemConditionMet) {
         String healthMessage = "Your health is too low! (" + String.valueOf(health) + ").";
         this.requestDisconnect(healthMessage);
      }
   }

   private boolean containsTotem(Slot slot) {
      ItemStack stackSnapshot = slot.getItem();
      Item totem = Items.TOTEM_OF_UNDYING;

      return stackSnapshot.is(totem);
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (this.isWorldUnavailable()) {
         this.pendingDisconnect = null;
      }

      if (this.pendingDisconnect != null) {
         if (this.disconnectTimer.hasElapsed(1500L)) {
            this.pendingDisconnect.run();
            this.pendingDisconnect = null;
         }
      }
   }

   private boolean isElytraDurabilityUnsafe() {
      Inventory equippedInventorySnapshot = ClientAccess.zioMinecraft.player.getInventory();
      EquipmentSlot chestSlotSnapshot = EquipmentSlot.CHEST;
      int chestIndex = chestSlotSnapshot.getIndex(36);

      ItemStack equippedStack = equippedInventorySnapshot.getItem(chestIndex);
      Item elytra = Items.ELYTRA;

      if (equippedStack.is(elytra)) {
         if (equippedStack.isDamageableItem()) {
            float equippedDurability = InventoryUtils.getDurabilityPercent(equippedStack);
            Integer durabilityLimit = this.durabilitySetting.getValue();
            boolean equippedDurabilitySafe = equippedDurability > durabilityLimit.intValue();
            if (equippedDurabilitySafe) {
               return false;
            }

            if (this.assumeInventorySetting.getValue()) {
               NonNullList nonEquipmentStacks = ClientAccess.zioMinecraft.player.getInventory().getNonEquipmentItems();
               Iterator inventoryStacks = nonEquipmentStacks.iterator();

               while (inventoryStacks.hasNext()) {
                  ItemStack candidateStack = (ItemStack)inventoryStacks.next();
                  Item elytraItem = Items.ELYTRA;
                  if (candidateStack.is(elytraItem)) {
                     if (!candidateStack.isDamageableItem()) {
                        return false;
                     }

                     float candidateDurability = InventoryUtils.getDurabilityPercent(candidateStack);
                     Integer candidateThreshold = this.durabilitySetting.getValue();
                     if (candidateDurability > candidateThreshold.intValue()) {
                        return false;
                     }
                  }
               }
            }

            return true;
         }
      }

      return false;
   }

   @EventHandler
   private void onPacketReceived(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundDisconnectPacket) {
         this.pendingDisconnect = null;
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      illegalDisconnect = (IllegalDisconnectModule)modules.getModule(
         IllegalDisconnectModule.class
      );
      autoReconnect = (AutoReconnectModule)modules.getModule(
         AutoReconnectModule.class
      );
   }

   public AutoLogModule() {
      super("AutoLog", "Logs you out so you don't have to fight anyone.", ModuleCategory.COMBAT, new String[0]);
      this.healthSetting = this.add(new NumberSetting<>("Health", 12, 1, 20).withUnit("hp"));
      this.invincTimeoutSetting = this.add(new NumberSetting<>("InvincTimeout", 0.0F, 0.0F, 4.0F).withUnit("s"));
      this.reconnectSetting = this.add(new BooleanSetting("Reconnect", false));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", true));
      this.gearedOnlySetting = this.add(new BooleanSetting("GearedOnly", false));
      this.unfocusedOnlySetting = this.add(new BooleanSetting("UnfocusedOnly", false));
      this.onRenderSetting = this.add(new BooleanSetting("OnRender", false));
      this.crystalSetting = this.add(new BooleanSetting("Crystal", false));
      this.tNTMinecartSetting = this.add(new BooleanSetting("TNTMinecart", false));
      this.totemsSetting = this.add(new BooleanSetting("Totems", false).markVisibilityDependency());
      Predicate totemCountVisibility = unused -> {
         return this.totemsSetting.isExpanded();
      };
      this.totemCountSetting = this.add(new NumberSetting<>("TotemCount", 1, 0, 16, totemCountVisibility));
      this.elytraSetting = this.add(new BooleanSetting("Elytra", false).markVisibilityDependency());
      this.durabilitySetting = this.add(new NumberSetting<>("Durability", 5, 1, 100).withUnit("%").dependsOn(this.elytraSetting));
      this.assumeInventorySetting = this.add(new BooleanSetting("AssumeInventory", false).dependsOn(this.elytraSetting));
      this.yLevelSetting = this.add(new BooleanSetting("YLevel", false).markVisibilityDependency());
      this.ySetting = this.add(new NumberSetting<>("Y", 64.0F, -64.0F, 300.0F).dependsOn(this.yLevelSetting));
      this.waypointsSetting = this.add(new BooleanSetting("Waypoints", false).markVisibilityDependency());
      this.distanceSetting = this.add(new NumberSetting<>("Distance", 16.0F, 8.0F, 128.0F).withUnit("m").dependsOn(this.waypointsSetting));
      this.disconnectTimer = new ElapsedTimer();
      this.disconnectTimeMillis = -1L;
   }
}
