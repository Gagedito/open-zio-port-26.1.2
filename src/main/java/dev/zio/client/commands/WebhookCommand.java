/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.zio.client.ClientContext;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.managers.WebhookManager;
import net.minecraft.network.chat.Component;

public final class WebhookCommand extends Command {
   public WebhookCommand() {
      super("webhook");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      builder.then(literal("get").executes(context -> {
         reply(ClientContext.webhookManager.getDestinationDescription(), -1035361);
         return 1;
      }));
      builder.then(literal("set").then(argument("url", StringArgumentType.greedyString()).executes(context -> {
         String url = ((String)context.getArgument("url", String.class)).trim();
         if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
         try {
            WebhookManager manager = ClientContext.webhookManager;
            manager.setUrl(url);
            reply("Notification destination updated: " + manager.getDestinationDescription(), -1035362);
         } catch (IllegalArgumentException invalid) {
            reply("Invalid webhook URL. Telegram URLs require /bot<token>/sendMessage?chat_id=<id>.", -1035362);
         }
         return 1;
      })));
      builder.then(literal("telegram").then(argument("botToken", FancyStringArgument.fancyString())
         .then(argument("chatId", FancyStringArgument.fancyString()).executes(context -> {
            try {
               ClientContext.webhookManager.setTelegram((String)context.getArgument("botToken", String.class),
                  (String)context.getArgument("chatId", String.class));
               reply("Telegram notifications configured. Start the bot or add it to the destination chat first.", -1035362);
            } catch (IllegalArgumentException invalid) {
               reply("Invalid Telegram credentials. Use webhook telegram <bot-token> <chat-id>.", -1035362);
            }
            return 1;
         }))));
      builder.then(literal("chatid").then(argument("id", FancyStringArgument.fancyString()).executes(context -> {
         WebhookManager manager = ClientContext.webhookManager;
         if (!manager.isTelegram()) {
            reply("Chat ID is not supported for current webhook.", -1);
            return 1;
         }
         String chatId = (String)context.getArgument("id", String.class);
         try {
            manager.setTelegramChatId(chatId);
            ChatUtils.printChat(Component.literal("Your webhook Chat ID has been updated to ").append(this.formatText(chatId)), ChatUtils.DEFAULT_SIGNATURE);
         } catch (IllegalArgumentException invalid) {
            reply("Invalid Telegram chat ID.", -1);
         }
         return 1;
      })).executes(context -> {
         WebhookManager manager = ClientContext.webhookManager;
         if (manager.isTelegram()) {
            ChatUtils.printChat(Component.literal("Your webhook Chat ID is ").append(this.formatText(manager.getTelegramChatId())), ChatUtils.DEFAULT_SIGNATURE);
         } else {
            reply("Chat ID is not supported for current webhook.", -1);
         }
         return 1;
      }));
      builder.then(literal("clear").executes(context -> {
         ClientContext.webhookManager.setUrl("");
         reply("Your notification destination has been cleared", -1035363);
         return 1;
      }));
   }

   private static void reply(String message, int signature) {
      ChatUtils.printChat(Component.literal(message), signature);
   }
}
