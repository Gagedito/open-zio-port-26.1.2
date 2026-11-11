/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.modules.misc.BetterChatModule;
import dev.zio.client.access.ChatLineAccess;
import dev.zio.client.utils.ClampedInterpolatedFloat;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Line.class)
public class GuiMessageLineMixin implements ChatLineAccess {
   @Unique
   private ClampedInterpolatedFloat progress = null;
   @Unique
   private MessageSignature signature;
   @Mutable
   @Final
   @Shadow
   private FormattedCharSequence content;
   @Unique
   private long addTimeMillis;

   /**
    * MC 26.1.2: GuiMessage$Line es record(parent, content, endOfEntry);
    * addedAtTick y GuiMessageTag viven ahora en el GuiMessage padre
    * (parent.addedTime()/parent.tag()). El hook solo anima la linea, asi que
    * basta la firma nueva del ctor.
    */
   @Inject(method = "<init>", at = @At("RETURN"))
   private void initializeChatLineAnimation(
      GuiMessage parent, FormattedCharSequence text, boolean endOfEntry, CallbackInfo callback
   ) {
      this.getProgress()
         .setValue(
            false
         );
      this.addTimeMillis = System.currentTimeMillis();
   }

   @Override
   public MessageSignature getSignature() {
      return this.signature;
   }

   @Override
   public void setSignature(MessageSignature newSignature) {
      this.signature = newSignature;
   }

   @Override
   public FormattedCharSequence getContent() {
      return this.content;
   }

   @Override
   public void setContent(FormattedCharSequence newContent) {
      this.content = newContent;
   }

   @Override
   public ClampedInterpolatedFloat getProgress() {
      if (this.progress == null) {
         this.progress = new ClampedInterpolatedFloat(
            () -> BetterChatModule.getInstance()
                  .speedSetting
                  .getValue()
               * 2.0F,
            true
         );
      }

      return this.progress;
   }

   @Override
   public long zio$getAddTime() {
      return this.addTimeMillis;
   }

   @Override
   public void zio$setAddTime(long addedAtMillis) {
      this.addTimeMillis = addedAtMillis;
   }
}
