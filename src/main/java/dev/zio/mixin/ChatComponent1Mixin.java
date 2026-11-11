/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.zio.client.modules.misc.BetterChatModule;
import dev.zio.client.ClientContext;
import dev.zio.client.access.ChatLineAccess;
import dev.zio.client.access.ChatGraphicsXOffset;
import dev.zio.client.modules.render.NoRenderModule;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent.ChatGraphicsAccess;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net/minecraft/client/gui/components/ChatComponent$1")
public class ChatComponent1Mixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final BetterChatModule betterChatModule = ClientContext.getModule(BetterChatModule.class);

   @ModifyExpressionValue(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;tag()Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;"))
   private GuiMessageTag hideMessageIndicator(GuiMessageTag originalTag) {
      return noRenderModule.isToggled()
            && noRenderModule.messageIndicatorSetting.getValue()
         ? null
         : originalTag;
   }

   @WrapOperation(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
   private boolean suppressMessageIndicatorIcon(
      ChatGraphicsAccess graphics, int messageY, float opacity, FormattedCharSequence text, Operation original, @Local(argsOnly = true) Line line
   ) {
      ChatLineAccess lineAccess = (ChatLineAccess)(Object)line;
      Minecraft minecraft = Minecraft.getInstance();
      int offsetX = 0;
      offsetX -= noRenderModule.isToggled()
            && noRenderModule.messageIndicatorSetting.getValue()
         ? 3
         : 0;
      if (betterChatModule.isAnimationEnabled()) {
         lineAccess.getProgress()
            .setTarget(
               true
            );
         float progress = lineAccess.getProgress().updateAndGet();
         long addedAtMillis = lineAccess.zio$getAddTime();
         boolean animateBothAxes = betterChatModule.typeSetting.getValue()
            == BetterChatModule.TypeChoice.BOTH;
         if (betterChatModule.typeSetting.getValue()
               == BetterChatModule.TypeChoice.VERTICAL
            || animateBothAxes) {
            messageY = (int)(messageY + 9.0F * (1.0F - progress));
         }

         if (betterChatModule.typeSetting.getValue()
               == BetterChatModule.TypeChoice.HORIZONTAL
            || animateBothAxes) {
            offsetX = (int)(offsetX - minecraft.font.width(text) * (1.0F - progress));
         }

         if (betterChatModule.alphaSetting.getValue() != 0) {
            float fadeProgress = Mth.clamp(
               (float)(System.currentTimeMillis() - addedAtMillis)
                  / (betterChatModule.alphaSetting.getValue())
                     .intValue(),
               0.0F,
               1.0F
            );
            opacity *= fadeProgress;
            if (opacity < 0.05) {
               opacity = 0.0F;
            }
         }
      }

      if (graphics instanceof ChatGraphicsXOffset offsetGraphics) {
         offsetGraphics.zio$updateX(offsetX);
      }

      return (Boolean)original.call(graphics, messageY, opacity, text);
   }
}
