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
import dev.zio.client.events.HudRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.utils.InterpolatedFloat;
import dev.zio.client.utils.FloatAnimation;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.managers.CommandRegistry;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

public final class ChatInputOverlay implements ClientAccess {
   private final FloatAnimation commandHighlightAnimation = new FloatAnimation();
   private final HUDModule hudModule;

   private static boolean isCommandInput() {
      if (ClientAccess.zioMinecraft.screen instanceof ChatScreen chatScreen) {
         EditBox input = chatScreen.input;
         String inputText = input.getValue();

         String prefix = CommandRegistry.getPrefix();
         return inputText.startsWith(prefix);
      } else {
         return false;
      }
   }

   ChatInputOverlay(HUDModule hudModule) {
      this.hudModule = hudModule;
   }

   @EventHandler
   private void onHudRender(HudRenderEvent.Unconditional event) {
      // 26.1.2: el texto que se escribe en el chat lo dibuja
      // EditBox.extractWidgetRenderState via GuiGraphicsExtractor.text(...)
      // directamente al estado del pase gui (verificado en el oraculo), sin
      // pasar por el ActiveTextCollector que zio reroutea. Cualquier fondo
      // translucido que este overlay dibuje corre en el dren (despues del
      // pase gui) y queda ENCIMA de esas letras tintandolas gris; ademas
      // ChatScreen ya pinta su propio rectangulo de input en extract.
      // Por eso el fondo custom ya no se dibuja en ninguna configuracion;
      // el outline de comandos queda porque es un borde de 1px fuera del
      // area de texto. En 1.21.11 el input pasaba por GuiGraphics.drawString
      // y el orden fondo->texto era correcto; recuperar el input custom
      FloatAnimation animation = this.commandHighlightAnimation;

      boolean commandInput = isCommandInput();
      animation.setTarget(commandInput, 250L);
      animation = this.commandHighlightAnimation;

      float animationValue = animation.getValue();

      float highlightAlpha = animationValue * 1.5F;
      InterpolatedFloat heightAnimation = this.hudModule.chatInputHeight;
      float chatInputHeight = heightAnimation.getValue();
      Minecraft widthMinecraft = ClientAccess.zioMinecraft;
      Window widthWindow = widthMinecraft.getWindow();
      float screenWidth = widthWindow.getGuiScaledWidth();
      Minecraft heightMinecraft = ClientAccess.zioMinecraft;
      Window heightWindow = heightMinecraft.getWindow();
      float screenHeight = heightWindow.getGuiScaledHeight();

      if (highlightAlpha > 0.0F) {
         Setting<Color> outlineColorSetting = UIModule.INSTANCE.colorSetting;
         Color outlineColor = ColorUtils.multiplyAlpha(
            outlineColorSetting.getValue(), highlightAlpha
         );
         PoseStack outlinePose = event.getPoseStack();

         float outlineTop = screenHeight - 3.0F - chatInputHeight;

         float outlineRight = screenWidth - 2.0F;

         GuiDrawUtils.drawRectOutline(
            outlinePose, 1.0F, outlineTop, outlineRight, screenHeight - 2.0F, outlineColor
         );
      }
   }

}
