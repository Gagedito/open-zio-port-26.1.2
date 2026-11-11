/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import dev.zio.client.ClientContext;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.render.ResizableHudWidgetRenderer;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.HudWidgetModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.StringSetting;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.regex.Matcher;

public class MusicModule extends HudWidgetModule {
   private final Setting<Boolean> prefixSetting;
   private final Setting<String> prefixTextSetting;

   private boolean shouldSkipMediaRendering() {
      if (!this.isInHudEditor()) {
         int playbackStatus = MediaSessionBridge.playbackStatus;

         return playbackStatus != 4;
      }

      return false;
   }

   @Override
   public void onEnable() {

   }

   @Override
   public float[] calculateDimensions() {

      if (!this.shouldSkipMediaRendering()) {

         Class<WelcomerModule> welcomerType = WelcomerModule.class;
         ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
         WelcomerModule welcomer = (WelcomerModule)moduleRegistry.getModule(welcomerType);
         if (!welcomer.shouldHideWidget(this)) {

            float[] dimensions = new float[2];
            FontRenderer textRenderer = FontRenderer.INSTANCE;
            dimensions[0] = textRenderer.getWidth(
               this.getMusicText()
            );
            dimensions[1] = FontRenderer.INSTANCE
               .getHeight();
            return dimensions;
         }
      }

      float[] hiddenDimensions = new float[2];
      hiddenDimensions[0] = 0.0F;
      hiddenDimensions[1] = 0.0F;
      return hiddenDimensions;
   }

   @Override
   public void renderContent(PoseStack poses) {

      if (this.shouldSkipMediaRendering()) {
         return;
      }

      Class<WelcomerModule> welcomerType = WelcomerModule.class;
      ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
      WelcomerModule welcomer = (WelcomerModule)moduleRegistry.getModule(welcomerType);
      if (welcomer.shouldHideWidget(this)) {
         return;
      }

      FontRenderer.INSTANCE.drawWithShadow(
         poses,
         this.getMusicText(),
         0.0F,
         0.0F,
         this.getHudColor(
            this.renderer
               .getY()
         )
      );
   }

   private String getMusicText() {
      String text = MediaSessionBridge.getDisplayText();
      if (this.isInHudEditor()) {
         int playbackStatus = MediaSessionBridge.playbackStatus;

         if (playbackStatus != 4) {

            text = "HydrachFM - f3dot";
         }
      }

      Setting<Boolean> prefixOption = this.prefixSetting;
      Boolean prefixEnabled = prefixOption.getValue();
      if (prefixEnabled) {
         Setting<String> prefixTextOption = this.prefixTextSetting;
         String musicText = String.valueOf(text);
         String prefixText = String.valueOf(prefixTextOption.getValue());
         text = "\u0001 \u0001"
            .replaceFirst("\u0001", Matcher.quoteReplacement(prefixText))
            .replaceFirst("\u0001", Matcher.quoteReplacement(musicText));
      }

      return text.trim();
   }

   public MusicModule() {
      super("Music", new String[0]);

      this.prefixSetting = this.add(new BooleanSetting("Prefix", false).markVisibilityDependency());

      this.prefixTextSetting = this.add(new StringSetting("Value", "♪").dependsOn(new Setting<?>[]{this.prefixSetting}));
      ResizableHudWidgetRenderer widget = new ResizableHudWidgetRenderer(this);
      widget.enableHudScale();
      this.setRenderer(widget);
   }

}
