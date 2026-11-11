/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.ScreenChangeEvent;
import dev.zio.client.events.ConnectScreenConnectEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.managers.ServerStateManager;
import dev.zio.client.utils.collections.IntPair;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.render.HudHorizontalAlignment;
import dev.zio.client.render.ResizableHudWidgetRenderer;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.HudWidgetModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.StringSetting;
import dev.zio.client.settings.options.HudAnchor;
import dev.zio.client.settings.options.WelcomerMode;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Mth;

public class WelcomerModule extends HudWidgetModule {
   public final Setting<WelcomerMode> modeSetting;
   public final Setting<String> textSetting;
   public final Setting<Boolean> smartSetting;
   private final List<HudNotification> notifications;
   private final ElapsedTimer connectionScreenTimer;
   private String lastMusicNotificationText;
   private boolean connectionScreenPending;

   private boolean tryAddServerWelcomeNotification() {
      ElapsedTimer timer = this.connectionScreenTimer;
      if (timer.hasElapsed(50L)) {
         return false;
      }
      Minecraft minecraft = ClientAccess.zioMinecraft;
      if (minecraft.isLocalServer()) {
         return false;
      }
      ClientPacketListener connection = ClientAccess.zioMinecraft.player.connection;
      ServerData server = connection.getServerData();
      if (server == null) {
         return false;
      }
      this.notifications.add(new HudNotification(
         "Welcome to " + server.ip,
         3000L,
         this.getHudColor(this.renderer.getY())
      ));
      return true;
   }

   private void updateSmartNotifications() {
      Setting<Boolean> smartOption = this.smartSetting;
      Boolean smartEnabled = smartOption.getValue();
      if (!smartEnabled || this.isInHudEditor()) {
         this.finishLagNotification();
         return;
      }
      if (this.tryAddServerWelcomeNotification()) {
         return;
      }
      this.updateMusicNotification();
      Iterator<HudNotification> activeNotifications = this.notifications.iterator();

      while (activeNotifications.hasNext()) {
         HudNotification notification = activeNotifications.next();
         if (notification instanceof LagNotification || notification instanceof MusicNotification) {
            return;
         }
      }

      long idleDuration = System.currentTimeMillis();
      ServerStateManager networkActivity = ClientContext.serverStateManager;

      idleDuration -= networkActivity.getLastPacketTime();
      long idleForClamp = idleDuration;
      long idleMillis = Math.max(idleForClamp, 0L);
      Class<LagOMeterModule> lagWidgetType = LagOMeterModule.class;
      ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
      LagOMeterModule lagWidget = (LagOMeterModule)moduleRegistry.getModule(
         lagWidgetType
      );
      if (this.overlapsWidget(lagWidget)
         && !this.connectionScreenPending) {

         if (idleMillis > 1000L) {
            Minecraft minecraft = ClientAccess.zioMinecraft;
            if (!minecraft.isLocalServer()) {
               int playerTicks = ClientAccess.zioMinecraft.player.tickCount;

               if (playerTicks >= 40) {
                  ElapsedTimer timer = this.connectionScreenTimer;

                  if (timer.hasElapsed(500L)) {
                     this.notifications.add(new LagNotification());
                     return;
                  }
               }
            }
         }
      }

      this.finishLagNotification();
   }

   private void finishLagNotification() {
      Iterator<HudNotification> activeNotifications = this.notifications.iterator();

      while (activeNotifications.hasNext()) {
         HudNotification notification = activeNotifications.next();
         if (notification instanceof LagNotification lagNotification) {

            lagNotification.finish();
            break;
         }
      }
   }

   @Override
   public float[] calculateDimensions() {
      float maximumWidth = 0.0F;
      float accumulatedProgress = 0.0F;
      float hudScale = hud.getTextScale();

      for (int index = this.notifications.size() - 1;
         index >= 0;
         index--
      ) {
         HudNotification notification = this.notifications.get(index);

         float transitionMillis = 250.0F;
         float progress = this.getNotificationProgress(notification, transitionMillis, 0.0F);

         float scaledProgress = progress * 10.0F;

         float maximumProgress = 1.0F;
         progress = Math.min(scaledProgress, maximumProgress);

         float easingUpperBound = 1.0F;

         float easingRemainder = 1.0F - progress;

         progress = easingUpperBound - easingRemainder * (1.0F - progress);
         FontRenderer textRenderer = FontRenderer.INSTANCE;

         float animatedWidth = textRenderer.getWidth(
               notification.getText()
            )
            * progress;
         maximumWidth = Math.max(animatedWidth, maximumWidth);
         accumulatedProgress += progress;

         if (accumulatedProgress > 1.0F) {
            break;
         }
      }

      float[] dimensions = new float[2];
      dimensions[0] = maximumWidth * hudScale;
      dimensions[1] = (
            FontRenderer.INSTANCE
                  .getHeight()
               + 1
         )
         * hudScale;
      return dimensions;
   }

   private boolean overlapsWidget(HudWidgetModule widget) {
      if (!widget.isToggled() || this.isInHudEditor()) {
         return false;
      }
      if (this.getAnchor() != HudAnchor.NONE) {
         return widget.getAnchor() == this.getAnchor();
      }
      if (this.renderer.getDimensions() == null || widget.getRenderer().getDimensions() == null) {
         return false;
      }

      IntPair welcomerPosition = new IntPair((int)this.renderer.getX(), (int)this.renderer.getY());
      IntPair widgetPosition = new IntPair((int)widget.getRenderer().getX(), (int)widget.getRenderer().getY());
      double distance = welcomerPosition.distanceTo(widgetPosition);
      return distance <= 100.0D;
   }

   private boolean slidesUp(HudNotification notification) {

      long time = notification.getStartTimeMillis();

      boolean evenStartTime = time % 2L == 0L;
      time = System.currentTimeMillis();

      time -= notification.getStartTimeMillis();

      return time <= 500L ? !evenStartTime : evenStartTime;
   }

   private float getNotificationProgress(
      HudNotification notification, float transitionMillis, float progressOffset
   ) {

      long timestamp = notification.getEndTimeMillis();

      long lifetimeMillis = timestamp - notification.getStartTimeMillis();
      timestamp = System.currentTimeMillis();

      long elapsedMillis = timestamp - notification.getStartTimeMillis();

      float progress = 1.0F;
      if ((float)elapsedMillis <= transitionMillis) {
         progress = (float)elapsedMillis / transitionMillis;
      } else if ((float)elapsedMillis >= (float)lifetimeMillis - transitionMillis) {
         float fadeOutFraction = (float)(lifetimeMillis - elapsedMillis) / transitionMillis;
         progress = Math.max(fadeOutFraction, 0.0F);
      }

      float offsetProgress = progress - progressOffset;

      float maximumProgress = 1.0F;
      return Mth.clamp(offsetProgress, 0.0F, maximumProgress);
   }

   @Override
   public void renderContent(PoseStack poses) {

      this.updateSmartNotifications();
      this.notifications.removeIf(
         notification -> {
            long nowMillis = System.currentTimeMillis();

            if (nowMillis < notification.getEndTimeMillis()) {
               Setting<Boolean> smartOption = this.smartSetting;
               Boolean smartEnabled = smartOption.getValue();
               if (smartEnabled || notification instanceof GreetingNotification) {
                  return false;
               }
            }

            return true;
         }
      );
      float[] dimensions = this.calculateDimensions();
      float hudScale = hud.getTextScale();
      float accumulatedProgress = 0.0F;

      for (int index = this.notifications.size() - 1;
         index >= 0;
         index--
      ) {
         HudNotification notification = this.notifications.get(index);
         notification.update();

         float opacityTransitionMillis = 250.0F;
         float opacity = this.getNotificationProgress(notification, opacityTransitionMillis, accumulatedProgress);

         float slideTransitionMillis = 500.0F;
         float progress = this.getNotificationProgress(notification, slideTransitionMillis, 0.0F);
         boolean slideUp = this.slidesUp(notification);
         if (opacity > 0.1D) {
            float horizontalOffset = 0.0F;
            if (this.renderer
                  .getHorizontalAlignment()
               == HudHorizontalAlignment.CENTER) {
               float availableWidth = dimensions[0];
               FontRenderer measurementRenderer = FontRenderer.INSTANCE;

               float remainingWidth = availableWidth
                  - measurementRenderer.getWidth(
                        notification.getText()
                     )
                     * hudScale;

               horizontalOffset = remainingWidth / 2.0F;
            }

            double easingRemainder = 1.0F - progress;

            float easedProgress = (float)(1.0D - Math.pow(easingRemainder, 5.0D));
            int direction = slideUp ? -1 : 1;

            float signedSlideDistance = direction * (-5);
            float verticalOffset = signedSlideDistance * (1.0F - easedProgress);
            verticalOffset *= hudScale;
            FontRenderer textRenderer = FontRenderer.INSTANCE;

            String text = notification.getText();
            float textX = horizontalOffset / hudScale;
            textRenderer.drawWithShadow(
               poses,
               text,
               textX,
               verticalOffset,
               hudScale,
               ColorUtils.withAlpha(
                  notification.getColor(), opacity
               )
            );
         }

         accumulatedProgress += opacity;

         if (accumulatedProgress >= 1.0F) {
            break;
         }
      }
   }

   @EventHandler
   private void onMinecraftSetScreenEvent(
      ScreenChangeEvent.Before event
   ) {
      try {
         java.lang.invoke.MethodHandles.lookup().ensureInitialized(net.minecraft.client.gui.screens.Screen.class);
      } catch (IllegalAccessException failure) {
         RuntimeException decoded = new RuntimeException(failure.toString());
         throw new RuntimeException(decoded.getClass().getName() + " :  null  : " + decoded);
      }
      if (event.getNewScreen() == null
         && this.connectionScreenPending) {
         this.connectionScreenPending = false;
         this.connectionScreenTimer.reset();
      }
   }

   public boolean shouldHideWidget(HudWidgetModule widget) {
      if (!this.isToggled()) {
         return false;
      }
      Setting<Boolean> smartOption = this.smartSetting;
      Boolean smartEnabled = smartOption.getValue();
      return smartEnabled && this.overlapsWidget(widget) && !this.isInHudEditor();
   }

   @EventHandler
   private void onConnectScreenConnectEvent(ConnectScreenConnectEvent event) {
      this.connectionScreenPending = true;
   }

   private void updateMusicNotification() {
      Class<MusicModule> musicWidgetType = MusicModule.class;
      ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
      MusicModule musicWidget = (MusicModule)moduleRegistry.getModule(musicWidgetType);
      if (!this.isInHudEditor()) {
         int statusOrTextLength = MediaSessionBridge.playbackStatus;

         if (statusOrTextLength == 4) {
            if (this.overlapsWidget(musicWidget)) {
               String mediaDisplayText = MediaSessionBridge.getDisplayText();
               String text = mediaDisplayText.trim();
               statusOrTextLength = text.length();

               if (statusOrTextLength < 3) {
                  this.lastMusicNotificationText = null;
                  return;
               }

               if (text.equals(this.lastMusicNotificationText)) {
                  return;
               }

               this.notifications.add(
                  new MusicNotification(
                     "♪ " + text,
                     3000L,
                     this.getHudColor(
                        this.renderer
                           .getY()
                     )
                  )
               );
               this.lastMusicNotificationText = text;
               return;
            }
         }

         this.lastMusicNotificationText = null;
      }
   }

   public WelcomerModule() {
      super("Welcomer", new String[0]);

      this.modeSetting = this.add(new EnumSetting<>("Mode", WelcomerMode.NAME));

      StringSetting textOption = new StringSetting("Text", "Welcome to Zio.", value -> {
         return this.modeSetting.getValue() == WelcomerMode.CUSTOM;
      });
      this.textSetting = this.add(textOption);

      this.smartSetting = this.add(new BooleanSetting("Smart", true));
      this.notifications = new ArrayList<>();
      this.connectionScreenTimer = new ElapsedTimer();
      ResizableHudWidgetRenderer widget = new ResizableHudWidgetRenderer(this);
      this.setRenderer(widget);
      this.notifications.add(new GreetingNotification(this));
   }

}
