/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.utils.math.MathConstants;
import java.util.function.Predicate;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.ChatMessageEvent;
import dev.zio.client.events.InputPressEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.MouseHandlerTurnPlayerEvent;
import dev.zio.client.managers.InputOverrideManager;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import dev.zio.mixin.MinecraftAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.InteractionHand;

public class AntiAFKModule extends Module {
   private final Setting<Float> delaySetting;
   private final Setting<Boolean> autoReplySetting;
   private final Setting<Boolean> jumpSetting;
   private final Setting<Boolean> sneakSetting;
   private final Setting<Boolean> rotateSetting;
   final Setting<Boolean> attackSetting;
   private final Setting<Boolean> onlySwingSetting;
   private final ElapsedTimer idleTimer;
   private final ElapsedTimer actionTimer;
   private boolean jumpActionPending;
   private boolean sneakActionPending;

   private List<Runnable> createActions() {
      List<Runnable> actions = new ArrayList<>();
      Setting<Boolean> jumpSnapshot = this.jumpSetting;
      Boolean jumpEnabled = jumpSnapshot.getValue();
      if (jumpEnabled) {
         actions.add(() -> this.jumpActionPending = true);
      }

      Setting<Boolean> sneakSnapshot = this.sneakSetting;
      Boolean sneakEnabled = sneakSnapshot.getValue();
      if (sneakEnabled) {
         actions.add(() -> this.sneakActionPending = true);
      }

      Setting<Boolean> rotateSnapshot = this.rotateSetting;
      Boolean rotateEnabled = rotateSnapshot.getValue();
      if (rotateEnabled) {
         Random rotationRandom = new Random();
         actions.add(
            () -> {
               LocalPlayer rotationPlayerSnapshot = ClientAccess.zioMinecraft.player;
               float yaw = rotationRandom.nextFloat(
                  MathConstants.FULL_TURN_DEGREES
               );
               rotationPlayerSnapshot.setYRot(yaw);
               rotationPlayerSnapshot = ClientAccess.zioMinecraft.player;

               float pitch = rotationRandom.nextFloat(1.8e+02F)
                  - MathConstants.NINETY_DEGREES;
               rotationPlayerSnapshot.setXRot(pitch);
            }
         );
      }

      Setting<Boolean> attackSnapshot = this.attackSetting;
      Boolean attackEnabled = attackSnapshot.getValue();
      if (attackEnabled) {
         actions.add(() -> {
            Setting<Boolean> onlySwingSnapshot = this.onlySwingSetting;
            Boolean onlySwing = onlySwingSnapshot.getValue();
            if (onlySwing) {
               LocalPlayer swingPlayer = ClientAccess.zioMinecraft.player;
               InteractionHand swingHand = InteractionHand.MAIN_HAND;
               swingPlayer.swing(swingHand);
            } else {
               ((MinecraftAccessor)ClientAccess.zioMinecraft).attack();
            }
         });
      }

      return actions;
   }

   private boolean isIdle() {
      ElapsedTimer timerSnapshot = this.idleTimer;
      Setting<Float> delaySnapshot = this.delaySetting;
      float delaySeconds = delaySnapshot.getValue();

      long delayMillis = (long)(delaySeconds * 1e+03F);

      return timerSnapshot.hasElapsed(delayMillis);
   }

   @Override
   public void onEnable() {
      this.idleTimer.reset();
      this.actionTimer.reset();
   }

   @EventHandler
   private void onInputActivity(InputPressEvent event) {
      this.idleTimer.reset();
   }

   @EventHandler
   private void onMouseHandlerTurnPlayerEvent(MouseHandlerTurnPlayerEvent event) {
      this.idleTimer.reset();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (!this.isIdle()) {
         return;
      }
      KeyMapping jumpKeySnapshot = ClientAccess.zioMinecraft.options.keyJump;
      if (jumpKeySnapshot.isDown()) {
         Setting<Boolean> jumpSettingSnapshot = this.jumpSetting;
         Boolean jumpEnabled = jumpSettingSnapshot.getValue();
         if (jumpEnabled) {
            boolean jumpPressed = false;
            KeyMapping jumpKey = ClientAccess.zioMinecraft.options.keyJump;
            jumpKey.setDown(jumpPressed);
         }
      }

      ElapsedTimer timerCapture = this.actionTimer;
      Setting<Float> delaySnapshot = this.delaySetting;
      Float delayValue = delaySnapshot.getValue();
      double delaySecondsCapture = delayValue.floatValue();
      TimeUnit delayUnit = TimeUnit.SECONDS;

      if (timerCapture.hasElapsed(delaySecondsCapture, delayUnit)) {
         List<Runnable> actions = this.createActions();
         Collections.shuffle(actions);
         if (!actions.isEmpty()) {
            actions.get(0).run();
         }

         timerCapture = this.actionTimer;

         timerCapture.reset();
      }

      if (this.jumpActionPending) {
         InputOverrideManager jumpOverridesSnapshot = ClientContext.inputOverrides;
         jumpOverridesSnapshot.requestJump((true));
      }

      if (this.sneakActionPending) {
         InputOverrideManager sneakOverridesSnapshot = ClientContext.inputOverrides;
         sneakOverridesSnapshot.requestSneak((false));
      }

      this.jumpActionPending = this.sneakActionPending = false;
   }

   @EventHandler(priority = 100)
   private void onIncomingMessage(ChatMessageEvent event) {
      if (event.isCancelled()) {
         return;
      }
      Setting<Boolean> autoReplySnapshot = this.autoReplySetting;
      Boolean autoReplyEnabled = autoReplySnapshot.getValue();
      if (!autoReplyEnabled) {
         return;
      }
      if (!this.isIdle()) {
         return;
      }
      if (event.getStage() != EventStage.Pre || event.getMessage() == null) {
         return;
      }
      String messageCapture = event.getMessage().getString();
      if (!ChatUtils.isIncomingWhisper(messageCapture)) {
         return;
      }

      BlockableEventLoop<?> replyClient = ClientAccess.zioMinecraft;
      Runnable replyAction = () -> {
         ClientPacketListener replyConnectionCapture = ClientAccess.zioMinecraft.player.connection;
         LocalPlayer replyPlayer = ClientAccess.zioMinecraft.player;
         String playerName = replyPlayer.getName().getString();
         double randomSample = Math.random();
         double replySuffixValue = randomSample * 10000.0D;
         int replySuffix = (int)Math.floor(replySuffixValue);
         String replyCommand = "r Hello! This is auto reply talking. \u0001 is currently AFK :'). [\u0001]";

         replyCommand = replyCommand.replaceFirst("\u0001", java.util.regex.Matcher.quoteReplacement(String.valueOf(playerName)));
         replyCommand = replyCommand.replaceFirst("\u0001", Integer.toString(replySuffix));
         replyConnectionCapture.sendCommand(replyCommand);
      };
      replyClient.executeIfPossible(replyAction);
   }

   public AntiAFKModule() {
      super("AntiAFK", "Prevents you from being kicked for AFK-ing.", ModuleCategory.MISC, new String[0]);
      this.delaySetting = this.add(new NumberSetting<>("Delay", 15.0F, 1.0F, 60.0F).withUnit("s"));
      Boolean autoReplyDefault = false;
      this.autoReplySetting = this.add(new BooleanSetting("AutoReply", autoReplyDefault));
      Boolean jumpDefault = false;
      this.jumpSetting = this.add(new BooleanSetting("Jump", jumpDefault));
      Boolean sneakDefault = false;
      this.sneakSetting = this.add(new BooleanSetting("Sneak", sneakDefault));
      Boolean rotateDefault = false;
      this.rotateSetting = this.add(new BooleanSetting("Rotate", rotateDefault));
      Boolean attackDefault = false;
      this.attackSetting = this.add(new BooleanSetting("Attack", attackDefault));
      Predicate<Object> onlySwingVisible = ignored -> this.attackSetting.getValue();
      Boolean onlySwingDefault = false;
      this.onlySwingSetting = this.add(new BooleanSetting("OnlySwing", onlySwingDefault, onlySwingVisible));
      this.idleTimer = new ElapsedTimer();
      this.actionTimer = new ElapsedTimer();
   }

}
