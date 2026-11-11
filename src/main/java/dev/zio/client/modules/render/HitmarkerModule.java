/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.commands.FakeplayerCommand;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.HudRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.utils.network.InteractPacketUtils;
import dev.zio.client.audio.BuiltinSound;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.managers.SoundManager;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.render.VertexGeometry;
import dev.zio.client.utils.collections.NamedIdentifier;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.IdentifierSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.zio.mixin.ServerboundInteractPacketAccessor;
import java.awt.Color;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class HitmarkerModule extends Module {
   private static CrosshairModule crosshair;
   final Setting<Boolean> drawSetting;
   private final Setting<Float> lengthSetting;
   private final Setting<Float> timeSetting;
   private final Setting<Float> fadeTimeSetting;
   private final Setting<Color> colorSetting;
   final Setting<Boolean> soundSetting;
   private final Setting<NamedIdentifier> soundTypeSetting;
   private final Setting<Float> volumeSetting;
   final Setting<Boolean> targetsSetting;
   private final Setting<Boolean> animalsSetting;
   private final Setting<Boolean> hostilesSetting;
   private final Setting<Boolean> playersSetting;
   private final Setting<Boolean> crystalsSetting;
   public long lastHitTime;
   public boolean soundPending;

   public boolean isEligibleTarget(Entity entity) {
      Boolean animalsEnabled = this.animalsSetting.getValue();
      if (animalsEnabled && entity instanceof AgeableMob) {
         return true;
      }
      Boolean hostilesEnabled = this.hostilesSetting.getValue();
      if (hostilesEnabled && entity instanceof Enemy) {
         return true;
      }
      Boolean playersEnabled = this.playersSetting.getValue();
      if (playersEnabled && entity instanceof Player) {
         return true;
      }
      return (this.crystalsSetting.getValue()) && entity instanceof EndCrystal;
   }

   private float getMarkerOpacity() {
      float secondsCapture = (this.timeSetting.getValue());

      long durationMillis = (long)(secondsCapture * 1e+03F);
      secondsCapture = (this.fadeTimeSetting.getValue());

      long fadeMillis = (long)(secondsCapture * 1e+03F);
      long elapsedMillis = System.currentTimeMillis() - this.lastHitTime;
      if (elapsedMillis < durationMillis - fadeMillis) {

         return 1.0F;
      } else {

         return 1.0F - (float)(elapsedMillis - (durationMillis - fadeMillis)) / (float)fadeMillis;
      }
   }

   static {

      crosshair = (CrosshairModule)ClientContext.getModule(CrosshairModule.class);
   }

   @Override
   public void onToggle() {

      this.lastHitTime = -1L;
      this.soundPending = false;
   }

   @EventHandler
   private void onHudRender(HudRenderEvent event) {
      if (this.isWorldUnavailable()) {
         return;
      }
      if (this.soundPending) {
         this.soundPending = false;
         Boolean soundEnabled = this.soundSetting.getValue();
         if (soundEnabled) {
            SoundManager soundManagerCapture = ClientContext.soundManager;
            NamedIdentifier soundIdentifierCapture = this.soundTypeSetting.getValue();
            float volume = (this.volumeSetting.getValue());
            soundManagerCapture.playSound(soundIdentifierCapture, volume);
         }
      }
      if (ClientAccess.zioMinecraft.options.hideGui) {
         return;
      }
      GameRenderer gameRenderer = ClientAccess.zioMinecraft.gameRenderer;
      Camera camera = gameRenderer.getMainCamera();
      if (camera.isDetached()) {
         return;
      }
      long hitTimeCapture = this.lastHitTime;

      if (hitTimeCapture == -1L) {
         return;
      }

      long elapsedMillis = System.currentTimeMillis()
         - this.lastHitTime;
      float durationCapture = (this.timeSetting.getValue());

      long durationMillis = (long)(durationCapture * 1e+03F);
      if (elapsedMillis > durationMillis) {

         this.lastHitTime = -1L;
         return;
      }

      float opacity = this.getMarkerOpacity();
      if (!(opacity > 0.0F)) {
         return;
      }
      Boolean drawEnabled = this.drawSetting.getValue();
      if (!drawEnabled) {
         return;
      }
      float scaledWidth = (ClientAccess.zioMinecraft.getWindow()).getGuiScaledWidth();

      float centerX = scaledWidth / 2.0F;
      float scaledHeight = (ClientAccess.zioMinecraft.getWindow()).getGuiScaledHeight();

      float halfHeight = scaledHeight / 2.0F;

      float centerY = halfHeight - 0.5F;
      CrosshairModule crosshairForOffset = crosshair;
      if (!crosshairForOffset.isToggled()) {

         centerX -= 0.5F;
      } else {

         centerY += 0.5F;
      }

      Float lengthValue = this.lengthSetting.getValue();
      float outerOffset = 2.0F + lengthValue;
      Setting<Color> colorOption = this.colorSetting;
      Color markerColor = ColorUtils.multiplyAlpha(
         colorOption.getValue(), opacity
      );
      VertexConsumer buffer = GuiDrawUtils.getLineVertices();
      PoseStack poseStackForPush = event.getPoseStack();
      poseStackForPush.pushPose();
      CrosshairModule crosshairForTranslation = crosshair;
      if (crosshairForTranslation.isToggled()) {
         PoseStack translationStackCapture = event.getPoseStack();

         translationStackCapture.translate(-0.6D, 0.0D, 0.0D);
      }

      PoseStack topLeftStackCapture = event.getPoseStack();
      float topLeftOuterX = centerX - outerOffset;
      float lineYCapture = centerY - outerOffset;

      float lineXCapture = centerX - 2.0F;

      float topLeftEndY = centerY - 2.0F;
      drawMarkerLine(
         topLeftStackCapture, buffer, markerColor, topLeftOuterX, lineYCapture, lineXCapture, topLeftEndY
      );
      PoseStack bottomLeftStackCapture = event.getPoseStack();
      float bottomLeftOuterX = centerX - outerOffset;
      lineYCapture = centerY + outerOffset;

      lineXCapture = centerX - 2.0F;

      float bottomLeftEndY = centerY + 2.0F;
      drawMarkerLine(
         bottomLeftStackCapture, buffer, markerColor, bottomLeftOuterX, lineYCapture, lineXCapture, bottomLeftEndY
      );
      PoseStack topRightStackCapture = event.getPoseStack();

      float topRightInnerX = centerX + 2.0F;

      lineYCapture = centerY - 2.0F;
      lineXCapture = centerX + outerOffset;
      float topRightEndY = centerY - outerOffset;
      drawMarkerLine(
         topRightStackCapture, buffer, markerColor, topRightInnerX, lineYCapture, lineXCapture, topRightEndY
      );
      PoseStack bottomRightStackCapture = event.getPoseStack();

      float bottomRightInnerX = centerX + 2.0F;

      lineYCapture = centerY + 2.0F;
      lineXCapture = centerX + outerOffset;
      float bottomRightEndY = centerY + outerOffset;
      drawMarkerLine(
         bottomRightStackCapture, buffer, markerColor, bottomRightInnerX, lineYCapture, lineXCapture, bottomRightEndY
      );
      PoseStack poseStackForPop = event.getPoseStack();
      poseStackForPop.popPose();

   }

   private static void drawMarkerLine(
      PoseStack poseStack, VertexConsumer buffer, Color color, float startX, float startY, float endX, float endY
   ) {
      VertexGeometry.appendLine(buffer, poseStack.last(), startX, startY, 0.0F, endX, endY, 0.0F, 1.0F, color.hashCode());
   }

   @EventHandler(priority = -100)
   private void onConnectionSendPacketEvent(
      PacketEvent.Send event
   ) {
      if (this.isWorldUnavailable()) {
         return;
      }
      // 26.1.2: el ATTACK viaja en ServerboundAttackPacket dedicado (el record
      // interact solo cubre interact/interact-at).
      if (!(event.getPacket() instanceof ServerboundAttackPacket packet)) {
         return;
      }
      ClientLevel levelCapture = ClientAccess.zioMinecraft.level;
      int entityId = packet.entityId();
      Entity target = levelCapture.getEntity(entityId);
      long elapsedMillis = System.currentTimeMillis()
         - this.lastHitTime;

      if (elapsedMillis <= 100L) {
         return;
      }

      boolean simulatedPlayer = InteractPacketUtils.getEntity(packet.entityId()) instanceof FakeplayerCommand.SimulatedPlayer;
      if (event.isCancelled() && !simulatedPlayer) {
         return;
      }

      if (target == null) {
         return;
      }
      if (target.level() == null || target == ClientAccess.zioMinecraft.player) {
         return;
      }
      Level targetLevel = target.level();
      if (!targetLevel.isClientSide()) {
         return;
      }
      if (this.isEligibleTarget(target)) {
         this.lastHitTime = System.currentTimeMillis();
         this.soundPending = true;
      }
   }

   public static void bindCrosshair(ModuleRegistry registry) {
      crosshair = (CrosshairModule)registry.getModule(
         CrosshairModule.class
      );
   }

   public HitmarkerModule() {
      super("Hitmarker", "Marks your screen when you hit entities.", ModuleCategory.RENDER, new String[0]);
      this.drawSetting = this.add(new BooleanSetting("Draw", true).markVisibilityDependency());
      Predicate<Object> lengthVisibility = ignored -> this.drawSetting.isExpanded();
      this.lengthSetting = this.add(new NumberSetting<>("Length", 3.0F, 0.1F, 10.0F, lengthVisibility));
      Predicate<Object> timeVisibility = ignored -> this.drawSetting.isExpanded();
      this.timeSetting = this.add(new NumberSetting<>("Time", 1.0F, 0.5F, 3.0F, timeVisibility).withUnit("s"));
      Predicate<Object> fadeTimeVisibility = ignored -> this.drawSetting.isExpanded();
      this.fadeTimeSetting = this.add(new NumberSetting<>("FadeTime", 0.5F, 0.5F, 3.0F, fadeTimeVisibility).withUnit("s"));
      Predicate<Object> colorVisibility = ignored -> this.drawSetting.isExpanded();
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-1581409793, true), colorVisibility));
      this.soundSetting = this.add(new BooleanSetting("Sound", false).markVisibilityDependency());
      this.soundTypeSetting = this.add(new IdentifierSetting("Sound", BuiltinSound.NEVERLOSE.getIdentifier()).dependsOn(this.soundSetting));
      Predicate<Object> volumeVisibility = ignored -> this.soundSetting.isExpanded();
      this.volumeSetting = this.add(new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F, volumeVisibility));
      this.targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency());
      Predicate<Object> animalsVisibility = ignored -> this.targetsSetting.isExpanded();
      this.animalsSetting = this.add(new BooleanSetting("Animals", false, animalsVisibility));
      Predicate<Object> hostilesVisibility = ignored -> this.targetsSetting.isExpanded();
      this.hostilesSetting = this.add(new BooleanSetting("Hostiles", false, hostilesVisibility));
      Predicate<Object> playersVisibility = ignored -> this.targetsSetting.isExpanded();
      this.playersSetting = this.add(new BooleanSetting("Players", true, playersVisibility));
      Predicate<Object> crystalsVisibility = ignored -> this.targetsSetting.isExpanded();
      this.crystalsSetting = this.add(new BooleanSetting("Crystals", false, crystalsVisibility));

      this.lastHitTime = -1L;
      this.soundPending = false;

      this.setDrawn(false);
      this.soundTypeSetting.setConfigName("Type");
   }

}
