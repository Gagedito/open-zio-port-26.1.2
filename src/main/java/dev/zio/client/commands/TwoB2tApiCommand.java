/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.ZioClient;

import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class TwoB2tApiCommand extends Command {
   private static final Pattern USERNAME_PATTERN;

   public static String formatDuration(int totalSeconds) {
      StringBuilder duration = new StringBuilder();

      int days = totalSeconds / 86400;
      int hours = totalSeconds / 3600 % 24;
      int minutes = totalSeconds / 60 % 60;
      int seconds = totalSeconds % 60;
      if (days > 0) {
         duration.append(days).append(" day").append(pluralSuffix(days)).append(", ");
      }

      if (days > 0 || hours > 0) {
         duration.append(hours).append(" hour").append(pluralSuffix(hours)).append(", ");
      }

      if (days > 0 || hours > 0 || minutes > 0) {
         duration.append(minutes).append(" minute").append(pluralSuffix(minutes)).append(" and ");
      }

      duration.append(seconds).append(" second").append(pluralSuffix(seconds));
      return duration.toString();
   }

   public static String pluralSuffix(int count) {
      return count == 1 ? "" : "s";
   }

   public TwoB2tApiCommand(String commandName) {
      super(commandName);
   }

   static {

      String usernamePattern = "[a-zA-Z0-9_]{1,16}";
      USERNAME_PATTERN = Pattern.compile(usernamePattern);
   }

   @Override
   public void exec(LiteralArgumentBuilder rootArgument) {
   }

   public CompletableFuture<JsonObject> fetchData(String endpoint, String playerName) {
      if (playerName != null && !playerName.isEmpty()
         && (playerName.length() < 3 || !USERNAME_PATTERN.matcher(playerName).matches())) {
         return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid username provided."));
      }
      String path = endpoint + (playerName == null || playerName.isEmpty() ? "" : "?playerName=" + playerName);
      return CompletableFuture.supplyAsync(() -> {
         RuntimeException lastFailure = null;
         for (int attempt = 0; attempt < 2; attempt++) {
            HttpResponse<String> response;
            try {
               var request = dev.zio.client.utils.network.HttpRequests.get("https://api.2b2t.vc/" + path)
                  .header("User-Agent", ZioClient.USER_AGENT).header("Accept", "application/json")
                  .timeout(Duration.ofSeconds(10)).build();
               response = dev.zio.client.utils.network.HttpRequests.send(request);
            } catch (RuntimeException failure) {
               lastFailure = failure;
               if (Thread.currentThread().isInterrupted()) throw failure;
               if (attempt == 0) {
                  try { Thread.sleep(1000L); }
                  catch (InterruptedException interrupted) {
                     Thread.currentThread().interrupt();
                     throw new java.util.concurrent.CompletionException(interrupted);
                  }
               }
               continue;
            }
            return switch (response.statusCode()) {
               case 200 -> dev.zio.client.ClientAccess.gson.fromJson(response.body(), JsonObject.class);
               case 204 -> throw new IllegalStateException("No data" + (playerName == null || playerName.isEmpty() ? "" : " for player") + ".");
               case 429 -> throw new IllegalStateException("You are being rate limited, please try again later.");
               default -> throw new IllegalStateException("Invalid server response code: " + response.statusCode() + ".");
            };
         }
         throw new java.util.concurrent.CompletionException("Failed to fetch data.", lastFailure);
      });
   }
}
