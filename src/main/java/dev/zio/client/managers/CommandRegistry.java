/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.managers;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.NamedFeature;
import dev.zio.client.commands.BaritoneCommand;
import dev.zio.client.commands.ModuleCommand;
import dev.zio.client.commands.Command;
import dev.zio.client.commands.EnemyFriendCommand;
import dev.zio.client.events.CommandExecuteEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.KeyboardHandlerCharTypedEvent;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.client.SettingsModule;
import dev.zio.client.settings.JsonConfigurable;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.SocialRole;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;

public final class CommandRegistry extends Registry<Command> implements ClientAccess, JsonConfigurable {

   private static final SettingsModule settingsModule = ClientContext.getModule(SettingsModule.class);
   public static final SharedSuggestionProvider suggestionSource = new ClientSuggestionProvider(
      null, ClientAccess.zioMinecraft, ignored -> true
   );
   public static final CommandDispatcher commandDispatcher = new CommandDispatcher();
   private static String commandPrefix = ";";

   public static char getPrefixCharacter() {
      return commandPrefix.charAt(0);
   }

   private void registerRelationshipCommands() {
      this.registerCommand(new EnemyFriendCommand(SocialRole.FRIEND));
      this.registerCommand(new EnemyFriendCommand(SocialRole.ENEMY));
   }

   private void registerPathfindingCommand() {
      this.registerCommand(new BaritoneCommand());
   }

   public CommandRegistry() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
      this.registerCommand(new dev.zio.client.commands.ActionCommand());
      this.registerCommand(new dev.zio.client.commands.AliasCommand());
      this.registerCommand(new dev.zio.client.commands.AlloffCommand());
      this.registerCommand(new dev.zio.client.commands.AngleCommand());
      this.registerCommand(new dev.zio.client.commands.AutopotCommand());
      this.registerCommand(new dev.zio.client.commands.AutopresetCommand());
      this.registerCommand(new dev.zio.client.commands.BindCommand());
      this.registerCommand(new dev.zio.client.commands.ChatfilterCommand());
      this.registerCommand(new dev.zio.client.commands.CheststealerCommand());
      this.registerCommand(new dev.zio.client.commands.ClipCommand());
      this.registerCommand(new dev.zio.client.commands.ColorCommand());
      this.registerCommand(new dev.zio.client.commands.CoordsCommand());
      this.registerCommand(new dev.zio.client.commands.DefaultsCommand());
      this.registerCommand(new dev.zio.client.commands.DisconnectCommand());
      this.registerCommand(new dev.zio.client.commands.DrawnCommand());
      this.registerCommand(new dev.zio.client.commands.FakeplayerCommand());
      this.registerCommand(new dev.zio.client.commands.FireworksCommand());
      this.registerCommand(new dev.zio.client.commands.FolderCommand());
      this.registerCommand(new dev.zio.client.commands.GlintCommand());
      this.registerCommand(new dev.zio.client.commands.GlobalCommand());
      this.registerCommand(new dev.zio.client.commands.HelpCommand());
      this.registerCommand(new dev.zio.client.commands.HighlightCommand());
      this.registerCommand(new dev.zio.client.commands.KitCommand());
      this.registerCommand(new dev.zio.client.commands.MacroCommand());
      this.registerCommand(new dev.zio.client.commands.NamehistoryCommand());
      this.registerCommand(new dev.zio.client.commands.NbtCommand());
      this.registerCommand(new dev.zio.client.commands.PanicCommand());
      this.registerCommand(new dev.zio.client.commands.PitchCommand());
      this.registerCommand(new dev.zio.client.commands.PlaytimeCommand());
      this.registerCommand(new dev.zio.client.commands.PluginsCommand());
      this.registerCommand(new dev.zio.client.commands.PrefixCommand());
      this.registerCommand(new dev.zio.client.commands.PresetCommand());
      this.registerCommand(new dev.zio.client.commands.PrintCommand());
      this.registerCommand(new dev.zio.client.commands.QueueCommand());
      this.registerCommand(new dev.zio.client.commands.ReloadCommand());
      this.registerCommand(new dev.zio.client.commands.SayCommand());
      this.registerCommand(new dev.zio.client.commands.SeenCommand());
      this.registerCommand(new dev.zio.client.commands.SessionCommand());
      this.registerCommand(new dev.zio.client.commands.ToggleCommand());
      this.registerCommand(new dev.zio.client.commands.TradeCommand());
      this.registerCommand(new dev.zio.client.commands.WaitCommand());
      this.registerCommand(new dev.zio.client.commands.WaypointsCommand());
      this.registerCommand(new dev.zio.client.commands.WebhookCommand());
      this.registerCommand(new dev.zio.client.commands.YawCommand());

      this.registerPathfindingCommand();
      this.registerRelationshipCommands();
      this.registerModuleAliasCommands();
      ((List<Command>)this.getRegistry()).sort(Comparator.comparing((Function<Command, String>)NamedFeature::getName));
   }

   @Override
   public boolean register(Object entry) {
      Command command = (Command)entry;
      return this.registerCommand(command);
   }

   public static void executeCommand(String input) {
      CommandExecuteEvent event = new CommandExecuteEvent(input);
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.post(event);
      if (event.isCancelled()) {
         return;
      }
      try {
         CommandDispatcher executionDispatcher = commandDispatcher;
         SharedSuggestionProvider source = suggestionSource;
         ParseResults parsed = commandDispatcher.parse(input, source);
         executionDispatcher.execute(parsed);
      } catch (CommandSyntaxException ignoredFailure) {
      }
   }

   @Override
   public void fromJson(JsonElement config) {
      JsonObject objectForPresence = config.getAsJsonObject();
      if (objectForPresence.has("prefix")) {
         JsonObject objectForValue = config.getAsJsonObject();
         JsonElement prefixElement = objectForValue.get("prefix");
         String prefix = prefixElement.getAsString();

         setPrefix(prefix);
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject config = new JsonObject();
      String prefix = commandPrefix;
      config.addProperty("prefix", prefix);
      return config;
   }

   @Override
   public String getConfigName() {
      return "commands.json";
   }

   @Override
   public boolean registerEntry(NamedEntry entry) {
      Command command = (Command)entry;
      return this.registerCommand(command);
   }

   @EventHandler
   public void onKeyboardHandlerCharTypedEvent(KeyboardHandlerCharTypedEvent inputEvent) {
      if (KeyUtils.isShiftDown()) {
         return;
      }
      String prefixForLength = getPrefix();
      if (prefixForLength.length() != 1) {
         return;
      }
      String prefixForCharacter = getPrefix();
      char prefixCharacter = prefixForCharacter.charAt(0);
      if (prefixCharacter != inputEvent.getCharacter()) {
         return;
      }
      Minecraft minecraft = ClientAccess.zioMinecraft;
      ChatScreen chatScreen = new ChatScreen("", false);
      minecraft.setScreen(chatScreen);
   }

   private Optional<LiteralArgumentBuilder> createMatchingModuleCommand(Command command) {
      return ClientContext.moduleRegistry.findFirst((Predicate<Module>)module -> {
         String moduleName = module.getName();
         String commandName = command.getName();
         return moduleName.equalsIgnoreCase(commandName);
      }).map((Function<Module, LiteralArgumentBuilder>)module -> {
         LiteralArgumentBuilder moduleBuilder = LiteralArgumentBuilder.literal("module");
         ModuleCommand moduleCommand = new ModuleCommand(module);
         moduleCommand.exec(moduleBuilder);
         return moduleBuilder;
      });
   }

   public static void setPrefix(String prefix) {
      if (prefix.isBlank()) {
         return;
      }
      String updatedPrefix = prefix.substring(0, 1);
      commandPrefix = updatedPrefix;
      Setting<String> prefixSetting = settingsModule.prefixSetting;
      prefixSetting.setValue(updatedPrefix);
   }

   public boolean registerCommand(Command command) {
      String commandName = command.getName();
      LiteralArgumentBuilder builder = LiteralArgumentBuilder.literal(commandName);
      command.exec(builder);
      Optional<LiteralArgumentBuilder> moduleCommand = this.createMatchingModuleCommand(command);
      moduleCommand.ifPresent(builder::then);
      LiteralCommandNode canonicalNode = commandDispatcher.register(builder);

      for (String alias : command.getAliases()) {
         CommandAliasBuilder aliasBuilder = new CommandAliasBuilder(alias);
         Iterator children = canonicalNode.getChildren().iterator();
         while (children.hasNext()) {
            CommandNode child = (CommandNode)children.next();
            aliasBuilder.then(child);
         }
         aliasBuilder.executes(canonicalNode.getCommand());
         commandDispatcher.register(aliasBuilder);
      }
      return this.entries.add(command);
   }

   public static String getPrefix() {
      return commandPrefix;
   }

   private void registerModuleAliasCommands() {
      Iterator<Module> moduleIterator = ClientContext.moduleRegistry.getRegistry().iterator();
      while (moduleIterator.hasNext()) {
         Module module = moduleIterator.next();
         Optional<Command> matchingCommand = this.findFirst((Predicate<Command>)command -> {
            String commandName = command.getName();
            String moduleName = module.getName();
            return commandName.equalsIgnoreCase(moduleName);
         });
         if (matchingCommand.isPresent()) {
            continue;
         }
         for (String alias : module.getAliases()) {
            ModuleAliasBuilder aliasBuilder = new ModuleAliasBuilder(alias.toLowerCase());
            ModuleCommand moduleCommand = new ModuleCommand(module);
            moduleCommand.exec(aliasBuilder);
            commandDispatcher.register(aliasBuilder);
         }
      }
   }

}
