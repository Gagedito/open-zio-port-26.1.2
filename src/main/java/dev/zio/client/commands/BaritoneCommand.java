/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.integrations.baritone.BaritoneBridge;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.IBaritoneProvider;
import baritone.api.command.IBaritoneChatControl;
import baritone.api.event.events.ChatEvent;
import baritone.api.event.events.TabCompleteEvent;
import baritone.api.event.listener.IEventBus;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

public final class BaritoneCommand extends Command {
   private static final String COMPLETION_PREFIX = "#";

   public BaritoneCommand() {
      super("baritone");
      String[] aliases = new String[]{"b"};
      this.setAliases(aliases);
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {
      StringArgumentType argumentType = StringArgumentType.greedyString();
      RequiredArgumentBuilder commandArgument = Command.argument("command", argumentType);
      SuggestionProvider<Object> suggestionProvider = (context, builder) -> {
         if (!ClientContext.baritoneBridge.isAvailable()) {
            return builder.buildFuture();
         }
         IBaritoneProvider provider = BaritoneAPI.getProvider();
         IBaritone baritone = provider.getPrimaryBaritone();
         String completionInput = COMPLETION_PREFIX;

         try {
            String remainingForCompletion = builder.getRemaining();
            completionInput = COMPLETION_PREFIX + remainingForCompletion;
         } catch (Exception ignored) {
         }

         TabCompleteEvent completionEvent = new TabCompleteEvent(completionInput);
         Runnable completeAction = () -> {
            baritone.getGameEventHandler().onPreTabComplete(completionEvent);
         };

         runWithCompletionPrefix(completeAction);
         if (completionEvent.completions != null && completionEvent.completions.length != 0) {
            String input = context.getInput();
            int measuredInputLength = input.length();
            int builderStart = builder.getStart();
            String remaining = builder.getRemaining();
            int remainingEnd = builderStart + remaining.length();
            int completionEnd = Math.min(measuredInputLength, remainingEnd);
            String inputThroughCompletion = input.substring(0, completionEnd);
            int wordStart = inputThroughCompletion.lastIndexOf(" ") + 1;
            int wordEnd = inputThroughCompletion.length();
            StringRange range = StringRange.between(wordStart, wordEnd);
            String[] completions = completionEvent.completions;
            Stream<Suggestion> suggestionStream = Stream.of(completions).map(completion -> new Suggestion(range, stripCompletionPrefix(completion)));
            List<Suggestion> suggestionList = suggestionStream.collect(Collectors.toList());
            Suggestions suggestions = new Suggestions(range, suggestionList);
            return CompletableFuture.completedFuture(suggestions);
         } else {
            return builder.buildFuture();
         }
      };
      commandArgument = commandArgument.suggests(suggestionProvider);
      com.mojang.brigadier.Command<Object> commandAction = context -> {
         if (!ClientContext.baritoneBridge.isAvailable()) {
            ChatUtils.printChat(Component.literal("Baritone is not installed."), ChatUtils.DEFAULT_SIGNATURE);
            return 0;
         }
         IBaritoneProvider provider = BaritoneAPI.getProvider();
         IBaritone baritone = provider.getPrimaryBaritone();

         String command = (String)context.getArgument("command", String.class);

         if (!"goto".equalsIgnoreCase(command)) {
            IEventBus chatEventBus = baritone.getGameEventHandler();
            ChatEvent chatEvent = new ChatEvent(formatForwardedCommand(IBaritoneChatControl.FORCE_COMMAND_PREFIX, command));
            chatEventBus.onSendChatMessage(chatEvent);
            return 1;
         } else {
            if (ClientAccess.zioMinecraft.hitResult instanceof BlockHitResult blockHit) {
               if (blockHit.getType() != Type.MISS) {
                  BlockPos target = blockHit.getBlockPos();

                  if (!WorldUtils.isReplaceable(target)) {
                     BlockPos aboveTarget = target.above();

                     if (WorldUtils.isReplaceable(aboveTarget)) {
                        target = target.above();
                     }
                  }

                  BaritoneBridge controller = ClientContext.baritoneBridge;
                  controller.pathTo(target);
                  return 1;
               }
            }

            BaritoneBridge pathingController = ClientContext.baritoneBridge;
            GameRenderer renderer = ClientAccess.zioMinecraft.gameRenderer;
            Camera camera = renderer.getMainCamera();
            BlockPos cameraPosition = camera.blockPosition();
            pathingController.pathTo(cameraPosition);
            return 1;
         }
      };
      ArgumentBuilder commandWithAction = commandArgument.executes(commandAction);
      root.then(commandWithAction);
   }

   private void runWithCompletionPrefix(Runnable action) {
      String previousPrefix = BaritoneAPI.getSettings().prefix.value;
      BaritoneAPI.getSettings().prefix.value = COMPLETION_PREFIX;
      try {
         action.run();
      } finally {
         BaritoneAPI.getSettings().prefix.value = previousPrefix;
      }
   }

   private static String formatForwardedCommand(String forceCommandPrefix, String command) {

      return String.valueOf(forceCommandPrefix) + String.valueOf(command);
   }

   private static String stripCompletionPrefix(String text) {
      if (text.startsWith(COMPLETION_PREFIX)) {
         return text.substring(1);
      }
      return text;
   }

}
