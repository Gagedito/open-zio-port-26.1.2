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
import dev.zio.client.events.InputPressEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.managers.MacroRegistry;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.config.JsonUtils;
import dev.zio.client.modules.misc.Macro;
import dev.zio.client.managers.CommandRegistry;
import dev.zio.client.modules.Keybind;
import dev.zio.client.settings.options.MacroType;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Base64.Encoder;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.network.chat.ClickEvent.SuggestCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class MacroCommand extends Command {
   private boolean waitingForKey;
   private String pendingMacroName;
   private MacroType pendingMacroType;

   private RequiredArgumentBuilder commandArgument() {

      return Command.argument("command", StringArgumentType.greedyString()).suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>)this::suggestCommands);
   }

   public MacroCommand() {
      super("macro");
      ClientAccess.eventBus.subscribe(this);
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {

      LiteralArgumentBuilder deleteBranch = this.buildMacroBranch("delete", (Consumer<RequiredArgumentBuilder>)deleteArgument -> deleteArgument.executes((com.mojang.brigadier.Command<Object>)deleteContext -> {

               Macro deletedMacro = (Macro)(deleteContext.getArgument("macro", Macro.class));
               ClientContext.macroRegistry.unregisterEntry(deletedMacro);
               String deletedName = FontRenderer.capitalize(
                  deletedMacro.getName()
               );
               MutableComponent deletedMessage = Component.literal(deletedName);

               deletedMessage = deletedMessage.append(" has been deleted");

               ChatUtils.printChat(deletedMessage, ChatUtils.DEFAULT_SIGNATURE);
               return 1;
            }));
      LiteralArgumentBuilder registrationBuilder = (LiteralArgumentBuilder)builder.then(deleteBranch);

      LiteralArgumentBuilder commandsBranch = this.buildMacroBranch("commands", (Consumer<RequiredArgumentBuilder>)this::configureCommands);
      registrationBuilder = (LiteralArgumentBuilder)registrationBuilder.then(commandsBranch);

      LiteralArgumentBuilder literalBuilder = Command.literal("new");

      RequiredArgumentBuilder nameArgument = Command.argument("name", FancyStringArgument.fancyString());

      RequiredArgumentBuilder typeArgument = Command.argument("type", new EnumArgumentType(MacroType.class, "Macro"));

      RequiredArgumentBuilder keyArgument = Command.argument("key", new KeybindArgumentType());

      ArgumentBuilder createWithCommand = this.commandArgument().executes((com.mojang.brigadier.Command<Object>)createContext -> {

            String createdName = (String)(createContext.getArgument("name", String.class));

            String createdCommand = (String)(createContext.getArgument("command", String.class));

            MacroType createdType = (MacroType)(createContext.getArgument("type", MacroType.class));

            Keybind createdKeybind = (Keybind)(createContext.getArgument("key", Keybind.class));
            Macro createdMacro = createdType.create(createdName, createdKeybind);
            createdMacro.getCommands().add(createdCommand);
            ClientContext.macroRegistry.registerEntry(createdMacro);

            MutableComponent createNamedMessage = Component.literal("Macro ").append(createdMacro.getDisplayComponent());

            MutableComponent createdMessage = createNamedMessage.append(" has been created");

            ChatUtils.printChat(createdMessage, ChatUtils.DEFAULT_SIGNATURE);
            return 1;
         });
      ArgumentBuilder createWithKey = ((RequiredArgumentBuilder)keyArgument.then(createWithCommand)).executes((com.mojang.brigadier.Command<Object>)emptyCreateContext -> {

            String emptyMacroName = (String)(emptyCreateContext.getArgument("name", String.class));

            MacroType emptyMacroType = (MacroType)(emptyCreateContext.getArgument("type", MacroType.class));

            Keybind emptyMacroKeybind = (Keybind)(emptyCreateContext.getArgument("key", Keybind.class));
            Macro emptyMacro = emptyMacroType.create(emptyMacroName, emptyMacroKeybind);
            ClientContext.macroRegistry.registerEntry(emptyMacro);

            MutableComponent emptyNamedMessage = Component.literal("Macro ").append(emptyMacro.getDisplayComponent());

            MutableComponent emptyCreatedMessage = emptyNamedMessage.append(" has been created");

            ChatUtils.printChat(emptyCreatedMessage, ChatUtils.DEFAULT_SIGNATURE);
            return 1;
         });
      ArgumentBuilder captureKey = ((RequiredArgumentBuilder)typeArgument.then(createWithKey)).executes((com.mojang.brigadier.Command<Object>)captureContext -> {

            this.pendingMacroName = (String)(captureContext.getArgument("name", String.class));

            this.pendingMacroType = (MacroType)(captureContext.getArgument("type", MacroType.class));
            this.waitingForKey = true;

            MutableComponent capturePromptMessage = Component.literal("Press a key");

            ChatUtils.printChat(capturePromptMessage, ChatUtils.DEFAULT_SIGNATURE);
            return 1;
         });
      ArgumentBuilder newArguments = nameArgument.then(captureKey);
      ArgumentBuilder newBranch = literalBuilder.then(newArguments);
      registrationBuilder = (LiteralArgumentBuilder)registrationBuilder.then(newBranch);

      ArgumentBuilder listBranch = Command.literal("list").executes((com.mojang.brigadier.Command<Object>)listContext -> {
            int listIndex = 0;

            MutableComponent listHeaderMessage = Component.literal("Macro list:");

            ChatUtils.printChat(listHeaderMessage, ChatUtils.DEFAULT_SIGNATURE);
            MacroRegistry listRegistry = ClientContext.macroRegistry;

            for (Iterator listIterator = ((List)listRegistry.getRegistry()).iterator(); listIterator.hasNext(); listIndex++) {
               Macro listedMacro = (Macro)listIterator.next();

               Object[] listLineArguments = new Object[2];
               listLineArguments[0] = listedMacro.getName();
               Keybind listedKeybind = listedMacro.getKeybind();
               listLineArguments[1] = listedKeybind.getDisplayName();
               String listLine = "%s (%s)".formatted(listLineArguments);
               if (!listedMacro.isToggled()) {
                  ChatFormatting disabledColor = ChatFormatting.GRAY;
                  listLine = String.valueOf(disabledColor) + listLine;
               }

               MutableComponent listLineMessage = Component.literal(listLine);
               int listSignatureId = -listIndex;

               listSignatureId -= 2;

               ChatUtils.printChat(listLineMessage, ChatUtils.createSignature(listSignatureId));
            }

            return 0;
         });
      registrationBuilder.then(listBranch);

      LiteralArgumentBuilder bindBranch = this.buildMacroBranch("bind", (Consumer<RequiredArgumentBuilder>)this::configureBind);
      builder.then(bindBranch);

      LiteralArgumentBuilder toggleBranch = this.buildMacroBranch("toggle", (Consumer<RequiredArgumentBuilder>)this::configureToggle);
      builder.then(toggleBranch);

      literalBuilder = Command.literal("export");

      ArgumentBuilder exportArgument = Command.argument("macro", new MacroArgumentType()).executes((com.mojang.brigadier.Command<Object>)this::exportMacro);
      ArgumentBuilder exportBranch = literalBuilder.then(exportArgument);
      builder.then(exportBranch);

      ArgumentBuilder importBranch = Command.literal("import").executes((com.mojang.brigadier.Command<Object>)this::importMacro);
      builder.then(importBranch);
   }

   private int exportMacro(CommandContext context) {

      Macro macro = (Macro)(context.getArgument("macro", Macro.class));
      JsonObject config = new JsonObject();

      String macroName = macro.getName();
      config.addProperty("name", macroName);
      MacroType type = macro.getType();
      String typeName = type.getName();
      config.addProperty("type", typeName);
      Keybind keybind = macro.getKeybind();

      Integer boxedKeyCode = (int)(keybind.code());
      config.addProperty("key", boxedKeyCode);
      keybind = macro.getKeybind();

      boolean mouse = keybind.mouse();
      Boolean boxedMouse = mouse;
      config.addProperty("mouse", boxedMouse);
      JsonArray commandsJson = new JsonArray();
      Iterator commandIterator = macro.getCommands().iterator();

      while (commandIterator.hasNext()) {
         String command = (String)commandIterator.next();
         commandsJson.add(command);
      }

      config.add("commands", commandsJson);
      Gson gson = ClientAccess.gson;
      String json = gson.toJson(config);
      Encoder base64Encoder = Base64.getEncoder();
      Charset utf8 = StandardCharsets.UTF_8;
      byte[] jsonBytes = json.getBytes(utf8);
      String encodedConfig = base64Encoder.encodeToString(jsonBytes);
      KeyboardHandler keyboard = ClientAccess.zioMinecraft.keyboardHandler;
      keyboard.setClipboard(encodedConfig);

      MutableComponent message = Component.literal("Successfully copied ");
      String nameForMessage = macro.getName();
      MutableComponent nameComponent = Component.literal(nameForMessage);
      ChatFormatting nameColor = ChatFormatting.GRAY;
      MutableComponent namedMessage = message.append(nameComponent.withStyle(nameColor));

      MutableComponent completedMessage = namedMessage.append(" macro to your clipboard");

      ChatUtils.printChat(completedMessage, ChatUtils.DEFAULT_SIGNATURE);
      return 1;
   }

   private LiteralArgumentBuilder buildMacroBranch(
      String name, Consumer<RequiredArgumentBuilder> configureArgument
   ) {

      RequiredArgumentBuilder macroArgument = Command.argument("macro", new MacroArgumentType());
      configureArgument.accept(macroArgument);
      LiteralArgumentBuilder literalBuilder = Command.literal(name);
      return (LiteralArgumentBuilder)literalBuilder.then(macroArgument);
   }

   private void configureCommands(RequiredArgumentBuilder macroArgument) {

      LiteralArgumentBuilder literalBuilder = Command.literal("add");

      ArgumentBuilder addCommand = this.commandArgument().executes((com.mojang.brigadier.Command<Object>)addContext -> {

            Macro addMacro = (Macro)(addContext.getArgument("macro", Macro.class));

            String addedCommand = (String)(addContext.getArgument("command", String.class));
            if (addMacro.getType() == MacroType.HOLD) {
               int holdCommandCount = addMacro.getCommands().size();

               if (holdCommandCount >= (2)) {
                  ChatFormatting holdErrorColor = ChatFormatting.RED;
                  String holdError = String.valueOf(holdErrorColor) + "You've reached the command limit of Hold Macro";
                  MutableComponent holdErrorMessage = Component.literal(holdError);

                  ChatUtils.printChat(holdErrorMessage, ChatUtils.DEFAULT_SIGNATURE);
                  return 1;
               }
            }
            addMacro.getCommands().add(addedCommand);
            return 1;
         });
      ArgumentBuilder addBranch = literalBuilder.then(addCommand);
      RequiredArgumentBuilder commandBranches = (RequiredArgumentBuilder)macroArgument.then(addBranch);

      literalBuilder = Command.literal("remove");

      ArgumentBuilder removeIndexArgument = Command.argument("index", IntegerArgumentType.integer(1)).executes((com.mojang.brigadier.Command<Object>)removeContext -> {

            Macro removeMacro = (Macro)(removeContext.getArgument("macro", Macro.class));

            Integer removeOneBasedIndex = (Integer)(removeContext.getArgument("index", Integer.class));
            int removeIndex = removeOneBasedIndex - 1;
            if (removeIndex
                  < removeMacro.getCommands().size()
               && removeIndex >= 0
               && !removeMacro.getCommands().isEmpty()) {
               removeMacro.getCommands().remove(removeIndex);
               return 1;
            }

            ChatFormatting removeErrorColor = ChatFormatting.RED;
            String removeErrorColorText = String.valueOf(removeErrorColor);
            ChatFormatting removeResetColor = ChatFormatting.WHITE;
            String removeResetColorText = String.valueOf(removeResetColor);
            String removeError = removeErrorColorText + "Invalid index" + removeResetColorText + "\n";
            MutableComponent removeErrorMessage = Component.literal(removeError);
            removeErrorMessage.append(this.formatCommands(removeMacro));
            ChatUtils.printChat(removeErrorMessage, ChatUtils.DEFAULT_SIGNATURE);
            return 1;
         });
      ArgumentBuilder removeBranch = literalBuilder.then(removeIndexArgument);
      commandBranches = (RequiredArgumentBuilder)commandBranches.then(removeBranch);

      ArgumentBuilder clearBranch = Command.literal("clear").executes((com.mojang.brigadier.Command<Object>)clearContext -> {

         Macro clearMacro = (Macro)(clearContext.getArgument("macro", Macro.class));
         clearMacro.getCommands().clear();
         return 1;
      });
      commandBranches = (RequiredArgumentBuilder)commandBranches.then(clearBranch);

      literalBuilder = Command.literal("edit");

      RequiredArgumentBuilder editIndexArgument = Command.argument("index", IntegerArgumentType.integer(1));

      ArgumentBuilder editCommandArgument = this.commandArgument().executes((com.mojang.brigadier.Command<Object>)editContext -> {

            Macro editMacro = (Macro)(editContext.getArgument("macro", Macro.class));

            String editedCommand = (String)(editContext.getArgument("command", String.class));

            Integer editOneBasedIndex = (Integer)(editContext.getArgument("index", Integer.class));
            int editIndex = editOneBasedIndex - 1;
            if (editIndex
               >= editMacro.getCommands().size()) {
               ChatFormatting editErrorColor = ChatFormatting.RED;
               String editErrorColorText = String.valueOf(editErrorColor);
               ChatFormatting editResetColor = ChatFormatting.WHITE;
               String editResetColorText = String.valueOf(editResetColor);
               String editError = editErrorColorText + "Invalid index" + editResetColorText + "\n";
               MutableComponent editErrorMessage = Component.literal(editError);
               editErrorMessage.append(this.formatCommands(editMacro));
               ChatUtils.printChat(editErrorMessage, ChatUtils.DEFAULT_SIGNATURE);
               return 1;
            } else {
               editMacro.getCommands().set(editIndex, editedCommand);
               return 1;
            }
         });
      ArgumentBuilder editArguments = editIndexArgument.then(editCommandArgument);
      ArgumentBuilder editBranch = literalBuilder.then(editArguments);
      ((RequiredArgumentBuilder)commandBranches.then(editBranch)).executes((com.mojang.brigadier.Command<Object>)showContext -> {

            Macro shownMacro = (Macro)(showContext.getArgument("macro", Macro.class));
            Component commandListMessage = this.formatCommands(shownMacro);

            ChatUtils.printChat(commandListMessage, ChatUtils.DEFAULT_SIGNATURE);
            return 1;
         });
   }

   private Component formatCommands(Macro macro) {
      String header = String.valueOf(macro.getName()) + "' command list: \n";
      MutableComponent message = Component.literal(header);
      int lineNumber = 1;

      while (lineNumber <= macro.getCommands().size()) {
         String lineNumberText = String.valueOf(lineNumber);
         message.append(lineNumberText);

         message.append(". ");
         String command = (String)macro.getCommands().get(lineNumber - 1);
         message.append(command);
         message.append(" ");
         message.append(this.formatEditAction(macro, lineNumber));
         message.append(" ");
         message.append(this.formatRemoveAction(macro, lineNumber));
         if (lineNumber != macro.getCommands().size()) {
            message.append("\n");
         }

         lineNumber++;
      }
      return message;
   }

   private Component formatEditAction(Macro macro, int lineNumber) {

      Object[] commandArguments = new Object[2];
      commandArguments[0] = macro.getName();
      commandArguments[1] = lineNumber;
      String suggestedCommand = ";macro commands %s edit %d ".formatted(commandArguments);

      String hoverText = "click to edit";

      MutableComponent labelComponent = Component.literal("[Edit]");
      ChatFormatting labelColor = ChatFormatting.GRAY;
      int color = labelColor.getColor();
      return labelComponent.withColor(color).withStyle(((UnaryOperator<Style>)style -> style.withClickEvent(new SuggestCommand(suggestedCommand)))).withStyle(((UnaryOperator<Style>)hoverStyle -> {
            return hoverStyle.withHoverEvent(new ShowText(Component.literal(hoverText)));
         }));
   }

   private void configureToggle(RequiredArgumentBuilder macroArgument) {

      ArgumentBuilder setStateArgument = Command.argument("state", BoolArgumentType.bool()).executes((com.mojang.brigadier.Command<Object>)setContext -> {

            Macro setMacro = (Macro)(setContext.getArgument("macro", Macro.class));

            boolean requestedState = BoolArgumentType.getBool(setContext, "state");
            setMacro.setToggled(requestedState);
            String setStateText = setMacro.isToggled() ? "enabled" : "disabled";

            Object[] setMessageArguments = new Object[2];
            setMessageArguments[0] = setMacro.getName();
            setMessageArguments[1] = setStateText;
            String setMessageText = "Macro %s has been %s".formatted(setMessageArguments);
            MutableComponent setMessage = Component.literal(setMessageText);

            ChatUtils.printChat(setMessage, ChatUtils.DEFAULT_SIGNATURE);
            return 1;
         });
      ((RequiredArgumentBuilder)macroArgument.then(setStateArgument)).executes((com.mojang.brigadier.Command<Object>)toggleContext -> {

            Macro toggledMacro = (Macro)(toggleContext.getArgument("macro", Macro.class));
            boolean nextState = !toggledMacro.isToggled();
            toggledMacro.setToggled(nextState);
            String toggleStateText = toggledMacro.isToggled() ? "enabled" : "disabled";

            Object[] toggleMessageArguments = new Object[2];
            toggleMessageArguments[0] = toggledMacro.getName();
            toggleMessageArguments[1] = toggleStateText;
            String toggleMessageText = "Macro %s has been %s".formatted(toggleMessageArguments);
            MutableComponent toggleMessage = Component.literal(toggleMessageText);

            ChatUtils.printChat(toggleMessage, ChatUtils.DEFAULT_SIGNATURE);
            return 1;
         });
   }

   private static boolean isCommandPrefix(char character) {
      return character == CommandRegistry.getPrefixCharacter();
   }

   private CompletableFuture suggestCommands(
      CommandContext context, SuggestionsBuilder builder
   ) {
      try {
         int absoluteStart = builder.getStart();
         int relativeCursor = 0;
         StringBuilder commandText = new StringBuilder();
         String remaining = builder.getRemaining();

         for (char character : remaining.toCharArray()) {
            relativeCursor++;

            if (isCommandPrefix(character) || (relativeCursor == 1 && character == ' ')) {
               absoluteStart += relativeCursor;
               relativeCursor = 0;
               commandText.setLength(0);
            } else {
               commandText.append(character);
            }
         }

         int completionCursor = relativeCursor;
         int completionOffset = absoluteStart;
         CommandDispatcher completionDispatcher = CommandRegistry.commandDispatcher;
         CommandDispatcher parseDispatcher = CommandRegistry.commandDispatcher;
         String command = commandText.toString();
         Object commandSource = null;
         ParseResults parseResults = parseDispatcher.parse(command, commandSource);
         return completionDispatcher.getCompletionSuggestions(parseResults, completionCursor).thenApply((Function<Suggestions, Suggestions>)suggestions -> {
            StringRange range = suggestions.getRange();
            range = new StringRange(range.getStart() + completionOffset, range.getEnd() + completionOffset);
            List shiftedSuggestions = suggestions.getList().stream().map((Function<Suggestion, Suggestion>)suggestion -> {
               StringRange shiftedRange = new StringRange(
                  suggestion.getRange().getStart() + completionOffset,
                  suggestion.getRange().getEnd() + completionOffset
               );
               return new Suggestion(shiftedRange, suggestion.getText(), suggestion.getTooltip());
            }).toList();
            return new Suggestions(range, shiftedSuggestions);
         });
      } catch (Exception suggestionFailure) {
         return builder.buildFuture();
      }
   }

   @EventHandler
   private void onInputPress(InputPressEvent event) {
      if (this.waitingForKey
         && this.pendingMacroType != null
         && this.pendingMacroName != null) {
         MacroType capturedType = this.pendingMacroType;
         String capturedName = this.pendingMacroName;
         Keybind keybind = new Keybind(
            event.getCode(), Keybind.Mode.TOGGLE, event.isMouse()
         );
         Macro macro = capturedType.create(capturedName, keybind);
         ClientContext.macroRegistry.registerEntry(macro);

         MutableComponent namedMessage = Component.literal("Macro ").append(macro.getDisplayComponent());

         MutableComponent createdMessage = namedMessage.append(" has been created");

         ChatUtils.printChat(createdMessage, ChatUtils.DEFAULT_SIGNATURE);
         this.waitingForKey = false;
      }
   }

   private Component formatRemoveAction(Macro macro, int lineNumber) {

      Object[] commandArguments = new Object[2];
      commandArguments[0] = macro.getName();
      commandArguments[1] = lineNumber;
      String suggestedCommand = ";macro commands %s remove %d ".formatted(commandArguments);

      String hoverText = "click to remove";

      MutableComponent labelComponent = Component.literal("[Remove]");
      ChatFormatting labelColor = ChatFormatting.GRAY;
      int color = labelColor.getColor();
      return labelComponent.withColor(color).withStyle(((UnaryOperator<Style>)style -> style.withClickEvent(new SuggestCommand(suggestedCommand)))).withStyle(((UnaryOperator<Style>)hoverStyle -> {
            return hoverStyle.withHoverEvent(new ShowText(Component.literal(hoverText)));
         }));
   }

   private void configureBind(RequiredArgumentBuilder macroArgument) {

      ArgumentBuilder keyArgument = Command.argument("key", new KeybindArgumentType()).executes((com.mojang.brigadier.Command<Object>)bindContext -> {

            Macro previousMacro = (Macro)(bindContext.getArgument("macro", Macro.class));

            Keybind newKeybind = (Keybind)(bindContext.getArgument("key", Keybind.class));
            MacroType previousType = previousMacro.getType();
            String previousName = previousMacro.getName();
            Macro replacementMacro = previousType.create(previousName, newKeybind);
            boolean previousToggleState = previousMacro.isToggled();
            replacementMacro.setToggled(previousToggleState);
            previousMacro.getCommands().forEach((Consumer<String>)copiedCommand -> {
                  replacementMacro.getCommands().add(copiedCommand);
               });
            ClientContext.macroRegistry.unregisterEntry(previousMacro);
            ClientContext.macroRegistry.registerEntry(replacementMacro);

            Object[] bindMessageArguments = new Object[2];
            bindMessageArguments[0] = previousMacro.getName();
            bindMessageArguments[1] = newKeybind.getDisplayName();
            String bindMessageText = "Macro %s key has been changed to %s".formatted(bindMessageArguments);
            MutableComponent bindMessage = Component.literal(bindMessageText);

            ChatUtils.printChat(bindMessage, ChatUtils.DEFAULT_SIGNATURE);
            return 1;
         });
      ((RequiredArgumentBuilder)macroArgument.then(keyArgument)).executes((com.mojang.brigadier.Command<Object>)describeContext -> {

            Macro describedMacro = (Macro)(describeContext.getArgument("macro", Macro.class));

            Object[] describeArguments = new Object[2];
            describeArguments[0] = describedMacro.getName();
            Keybind describedKeybind = describedMacro.getKeybind();
            describeArguments[1] = describedKeybind.getDisplayName();
            String description = "%s (%s)".formatted(describeArguments);
            if (!describedMacro.isToggled()) {
               ChatFormatting disabledColor = ChatFormatting.GRAY;
               description = String.valueOf(disabledColor) + description;
            }

            description = "Macro: " + String.valueOf(description);
            MutableComponent descriptionMessage = Component.literal(description);

            ChatUtils.printChat(descriptionMessage, ChatUtils.DEFAULT_SIGNATURE);
            return 1;
         });
   }

   private int importMacro(CommandContext context) {
      KeyboardHandler keyboard = ClientAccess.zioMinecraft.keyboardHandler;
      String clipboard = keyboard.getClipboard();

      try {
         String decodedJson = new String(Base64.getDecoder().decode(clipboard.getBytes()));
         JsonObject config = (JsonObject)JsonParser.parseString(decodedJson);

         String[] requiredFields = new String[5];

         requiredFields[0] = "name";

         requiredFields[1] = "type";

         requiredFields[(2)] = "key";

         requiredFields[(3)] = "mouse";

         requiredFields[(4)] = "commands";
         if (!JsonUtils.hasProperties(config, requiredFields)) {
            throw new RuntimeException();
         }

         JsonElement typeElement = config.get("type");
         String typeName = typeElement.getAsString();
         MacroType type = MacroType.fromName(typeName);

         JsonElement nameElement = config.get("name");
         String name = nameElement.getAsString();

         JsonElement keyElement = config.get("key");
         int keyCode = keyElement.getAsInt();

         JsonElement mouseElement = config.get("mouse");
         boolean mouse = mouseElement.getAsBoolean();
         Keybind keybind = Keybind.toggle(
            keyCode, mouse
         );
         Macro macro = type.create(name, keybind);

         JsonElement commandsElement = config.get("commands");
         JsonArray commandsJson = commandsElement.getAsJsonArray();
         Iterator commandIterator = commandsJson.iterator();

         while (commandIterator.hasNext()) {
            JsonElement commandElement = (JsonElement)commandIterator.next();
            List commands = macro.getCommands();
            commands.add(commandElement.getAsString());
         }

         if (type == MacroType.HOLD) {
            while (true) {
               int holdCommandCount = macro.getCommands().size();

               if (holdCommandCount <= (2)) {
                  break;
               }
               macro.getCommands().removeLast();
            }
         }

         Optional existingMacro = ClientContext.macroRegistry.findFirst((Predicate<Macro>)candidateMacro -> {
               String candidateName = candidateMacro.getName();
               String importedName = macro.getName();
               return candidateName.equalsIgnoreCase(importedName);
            });
         MacroRegistry registryForRemoval = ClientContext.macroRegistry;
         existingMacro.ifPresent((Consumer<Macro>)registryForRemoval::unregisterEntry);
         ClientContext.macroRegistry.registerEntry(macro);

         MutableComponent message = Component.literal("Successfully loaded ");
         String nameForMessage = macro.getName();
         MutableComponent nameComponent = Component.literal(nameForMessage);
         ChatFormatting nameColor = ChatFormatting.GRAY;
         MutableComponent namedMessage = message.append(nameComponent.withStyle(nameColor));

         MutableComponent completedMessage = namedMessage.append(" config from your clipboard");

         ChatUtils.printChat(completedMessage, ChatUtils.DEFAULT_SIGNATURE);
      } catch (Throwable importFailure) {

         MutableComponent errorMessage = Component.literal("Failed to load macro from your clipboard");

         ChatUtils.printChat(errorMessage, ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.MID);
      }

      return 1;
   }

}
