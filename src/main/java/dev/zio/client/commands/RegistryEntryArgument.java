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
import dev.zio.client.utils.collections.NamedRegistryEntry;
import dev.zio.client.utils.collections.RegistryGroupMatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class RegistryEntryArgument implements ArgumentType, ClientAccess {
   private final BiPredicate suggestionFilter;
   private final Registry registry;
   private static final String MISSING_ENTRY_PREFIX;

   public RegistryEntryArgument(Registry registry, BiPredicate suggestionFilter) {
      this.suggestionFilter = suggestionFilter;
      this.registry = registry;
   }

   static {
      MISSING_ENTRY_PREFIX = "Element not found ";
   }

   @Override
   public Object parse(StringReader reader) throws CommandSyntaxException {

      String input = reader.readString();
      Registry registrySnapshot = this.registry;
      Identifier id = Identifier.parse(input);
      Optional<?> entry = registrySnapshot.get(id);
      Supplier<CommandSyntaxException> missingEntry = () -> {
         DynamicCommandExceptionType exceptionTypeSnapshot = new DynamicCommandExceptionType(
            missingValue -> {
               String argumentText = String.valueOf(missingValue);
               String message = MISSING_ENTRY_PREFIX.concat(argumentText);
               return Component.nullToEmpty(message);
            }
         );
         return exceptionTypeSnapshot.create(input);
      };
      Reference holder = (Reference)entry.orElseThrow(missingEntry);
      return holder.value();

   }

   public static ArgumentType registry(Registry registry, BiPredicate suggestionFilter) {
      return new RegistryEntryArgument(registry, suggestionFilter);
   }

   public static ArgumentType registry(Registry registry) {
      return new RegistryEntryArgument(registry, (ignoredContext, ignoredEntry) -> true);
   }

   @Override
   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      Registry registrySnapshot = this.registry;
      Set<Identifier> ids = registrySnapshot.keySet();
      Stream<String> suggestions = ids.stream()
         .filter(id -> this.suggestionFilter.test(context, this.registry.getValue(id)))
         .map(Identifier::toShortLanguageKey);
      return SharedSuggestionProvider.suggest(suggestions, builder);
   }

   public static ArgumentType aliased(Registry registry, IntFunction arrayFactory) {
      return new RegistryEntryArgument.GroupedEntries(registry, (ignoredContext, ignoredEntry) -> true, arrayFactory);
   }

   public static ArgumentType aliased(Registry registry, BiPredicate suggestionFilter, IntFunction arrayFactory) {
      return new RegistryEntryArgument.GroupedEntries(registry, suggestionFilter, arrayFactory);
   }

   public static class GroupedEntries implements ArgumentType, ClientAccess {
      private final Registry registry;
      private final RegistryGroupMatcher groupMatcher;
      private final BiPredicate suggestionFilter;
      private final IntFunction arrayFactory;
      private static final String MISSING_ENTRY_PREFIX;

      public GroupedEntries(Registry registry, BiPredicate suggestionFilter, IntFunction arrayFactory) {
         this.registry = registry;
         this.groupMatcher = RegistryGroupMatcher.forRegistry(registry);
         this.suggestionFilter = suggestionFilter;
         this.arrayFactory = arrayFactory;
      }

      static {
         MISSING_ENTRY_PREFIX = "Element not found: ";
      }

      @Override
      public Object[] parse(StringReader reader) throws CommandSyntaxException {

         String input = reader.readString();
         Collection<NamedRegistryEntry> groupEntries = this.groupMatcher.getGroupEntries(input);
         if (!groupEntries.isEmpty()) {
            return groupEntries.stream().map(NamedRegistryEntry::value).toArray(this.arrayFactory);
         }

         Registry registrySnapshot = this.registry;
         Identifier id = Identifier.parse(input);
         Optional<?> entry = registrySnapshot.get(id);
         Supplier<CommandSyntaxException> missingEntry = () -> {
            DynamicCommandExceptionType exceptionTypeSnapshot = new DynamicCommandExceptionType(
               missingValue -> {
                  String argumentText = String.valueOf(missingValue);
                  String message = MISSING_ENTRY_PREFIX.concat(argumentText);
                  return Component.nullToEmpty(message);
               }
            );
            return exceptionTypeSnapshot.create(input);
         };
         Reference holder = (Reference)entry.orElseThrow(missingEntry);
         Object value = holder.value();
         IntFunction factory = this.arrayFactory;
         Object[] result = (Object[])factory.apply(1);
         result[0] = value;
         return result;

      }

      @Override
      public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
         Collection<String> groupNames = this.groupMatcher.getGroupNames();
         Stream<String> groupSuggestions = groupNames.stream().filter(candidateName -> {
            Collection<NamedRegistryEntry> groupEntries = this.groupMatcher.getGroupEntries(candidateName);
            return groupEntries.stream().map(NamedRegistryEntry::value)
               .anyMatch(candidateValue -> this.suggestionFilter.test(context, candidateValue));
         });
         Registry registrySnapshot = this.registry;
         Set<Identifier> ids = registrySnapshot.keySet();
         Stream<String> entrySuggestions = ids.stream()
            .filter(id -> this.suggestionFilter.test(context, this.registry.getValue(id)))
            .map(Identifier::toShortLanguageKey);
         Stream<String> suggestions = Stream.concat(groupSuggestions, entrySuggestions);
         return SharedSuggestionProvider.suggest(suggestions, builder);
      }
   }
}
