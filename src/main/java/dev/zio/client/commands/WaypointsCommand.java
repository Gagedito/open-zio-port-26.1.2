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
import dev.zio.client.utils.world.Waypoint;
import dev.zio.client.managers.WaypointRegistry;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.modules.render.WaypointsModule;
import dev.zio.client.settings.options.DimensionType;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

public final class WaypointsCommand extends Command {

   static String getCurrentServerKey() {
      return dev.zio.client.utils.ServerAddressUtils.getCurrentServerAddress();
   }

   public WaypointsCommand() {
      super("waypoints");
      String[] aliases = new String[1];

      aliases[0] = "wp";
      this.setAliases(aliases);
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {

      LiteralArgumentBuilder operationBuilder = Command.literal("add");

      RequiredArgumentBuilder operationArgument = Command.argument("name", FancyStringArgument.fancyString());

      RequiredArgumentBuilder positionBranch = (RequiredArgumentBuilder)(Command.argument("pos", new PositionArgument())).executes((com.mojang.brigadier.Command<Object>)context -> {

            String name = (String)context.getArgument("name", String.class);

            Vec3 position = (Vec3)context.getArgument("pos", Vec3.class);

            DimensionType currentDimension = WorldUtils.getDimension();
            String dimensionDisplayName = currentDimension.getDisplayName();
            String dimensionKey = dimensionDisplayName.toLowerCase();

            Waypoint waypoint = new Waypoint(
               name, position, dimensionKey, getCurrentServerKey()
            );
            WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
            waypointRegistry.registerWaypoint(waypoint);

            String messagePrefix = "Waypoint ";
            MutableComponent message = Component.literal(messagePrefix);
            MutableComponent messageWithWaypoint = message.append(waypoint.getDisplayComponent());

            String messageSuffix = " has been created";
            MutableComponent messageForSuffix = messageWithWaypoint;
            ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1);
            return 1;
         });

      ArgumentBuilder dimensionBranch = (Command.argument("dimension", new EnumArgumentType(DimensionType.class, "dimension"))).executes((com.mojang.brigadier.Command<Object>)context -> {

            String name = (String)context.getArgument("name", String.class);

            Vec3 position = (Vec3)context.getArgument("pos", Vec3.class);

            DimensionType dimension = (DimensionType)context.getArgument("dimension", DimensionType.class);
            String dimensionDisplayName = dimension.getDisplayName();
            String dimensionKey = dimensionDisplayName.toLowerCase();

            Waypoint waypoint = new Waypoint(
               name, position, dimensionKey, getCurrentServerKey()
            );
            WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
            waypointRegistry.registerWaypoint(waypoint);

            String messagePrefix = "Waypoint ";
            MutableComponent message = Component.literal(messagePrefix);
            MutableComponent messageWithWaypoint = message.append(waypoint.getDisplayComponent());

            String messageSuffix = " has been created";
            MutableComponent messageForSuffix = messageWithWaypoint;
            ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1);
            return 1;
         });
      ArgumentBuilder positionWithDimension = positionBranch.then(dimensionBranch);
      ArgumentBuilder addBranch = ((RequiredArgumentBuilder)operationArgument.then(positionWithDimension)).executes((com.mojang.brigadier.Command<Object>)context -> {

            String name = (String)context.getArgument("name", String.class);

            DimensionType currentDimension = WorldUtils.getDimension();
            String dimensionDisplayName = currentDimension.getDisplayName();
            String dimensionKey = dimensionDisplayName.toLowerCase();
            LocalPlayer playerForPosition = ClientAccess.zioMinecraft.player;
            Vec3 playerPosition = playerForPosition.position();

            Waypoint waypoint = new Waypoint(
               name, playerPosition, dimensionKey, getCurrentServerKey()
            );
            WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
            waypointRegistry.registerWaypoint(waypoint);

            String messagePrefix = "Waypoint ";
            MutableComponent message = Component.literal(messagePrefix);
            MutableComponent messageWithWaypoint = message.append(waypoint.getDisplayComponent());

            String messageSuffix = " has been created";
            MutableComponent messageForSuffix = messageWithWaypoint;
            ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1);
            return 1;
         });
      ArgumentBuilder addTree = operationBuilder.then(addBranch);
      LiteralArgumentBuilder rootWithCommands = (LiteralArgumentBuilder)root.then(addTree);

      String[] removeAliasNames = new String[3];

      removeAliasNames[0] = "remove";

      removeAliasNames[1] = "delete";

      removeAliasNames[(2)] = "del";
      RequiredArgumentBuilder removeAliasBuilder = Command.aliasedLiteral(removeAliasNames);

      operationArgument = (Command.argument("server", StringArgumentType.string())).suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>)WaypointsCommand::suggestServers);

      ArgumentBuilder removeNameBranch = ((Command.argument("name", StringArgumentType.string())).suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>)WaypointsCommand::suggestWaypointNames)).executes((com.mojang.brigadier.Command<Object>)context -> {

            String name = (String)context.getArgument("name", String.class);

            String server = (String)context.getArgument("server", String.class);
            WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
            ((List)waypointRegistry.getRegistry()).removeIf((Predicate<Waypoint>)candidate -> {

               String candidateServer = candidate.getServer();
               if (server.equalsIgnoreCase(candidateServer)) {
                  String candidateName = candidate.getName();
                  if (name.equalsIgnoreCase(candidateName)) {
                     return true;
                  }
               }

               return false;
            });

            String messagePrefix = "Waypoint ";
            MutableComponent message = Component.literal(messagePrefix);
            MutableComponent messageWithName = message.append(name);

            String messageSuffix = " has been removed";
            MutableComponent messageForSuffix = messageWithName;
            ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1);
            return 1;
         });
      ArgumentBuilder removeServerTree = operationArgument.then(removeNameBranch);
      ArgumentBuilder removeTree = removeAliasBuilder.then(removeServerTree);
      rootWithCommands = (LiteralArgumentBuilder)rootWithCommands.then(removeTree);

      operationBuilder = Command.literal("rename");

      operationArgument = (Command.argument("server", StringArgumentType.string())).suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>)WaypointsCommand::suggestServers);

      RequiredArgumentBuilder renameNameBuilder = (Command.argument("name", StringArgumentType.string())).suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>)WaypointsCommand::suggestWaypointNames);

      ArgumentBuilder renameTargetBranch = (Command.argument("target", StringArgumentType.string())).executes((com.mojang.brigadier.Command<Object>)context -> {

            String name = (String)context.getArgument("name", String.class);

            String server = (String)context.getArgument("server", String.class);

            String targetName = (String)context.getArgument("target", String.class);
            Predicate<Waypoint> matchesWaypoint = candidate -> {

               String candidateServer = candidate.getServer();
               if (server.equalsIgnoreCase(candidateServer)) {
                  String candidateName = candidate.getName();
                  if (name.equalsIgnoreCase(candidateName)) {
                     return true;
                  }
               }

               return false;
            };
            Waypoint waypoint = (Waypoint)ClientContext.waypointRegistry.findFirst(matchesWaypoint).orElse(null);
            if (waypoint == null) {

               String missingWaypointMessage = "Waypoint not found";
               ChatUtils.printChat(Component.nullToEmpty(missingWaypointMessage), -1);
               return 1;
            } else {
               WaypointRegistry registryForRemoval = ClientContext.waypointRegistry;
               ((List)registryForRemoval.getRegistry()).removeIf(matchesWaypoint);
               WaypointRegistry registryForReplacement = ClientContext.waypointRegistry;
               List replacementRegistry = (List)registryForReplacement.getRegistry();

               Vec3 waypointPosition = waypoint.getPosition();

               String waypointDimension = waypoint.getDimension();

               replacementRegistry.add(new Waypoint(
                     targetName,
                     waypointPosition,
                     waypointDimension,
                     waypoint.getServer()
                  ));

               String messagePrefix = "Waypoint ";
               MutableComponent renameMessage = Component.literal(messagePrefix);
               renameMessage = renameMessage.append(name);

               String messageSuffix = " has been renamed to";
               MutableComponent messageForSuffix = renameMessage;
               renameMessage = messageForSuffix.append(messageSuffix);
               renameMessage = renameMessage.append(targetName);
               ChatUtils.printChat(renameMessage, -1);
               return 1;
            }
         });
      ArgumentBuilder renameNameTree = renameNameBuilder.then(renameTargetBranch);
      ArgumentBuilder renameServerTree = operationArgument.then(renameNameTree);
      ArgumentBuilder renameTree = operationBuilder.then(renameServerTree);
      rootWithCommands = (LiteralArgumentBuilder)rootWithCommands.then(renameTree);

      ArgumentBuilder listBranch = (Command.literal("list")).executes((com.mojang.brigadier.Command<Object>)context -> {
            WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
            List<Component> waypointComponents = ((List<Waypoint>)waypointRegistry.getRegistry()).stream()
               .sorted(Comparator.comparing(Waypoint::isToggled).reversed())
               .map(Waypoint::getDisplayComponent)
               .toList();
            MutableComponent message = Component.empty();

            String messagePrefix = "Waypoints list: ";
            message.append(messagePrefix);

            message.append(ComponentUtils.formatList(waypointComponents, Component.literal(", ")));
            ChatUtils.printChat(message, -1);
            return 1;
         });
      rootWithCommands.then(listBranch);

      operationBuilder = Command.literal("toggle");

      ArgumentBuilder toggleNameBranch = ((Command.argument("name", StringArgumentType.string())).suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>)WaypointsCommand::suggestWaypointNames)).executes((com.mojang.brigadier.Command<Object>)context -> {

            String name = (String)context.getArgument("name", String.class);

            String server = getCurrentServerKey();
            Predicate<Waypoint> matchesWaypoint = candidate -> {

               String candidateServer = candidate.getServer();
               if (server.equalsIgnoreCase(candidateServer)) {
                  String candidateName = candidate.getName();
                  if (name.equalsIgnoreCase(candidateName)) {
                     return true;
                  }
               }

               return false;
            };
            Waypoint waypoint = (Waypoint)ClientContext.waypointRegistry.findFirst(matchesWaypoint).orElse(null);
            if (waypoint == null) {

               String missingWaypointMessage = "Waypoint not found";
               ChatUtils.printChat(Component.nullToEmpty(missingWaypointMessage), -1);
               return 1;
            }

            boolean newVisibility = !waypoint.isToggled();
            waypoint.setToggled(newVisibility);

            Object[] formatArguments = new Object[2];
            formatArguments[0] = name;
            String visibilityName;
            if (waypoint.isToggled()) {

               visibilityName = "visible";
            } else {

               visibilityName = "invisible";
            }

            formatArguments[1] = visibilityName;
            String toggleMessage = "Made the waypoint %s %s.".formatted(formatArguments);
            ChatUtils.printChat(Component.literal(toggleMessage), -1);
            return 1;
         });
      ArgumentBuilder toggleTree = operationBuilder.then(toggleNameBranch);
      root.then(toggleTree);
      if ((ClientContext.baritoneBridge).isAvailable()) {
         ElytraCommand elytraCommand = new ElytraCommand();
         this.addSubcommand(elytraCommand, root);
         WaypointWalkCommand walkCommand = new WaypointWalkCommand();
         this.addSubcommand(walkCommand, root);
      }
   }

   static Vec3 resolveWaypointPosition(CommandContext context) {

      String name = (String)context.getArgument("name", String.class);
      Optional waypointResult = ClientContext.waypointRegistry.findFirst((Predicate<Waypoint>)candidate -> {

            String server = getCurrentServerKey();

            return candidate.matches(name, server);
         });
      Waypoint waypoint = (Waypoint)waypointResult.orElse(null);
      if (waypoint == null) {
         return null;
      }

      return WaypointsModule.getPositionInCurrentDimension(waypoint);
   }

   static CompletableFuture suggestNamesForServer(
      CommandContext context, SuggestionsBuilder builder, String server
   ) {
      WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
      Stream<String> names = ((List<Waypoint>)waypointRegistry.getRegistry()).stream()
         .filter(candidate -> {
            String candidateServerSnapshot = candidate.getServer();
            return candidateServerSnapshot.equalsIgnoreCase(server);
         })
         .map(Waypoint::getName);
      return SharedSuggestionProvider.suggest(names, builder);
   }

   static String formatServerSuggestion(Waypoint waypoint) {

      String server = waypoint.getServer();
      String lowercaseServer = server.toLowerCase();
      if (lowercaseServer.contains(":")) {
         return "\"" + lowercaseServer + "\"";
      } else {
         return lowercaseServer;
      }
   }

   static CompletableFuture suggestServers(
      CommandContext context, SuggestionsBuilder builder
   ) {
      WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
      Stream<String> servers = ((List<Waypoint>)waypointRegistry.getRegistry()).stream()
         .map(WaypointsCommand::formatServerSuggestion)
         .distinct();
      return SharedSuggestionProvider.suggest(servers, builder);
   }

   static CompletableFuture suggestWaypointNames(
      CommandContext context, SuggestionsBuilder builder
   ) {
      String server;
      try {

         server = (String)context.getArgument("server", String.class);
      } catch (IllegalArgumentException missingArgument) {

         server = getCurrentServerKey();
      }

      return suggestNamesForServer(context, builder, server);
   }

}
