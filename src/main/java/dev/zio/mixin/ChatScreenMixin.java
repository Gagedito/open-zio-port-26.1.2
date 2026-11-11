/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import dev.zio.client.utils.math.MathConstants;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import dev.zio.client.modules.misc.BetterChatModule;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.access.ChatLineAccess;
import dev.zio.client.gui.screens.PresetImportScreen;
import dev.zio.client.render.FontActiveTextCollector;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.managers.CommandRegistry;
import dev.zio.client.modules.client.FontsModule;
import dev.zio.client.modules.client.HUDModule;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
   private static final FontsModule fontsModule = ClientContext.getModule(FontsModule.class);
   private static final BetterChatModule betterChatModule = ClientContext.getModule(BetterChatModule.class);
   private static final HUDModule hudModule = ClientContext.getModule(HUDModule.class);
   @Shadow
   protected EditBox input;
   @Shadow
   CommandSuggestions commandSuggestions;

   @Shadow
   protected abstract boolean insertionClickMode();

   protected ChatScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "init()V", at = @At("HEAD"))
   private void resetChatLineAnimations(CallbackInfo callback) {
      if (betterChatModule.alwaysSetting.getValue()
         && betterChatModule.isAnimationEnabled()) {
         for (Line line : Minecraft.getInstance().gui.getChat().trimmedMessages) {
            ((ChatLineAccess)(Object)line)
               .getProgress()
               .setValue(
                  false
               );
            ((ChatLineAccess)(Object)line).zio$setAddTime(System.currentTimeMillis());
         }
      }
   }

   // 26.1.2: el handler viejo suprimia este fill sin condicion y el fondo de zio se dibujaba desde
   // ChatInputOverlay (dren del HUD); como el dren corre DESPUES del pase gui, ese fondo quedaba
   // ENCIMA del texto del EditBox y toda la barra de input desaparecia. Se reproduce aqui el mismo
   // fondo animado del baseline (ChatInputOverlay:71-85 decompilado: fillRect(2, h-2-hchat, w-2,
   // h-2, bg*alpha) con alpha = chatInputHeight/12) dibujado IN-ORDER, antes de que la llamada a
   // Screen.extractRenderState extraiga el texto del EditBox.
   @WrapOperation(
      method = "extractRenderState",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"
      )
   )
   private void chatInputBackgroundFill(
      GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color, Operation<Void> operation
   ) {
      float chatInputHeight = hudModule.getChatInputHeight();
      float screenHeight = ClientAccess.zioMinecraft.getWindow().getGuiScaledHeight();
      float screenWidth = ClientAccess.zioMinecraft.getWindow().getGuiScaledWidth();
      int backgroundArgb = ClientAccess.zioMinecraft.options.getBackgroundColor(-2147483648);
      Color backgroundColor = ColorUtils.multiplyAlpha(
         new Color(backgroundArgb, true), chatInputHeight / 12.0F
      );
      graphics.fill(
         2,
         (int)(screenHeight - 2.0F - chatInputHeight),
         (int)(screenWidth - 2.0F),
         (int)screenHeight - 2,
         ColorUtils.toArgb(backgroundColor, backgroundColor.getAlpha() / 255.0F)
      );
   }

   @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
   private boolean allowChatSuperclassRender(
      Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick
   ) {
      return hudModule.getChatInputOffset()
            / 12.0F
         > MathConstants.ONE_QUARTER;
   }

   @Inject(
      method = "extractRenderState",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", shift = Shift.BEFORE)
   )
   private void updateInlineCommandSuggestion(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback
   ) {
      String commandInput = this.input.getValue();
      if (!commandInput.startsWith(CommandRegistry.getPrefix())) {
         return;
      }
      CommandSuggestionsSuggestionsListAccessor suggestionsWindow = (CommandSuggestionsSuggestionsListAccessor)((CommandSuggestionsAccessor)this.commandSuggestions).getWindow();
      CompletableFuture<Suggestions> pendingSuggestions = ((CommandSuggestionsAccessor)this.commandSuggestions).getSuggestion();
      if (pendingSuggestions == null || !pendingSuggestions.isDone()
         || !this.input.getValue().startsWith(CommandRegistry.getPrefix())) {
         return;
      }
      String suggestionText = "";
      Suggestions suggestions = pendingSuggestions.join();
      ParseResults<?> parseResults = ((CommandSuggestionsAccessor)this.commandSuggestions).getParse();
      List<String> placeholders = Collections.emptyList();
      if (parseResults != null && this.input.getCursorPosition() == this.input.getValue().length()) {
         List<? extends ParsedCommandNode<?>> parsedNodes = parseResults.getContext().getNodes();
         if (!parseResults.getContext().getNodes().isEmpty()) {
            placeholders = this.buildCommandNodePlaceholders(
               parsedNodes.getLast().getNode().getChildren()
            );
         }
      }

      if (suggestionsWindow != null && !suggestions.getList().isEmpty()) {
         String completedInput = suggestions.getList().get(suggestionsWindow.getSelection()).apply(suggestionsWindow.getTypedText());
         suggestionText = suggestionText + (completedInput.startsWith(this.input.getValue()) ? completedInput.substring(this.input.getValue().length()) : "");
      }

      if (this.input.getCursorPosition() > 0
            && this.input.getValue().charAt(this.input.getCursorPosition() - 1)
               != 32
         || !suggestionText.isEmpty()) {
         suggestionText = suggestionText + " ";
      }

      if (parseResults != null
         && parseResults.getReader().canRead()
         && (
            parseResults.getReader().peek()
                  != 32
               || suggestionsWindow != null
         )
         && !placeholders.isEmpty()) {
         placeholders.removeFirst();
      }

      suggestionText = suggestionText + String.join(" ", placeholders);
      this.input.setSuggestion(suggestionText);
   }

   @ModifyExpressionValue(
      method = "mouseClicked",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector$ClickableStyleFinder;result()Lnet/minecraft/network/chat/Style;")
   )
   private Style findCustomFontClickedStyle(
      Style originalStyle, @Local(argsOnly = true) MouseButtonEvent mouseEvent
   ) {
      if (fontsModule.isToggled()
         && fontsModule.chatSetting.getValue()) {
         int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
         FontActiveTextCollector.ClickableStyleFinder styleFinder = new FontActiveTextCollector.ClickableStyleFinder(mouseEvent.x(), mouseEvent.y(), this.insertionClickMode());
         this.minecraft.gui.getChat().captureClickableText(styleFinder, screenHeight, this.minecraft.gui.getGuiTicks(), ChatComponent.DisplayMode.FOREGROUND);
         return styleFinder.result();
      } else {
         return originalStyle;
      }
   }

   @Override
   public void onFilesDrop(List paths) {
      PresetImportScreen.openForDroppedFiles(this, paths);
   }

   @Unique
   private List<String> buildCommandNodePlaceholders(Collection<? extends CommandNode<?>> nodeChildren) {
      List<String> placeholders = new ArrayList<>();

      while (true) {
         CommandNode<?> lastNode = null;
         int childCount = 0;
         StringBuilder groupText = new StringBuilder("<");

         for (CommandNode<?> childNode : nodeChildren) {
            childCount++;
            groupText.append(childNode.getName());
            if (childCount != nodeChildren.size()) {
               groupText.append(", ");
            } else {
               lastNode = childNode;
            }
         }

         groupText.append(">");
         if (!nodeChildren.isEmpty()) {
            placeholders.add(groupText.toString());
         }

         if (nodeChildren.size() != 1 || lastNode == null) {
            return placeholders;
         }

         nodeChildren = lastNode.getChildren();
      }
   }
}
