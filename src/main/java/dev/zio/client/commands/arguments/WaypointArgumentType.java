/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands.arguments;

import dev.zio.client.managers.WaypointRegistry;
import dev.zio.client.utils.world.Waypoint;

import dev.zio.client.ClientContext;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class WaypointArgumentType implements ArgumentType<Waypoint> {
   private static final String unknownWaypointFormat;

   static {
      unknownWaypointFormat = "Waypoint %s doesn't exists";
   }

   @Override
   public Waypoint parse(StringReader reader) throws CommandSyntaxException {
      String requestedName = reader.readString();
      WaypointRegistry registryForParsing = ClientContext.waypointRegistry;
      Iterator<Waypoint> waypointIterator = ((List<Waypoint>)registryForParsing.getRegistry()).iterator();
      while (waypointIterator.hasNext()) {
         Waypoint waypoint = waypointIterator.next();
         if (waypoint != null && waypoint.getName() != null) {
            String waypointName = waypoint.getName();
            if (waypointName.equalsIgnoreCase(requestedName)) {
               return waypoint;
            }
         }
      }

      DynamicCommandExceptionType unknownWaypointError = new DynamicCommandExceptionType(argument -> {
         String errorFormat = unknownWaypointFormat;
         Object[] formatArguments = new Object[]{argument};
         String message = String.format(errorFormat, formatArguments);
         return Component.literal(message);
      });
      throw unknownWaypointError.create(requestedName);
   }

   @Override
   public <S> CompletableFuture<Suggestions> listSuggestions(
      CommandContext<S> context, SuggestionsBuilder builder
   ) {
      ArrayList<Waypoint> invalidWaypoints = new ArrayList<>();
      WaypointRegistry registryForSuggestionScan = ClientContext.waypointRegistry;
      Iterator<Waypoint> waypointIterator = ((List<Waypoint>)registryForSuggestionScan.getRegistry()).iterator();
      while (waypointIterator.hasNext()) {
         Waypoint waypoint = waypointIterator.next();
         if (waypoint != null && (waypoint.getServer() == null || waypoint.getDimension() == null || waypoint.getName() == null)) {
            invalidWaypoints.add(waypoint);
         }
      }

      waypointIterator = invalidWaypoints.iterator();
      while (waypointIterator.hasNext()) {
         Waypoint invalidWaypoint = waypointIterator.next();
         ClientContext.waypointRegistry.unregisterWaypoint(invalidWaypoint);
      }

      WaypointRegistry suggestionRegistry = ClientContext.waypointRegistry;
      Stream<String> waypointNames = ((List<Waypoint>)suggestionRegistry.getRegistry()).stream().map(Waypoint::getName);
      return SharedSuggestionProvider.suggest(waypointNames, builder);
   }
}
