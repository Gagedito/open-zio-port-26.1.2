/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;


import dev.zio.client.ClientAccess;
import dev.zio.client.events.ClientPacketListenerHandleExplosionEvent;
import dev.zio.client.events.ClientPacketListenerHandleSetEntityMotionEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.FishingHookPullEntityEvent;
import dev.zio.client.events.LocalPlayerMoveTowardsClosestSpaceEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.zio.client.utils.movement.VelocityModeHandler;
import dev.zio.client.utils.movement.GrimVelocityHandler;
import dev.zio.client.utils.movement.WallsVelocityHandler;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.utils.inventory.EnchantmentUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.utils.math.MathUtils;

import dev.zio.client.managers.RotationManager;
import java.util.function.Predicate;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.item.enchantment.Enchantments;

public class VelocityModule extends Module {
   public final Setting<ModeChoice> modeSetting;
   public final Setting<Integer> horizontalSetting;
   public final Setting<Integer> verticalSetting;
   public final Setting<Boolean> inverseSetting;
   public final Setting<Boolean> reduceSetting;
   public final Setting<Boolean> staticSetting;
   public final Setting<Boolean> sourcesSetting;
   public final Setting<Boolean> explosionsSetting;
   public final Setting<Boolean> pushSetting;
   public final Setting<Boolean> liquidsSetting;
   public final Setting<Boolean> fishingBobberSetting;
   private final EnumSelectionMap<VelocityModeHandler> modeHandlers;
   private boolean lungeMotionPending;

   @Override
   public String getInfo() {
      if (this.modeSetting.getValue() != ModeChoice.PLAIN) {
         ModeChoice mode = this.modeSetting.getValue();
         return FontRenderer.capitalize(mode.getName());
      }

      float horizontalDisplay = MathUtils.floorToDecimals(this.horizontalSetting.getValue(), 1);
      float verticalDisplay = MathUtils.floorToDecimals(this.verticalSetting.getValue(), 1);
      return "H" + horizontalDisplay + "% V" + verticalDisplay + "%";
   }

   @Override
   public void onDisable() {
      this.modeHandlers.getSelected().onDisable();
   }

   public boolean isLungeMotionPending() {
      return this.lungeMotionPending;
   }

   @EventHandler
   private void onClientPacketListenerHandleExplosionEvent(
      ClientPacketListenerHandleExplosionEvent event
   ) {
      this.modeHandlers.getSelected().onExplosion(event);
   }

   @EventHandler
   private void onFishingHookPullEntityEvent(FishingHookPullEntityEvent event) {
      if (this.fishingBobberSetting.getValue() && event.getEntity() == ClientAccess.zioMinecraft.player) {
         event.cancel();
      }
      this.modeHandlers.getSelected().onFishingPull(event);
   }

   @EventHandler
   private void onLocalPlayerMoveTowardsClosestSpaceEvent(
      LocalPlayerMoveTowardsClosestSpaceEvent event
   ) {
      if (this.pushSetting.getValue()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onClientPacketListenerHandleSetEntityMotionEvent(
      ClientPacketListenerHandleSetEntityMotionEvent event
   ) {
      this.modeHandlers.getSelected().onEntityMotion(event);
   }

   @EventHandler
   private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
      this.modeHandlers.getSelected().onUseItemOn(event);
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      if (this.isWorldUnavailable()) {
         return;
      }
      if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket
         && Action.STAB == actionPacket.getAction()
         && EnchantmentUtils.hasEnchantment(Enchantments.LUNGE, ClientAccess.zioMinecraft.player.getMainHandItem())) {
         this.lungeMotionPending = true;
      }
      this.modeHandlers.getSelected().onSendPacket(event);
   }

   @EventHandler(priority = -200)
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
      this.modeHandlers.getSelected().onTick();
   }

   public void setLungeMotionPending(boolean pending) {
      this.lungeMotionPending = pending;
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      this.modeHandlers.getSelected().onSendPosition(event);
   }

   @EventHandler
   private void onPacketReceive(
      PacketEvent.Receive event
   ) {
      this.modeHandlers.getSelected().onReceivePacket(event);
   }

   public static void bindModules(ModuleRegistry modules) {
      WallsVelocityHandler.bindModules(modules);
   }

   public VelocityModule() {
      super("Velocity", "Cancels all the pushing you receive.", ModuleCategory.MOVEMENT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", VelocityModule.ModeChoice.PLAIN));
      Predicate<Object> horizontalVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.PLAIN;
      this.horizontalSetting = this.add(new NumberSetting<>("Horizontal", 0, 0, 100, horizontalVisibility).withUnit("%"));
      Predicate<Object> verticalVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.PLAIN;
      this.verticalSetting = this.add(new NumberSetting<>("Vertical", 0, 0, 100, verticalVisibility).withUnit("%"));
      Predicate<Object> inverseVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.PLAIN;
      this.inverseSetting = this.add(new BooleanSetting("Inverse", false, inverseVisibility));
      Predicate<Object> reduceVisibility = ignored -> RotationManager.isMovementSyncEnabled() || this.modeSetting.getValue() == ModeChoice.WALLS;
      this.reduceSetting = this.add(new BooleanSetting("Reduce", false, reduceVisibility));
      Predicate<Object> staticVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.WALLS;
      this.staticSetting = this.add(new BooleanSetting("Static", false, staticVisibility).dependsOn(this.reduceSetting));
      this.sourcesSetting = this.add(new BooleanSetting("Sources", false).markAsGroup());
      this.explosionsSetting = this.add(new BooleanSetting("Explosions", true).dependsOn(this.sourcesSetting));
      this.pushSetting = this.add(new BooleanSetting("Push", true).dependsOn(this.sourcesSetting));
      this.liquidsSetting = this.add(new BooleanSetting("Liquids", true).dependsOn(this.sourcesSetting));
      this.fishingBobberSetting = this.add(new BooleanSetting("FishingBobber", true).dependsOn(this.sourcesSetting));
      this.modeHandlers = new EnumSelectionMap<>(this.modeSetting);
      this.modeHandlers.register(ModeChoice.PLAIN, new PlainVelocityHandler(this));
      this.modeHandlers.register(ModeChoice.GRIM, new GrimVelocityHandler(this));
      this.modeHandlers.register(ModeChoice.WALLS, new WallsVelocityHandler(this));
   }

   public enum ModeChoice implements NamedEntry {
      PLAIN("Plain"),
      GRIM("Grim"),
      WALLS("Walls");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
