/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;


import java.util.function.Predicate;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.ClientPacketListenerSendChatEvent;
import dev.zio.client.events.ChatMessageEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.HudRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.audio.BuiltinSound;
import dev.zio.client.utils.ClampedInterpolatedFloat;
import dev.zio.client.audio.SoundClip;
import dev.zio.client.managers.SoundManager;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.events.EventStage;
import dev.zio.client.utils.collections.NamedIdentifier;
import dev.zio.client.modules.misc.SocialEntry;
import dev.zio.client.settings.options.SocialRole;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.IdentifierSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.StringSetting;

import java.awt.Color;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.util.thread.BlockableEventLoop;

public class BetterChatModule extends Module {
   private static final Pattern NUMERIC_MESSAGE_PATTERN;
   public static boolean addingMessageToDisplayQueue;
   public static boolean clearingChatFromDebugKey;
   private static BetterChatModule instance;
   final Setting<Boolean> timestampsSetting;
   private final Setting<Boolean> onlyServerSetting;
   private final Setting<FormatChoice> formatSetting;
   private final Setting<Color> textColorSetting;
   private final Setting<Color> bracketsColorSetting;
   final Setting<Boolean> rainbowSetting;
   private final Setting<Integer> rainbowAmountSetting;
   private final Setting<String> leftBracketSetting;
   private final Setting<String> rightBracketSetting;
   private final Setting<Boolean> mentionSoundSetting;
   private final Setting<NamedIdentifier> mentionSoundModeSetting;
   private final Setting<Float> mentionVolumeSetting;
   private final Setting<Boolean> privateMessageSoundSetting;
   private final Setting<NamedIdentifier> privateMessageSoundModeSetting;
   private final Setting<Float> privateMessageVolumeSetting;
   final Setting<Boolean> animationSetting;
   public final Setting<TypeChoice> typeSetting;
   public final Setting<Float> speedSetting;
   public final Setting<Integer> alphaSetting;
   public final Setting<Boolean> alwaysSetting;
   private final Setting<Boolean> messageSetting;
   private final Setting<String> prefixSetting;
   private final Setting<String> suffixSetting;
   public final Setting<Boolean> highlightSetting;
   public final Setting<Color> colorSetting;
   private final Setting<Boolean> selfHighlightSetting;
   private final Setting<Boolean> friendsHighlightSetting;
   private final Setting<Boolean> enemiesHighlightSetting;
   public final Setting<Boolean> longChatHistorySetting;
   public final Setting<Boolean> noResetSetting;
   public final ClampedInterpolatedFloat bounceAnimation;
   private boolean outgoingMessagePending;

   private void restartBounceAnimation() {
      if (this.isAnimationEnabled()) {
         Setting<TypeChoice> animationType = this.typeSetting;
         if (animationType.getValue() == BetterChatModule.TypeChoice.BOUNCE) {
            this.bounceAnimation.setValue(true);
            return;
         }
      }
   }

   public static BetterChatModule getInstance() {
      return instance;
   }

   public FormattedText highlightNames(FormattedText text) {
      if (!this.isToggled() || !this.highlightSetting.getValue()) return text;
      List<NameHighlight> names = new ArrayList<>();
      if (this.selfHighlightSetting.getValue()) {
         names.add(new NameHighlight(ClientAccess.zioMinecraft.getUser().getName(), this.colorSetting.getValue()));
      }
      var socials = ClientContext.socialRegistry;
      if (socials != null) {
         for (SocialRole role : List.of(SocialRole.FRIEND, SocialRole.ENEMY)) {
            if (!(role == SocialRole.FRIEND ? this.friendsHighlightSetting : this.enemiesHighlightSetting).getValue()) continue;
            for (Object entry : socials.getRegistry()) {
               if (entry instanceof SocialEntry social && social.role() == role) {
                  names.add(new NameHighlight(social.name(), role == SocialRole.FRIEND ? socials.getFriendColor() : socials.getEnemyColor()));
               }
            }
         }
      }
      if (names.isEmpty()) return text;
      MutableComponent result = Component.empty();
      text.visit((style, segment) -> {
         int cursor = 0;
         int unhighlightedStart = 0;
         while (cursor < segment.length()) {
            NameHighlight longest = null;
            for (NameHighlight candidate : names) {
               String name = candidate.name();
               int end = cursor + name.length();
               if (name.isEmpty() || end > segment.length() || !segment.regionMatches(cursor, name, 0, name.length())) continue;
               if (cursor > 0 && isNameLetter(segment.charAt(cursor - 1))) continue;
               if (end < segment.length() && isNameLetter(segment.charAt(end))) continue;
               if (longest == null || name.length() > longest.name().length()) longest = candidate;
            }
            if (longest == null) {
               cursor++;
               continue;
            }
            if (cursor > unhighlightedStart) {
               result.append(Component.literal(segment.substring(unhighlightedStart, cursor)).setStyle(style));
            }
            int end = cursor + longest.name().length();
            Color color = longest.color();
            Style highlightedStyle = Style.EMPTY.withColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 255).getRGB())
               .withBold(style.isBold()).withItalic(style.isItalic()).withStrikethrough(style.isStrikethrough())
               .withUnderlined(style.isUnderlined()).withObfuscated(style.isObfuscated());
            result.append(Component.literal(segment.substring(cursor, end)).setStyle(highlightedStyle));
            cursor = end;
            unhighlightedStart = cursor;
         }
         if (unhighlightedStart < segment.length()) {
            result.append(Component.literal(segment.substring(unhighlightedStart)).setStyle(style));
         }
         return Optional.empty();
      }, Style.EMPTY);
      return result;
   }

   private static boolean isNameLetter(char character) {
      return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z';
   }

   private record NameHighlight(String name, Color color) {}

   public boolean isAnimationEnabled() {
      return this.isToggled() && this.animationSetting.getValue();
   }

   static {
      NUMERIC_MESSAGE_PATTERN = Pattern.compile(("^\\d+$"));
      addingMessageToDisplayQueue = false;
      clearingChatFromDebugKey = false;
   }

   private void playMentionSound(ChatMessageEvent event) {
      Setting<Boolean> mentionToggle = this.mentionSoundSetting;
      if (!mentionToggle.getValue()) {
         return;
      }
      Component mentionMessage = event.getMessage();
      String messageText = mentionMessage.getString();
      String lowercaseMessage = messageText.toLowerCase();
      Setting<Boolean> privateMessageToggle = this.privateMessageSoundSetting;
      if (privateMessageToggle.getValue()) {
         if (ChatUtils.isIncomingWhisper(lowercaseMessage)) {
            return;
         }
      }
      if (this.outgoingMessagePending) {
         return;
      }
      Minecraft client = ClientAccess.zioMinecraft;
      User user = client.getUser();
      String username = user.getName();
      if (lowercaseMessage.contains(username.toLowerCase())) {
         SoundManager soundManager = ClientContext.soundManager;
         Setting<NamedIdentifier> selectedSound = this.mentionSoundModeSetting;
         NamedIdentifier soundId = selectedSound.getValue();
         SoundClip sound = soundManager.getSound(soundId);
         Setting<Float> selectedVolume = this.mentionVolumeSetting;
         float volume = selectedVolume.getValue();
         sound.play(volume);
      }
   }

   @EventHandler
   private void onChatAnimationRender(HudRenderEvent event) {
      ClampedInterpolatedFloat animation = this.bounceAnimation;
      clearBounceTarget(animation);
      this.bounceAnimation.updateAndGet();
   }

   private static void clearBounceTarget(ClampedInterpolatedFloat animation) {
      animation.setTarget(0.0F);
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      if (event.isCancelled()) {
         return;
      }
      if (!(event.getPacket() instanceof ServerboundChatPacket)
         && !(event.getPacket() instanceof ServerboundChatCommandPacket)) {
         return;
      }
      this.outgoingMessagePending = true;
   }

   private void playPrivateMessageSound(ChatMessageEvent event) {
      if (event.isCancelled()) {
         return;
      }
      Setting<Boolean> privateMessageToggle = this.privateMessageSoundSetting;
      if (!privateMessageToggle.getValue() || event.getMessage() == null) {
         return;
      }
      Component privateMessage = event.getMessage();
      String messageText = privateMessage.getString();
      if (!ChatUtils.isIncomingWhisper(messageText)) {
         return;
      }
      Minecraft soundClient = ClientAccess.zioMinecraft;
      Runnable soundTask = () -> {
         SoundManager soundManager = ClientContext.soundManager;
         Setting<NamedIdentifier> selectedSound = this.privateMessageSoundModeSetting;
         NamedIdentifier soundId = selectedSound.getValue();
         SoundClip sound = soundManager.getSound(soundId);
         Setting<Float> selectedVolume = this.privateMessageVolumeSetting;
         float volume = selectedVolume.getValue();
         sound.play(volume);
      };
      ((BlockableEventLoop)soundClient).executeIfPossible(soundTask);
   }

   @EventHandler
   private void onChatMessage(ChatMessageEvent event) {
      if (event.isCancelled() || event.getStage() != EventStage.Pre) {
         return;
      }
      this.playPrivateMessageSound(event);
      this.playMentionSound(event);
      this.prependTimestamp(event);
      this.restartBounceAnimation();
      this.outgoingMessagePending = false;
   }

   @EventHandler
   private void onClientPacketListenerSendChatEvent(ClientPacketListenerSendChatEvent event) {
      String message = event.getMessage();
      Pattern numericPattern = NUMERIC_MESSAGE_PATTERN;
      Matcher numericMessage = numericPattern.matcher(message);
      if (numericMessage.matches()) {
         return;
      }
      Setting<Boolean> messageToggle = this.messageSetting;
      if (!messageToggle.getValue()) {
         return;
      }
      Setting<String> selectedPrefix = this.prefixSetting;
      String prefix = selectedPrefix.getValue();
      Setting<String> selectedSuffix = this.suffixSetting;
      String suffix = selectedSuffix.getValue();
      // Preserve the original formatter's sequential placeholder replacement.
      message = "\u0001\u0001 \u0001"
         .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(prefix)))
         .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(message)))
         .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(suffix)));
      int messageLength = message.length();
      if (messageLength >= (256)) {
         message = message.substring(0, 256);
      }
      event.setMessage(message);
   }

   private void prependTimestamp(ChatMessageEvent event) {
      Setting<Boolean> timestampsToggle = this.timestampsSetting;
      if (!timestampsToggle.getValue()) {
         return;
      }
      GuiMessageTag tag = event.getTag();
      if (tag != GuiMessageTag.system()
         && tag != GuiMessageTag.systemSinglePlayer()
         && tag != GuiMessageTag.chatNotSecure()) {
         Setting<Boolean> onlyServerToggle = this.onlyServerSetting;
         if (onlyServerToggle.getValue()) {
            return;
         }
      }
      MutableComponent timestamp = Component.empty();
      Setting<String> selectedLeftBracket = this.leftBracketSetting;
      String leftBracket = selectedLeftBracket.getValue();
      MutableComponent leftBracketText = Component.literal(leftBracket);
      UnaryOperator<Style> leftBracketStyle = style -> {
         Setting<Color> selectedColor = this.bracketsColorSetting;
         Color color = selectedColor.getValue();
         int rgb = color.hashCode();
         return style.withColor(rgb);
      };
      Component leftBracketStyled = leftBracketText.withStyle(leftBracketStyle);
      timestamp = timestamp.append(leftBracketStyled);
      Setting<FormatChoice> selectedFormat = this.formatSetting;
      BetterChatModule.FormatChoice format = selectedFormat.getValue();
      Date now = new Date();
      String formattedTime = format.format(now);
      MutableComponent formattedTimestampText = Component.literal(formattedTime);
      UnaryOperator<Style> timestampStyle = style -> {
         Setting<Color> selectedColor = this.textColorSetting;
         Color color = selectedColor.getValue();
         int rgb = color.hashCode();
         return style.withColor(rgb);
      };
      Component styledTimestampText = formattedTimestampText.withStyle(timestampStyle);
      timestamp = timestamp.append(styledTimestampText);
      Setting<String> selectedRightBracket = this.rightBracketSetting;
      String rightBracket = selectedRightBracket.getValue();
      MutableComponent rightBracketText = Component.literal(rightBracket);
      UnaryOperator<Style> rightBracketStyle = style -> {
         Setting<Color> selectedColor = this.bracketsColorSetting;
         Color color = selectedColor.getValue();
         int rgb = color.hashCode();
         return style.withColor(rgb);
      };
      Component rightBracketStyled = rightBracketText.withStyle(rightBracketStyle);
      timestamp = timestamp.append(rightBracketStyled);
      MutableComponent prefix = timestamp.append(" ");
      Setting<Boolean> rainbowToggle = this.rainbowSetting;
      if (rainbowToggle.getValue()) {
         MutableComponent rainbowPrefix = Component.empty();
         Component timestampContent = prefix;
         String timestampText = timestampContent.getString();
         Supplier<Integer> rainbowAmount = () -> {
            Setting<Integer> selectedAmount = this.rainbowAmountSetting;
            int amount = selectedAmount.getValue();
            int multiplier = 10;
            return (int)(amount * multiplier);
         };
         Setting<Color> selectedColor = this.textColorSetting;
         Color rainbowColor = selectedColor.getValue();
         Component rainbowTimestamp = ChatUtils.rainbowText(timestampText, rainbowAmount, rainbowColor);
         prefix = rainbowPrefix.append(rainbowTimestamp);
      }
      Component message = event.getMessage();
      Component timestampedMessage = prefix.append(message);
      event.setMessage(timestampedMessage);
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      instance = (BetterChatModule)registry.getModule(
         BetterChatModule.class
      );
   }

   public BetterChatModule() {
      super("BetterChat", "Allows you to customize your chat as you please.", ModuleCategory.MISC, new String[0]);
      BooleanSetting timestamps = new BooleanSetting("Timestamps", false);
      timestamps.markVisibilityDependency();
      this.timestampsSetting = this.add(timestamps);
      this.onlyServerSetting = this.add(new BooleanSetting("OnlyServer", true).dependsOn(this.timestampsSetting));
      Predicate<Object> formatVisibility = ignored -> this.timestampsSetting.isExpanded();
      this.formatSetting = this.add(new EnumSetting<>("Format", FormatChoice.MINUTES, formatVisibility));
      Predicate<Object> textColorVisibility = ignored -> this.timestampsSetting.isExpanded();
      this.textColorSetting = this.add(new ColorSetting("TextColor", new Color(-1, true), textColorVisibility));
      Predicate<Object> bracketsColorVisibility = ignored -> this.timestampsSetting.isExpanded();
      this.bracketsColorSetting = this.add(new ColorSetting("BracketsColor", new Color(-8355712, true), bracketsColorVisibility));
      Predicate<Object> rainbowVisibility = ignored -> this.timestampsSetting.isExpanded();
      this.rainbowSetting = this.add(new BooleanSetting("Rainbow", false, rainbowVisibility));
      Predicate<Object> rainbowAmountVisibility = ignored -> this.timestampsSetting.isExpanded() && this.rainbowSetting.getValue();
      this.rainbowAmountSetting = this.add(new NumberSetting<>("RainbowAmount", 10, 1, 100, rainbowAmountVisibility));
      Predicate<Object> leftBracketVisibility = ignored -> this.timestampsSetting.isExpanded();
      this.leftBracketSetting = this.add(new StringSetting("LeftBracket", "<", leftBracketVisibility));
      Predicate<Object> rightBracketVisibility = ignored -> this.timestampsSetting.isExpanded();
      this.rightBracketSetting = this.add(new StringSetting("RightBracket", ">", rightBracketVisibility));
      BooleanSetting mentionSound = new BooleanSetting("MentionSound", false);
      mentionSound.markVisibilityDependency();
      this.mentionSoundSetting = this.add(mentionSound);
      this.mentionSoundModeSetting = this.add(new IdentifierSetting("SoundMode", BuiltinSound.CSS.getIdentifier()).dependsOn(this.mentionSoundSetting));
      this.mentionVolumeSetting = this.add(new NumberSetting<>("Volume", 1.0F, 0.0F, 1.0F).dependsOn(this.mentionSoundSetting));
      BooleanSetting privateMessageSoundToggle = new BooleanSetting("PMSound", false);
      privateMessageSoundToggle.markVisibilityDependency();
      this.privateMessageSoundSetting = this.add(privateMessageSoundToggle);
      this.privateMessageSoundModeSetting = this.add(new IdentifierSetting("SoundMode", BuiltinSound.VK.getIdentifier()).dependsOn(this.privateMessageSoundSetting));
      this.privateMessageVolumeSetting = this.add(new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F).dependsOn(this.privateMessageSoundSetting));
      BooleanSetting animation = new BooleanSetting("Animation", false);
      animation.markVisibilityDependency();
      this.animationSetting = this.add(animation);
      Predicate<Object> animationTypeVisibility = ignored -> this.animationSetting.isExpanded();
      this.typeSetting = this.add(new EnumSetting<>("Type", TypeChoice.HORIZONTAL, animationTypeVisibility));
      Predicate<Object> animationSpeedVisibility = ignored -> this.animationSetting.isExpanded();
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.1F, 3.0F, animationSpeedVisibility));
      this.alphaSetting = this.add(new NumberSetting<>("Alpha", 250, 0, 1000).withUnit("ms").dependsOn(this.animationSetting));
      this.alwaysSetting = this.add(new BooleanSetting("Always", false).dependsOn(this.animationSetting));
      BooleanSetting message = new BooleanSetting("Message", false);
      message.markVisibilityDependency();
      this.messageSetting = this.add(message);
      this.prefixSetting = this.add(new StringSetting("Prefix", "").dependsOn(this.messageSetting));
      this.suffixSetting = this.add(new StringSetting("Suffix", "⋆ ᴍɪᴏ").dependsOn(this.messageSetting));
      BooleanSetting highlight = new BooleanSetting("Highlight", false);
      highlight.markVisibilityDependency();
      this.highlightSetting = this.add(highlight);
      this.selfHighlightSetting = this.add(new BooleanSetting("Self", true).dependsOn(this.highlightSetting));
      this.friendsHighlightSetting = this.add(new BooleanSetting("Friends", false).dependsOn(this.highlightSetting));
      this.enemiesHighlightSetting = this.add(new BooleanSetting("Enemies", false).dependsOn(this.highlightSetting));
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-43691, true)).dependsOn(this.highlightSetting, this.selfHighlightSetting));
      this.longChatHistorySetting = this.add(new BooleanSetting("LongChatHistory", false));
      this.noResetSetting = this.add(new BooleanSetting("NoReset", false));
      this.bounceAnimation = new ClampedInterpolatedFloat(this.speedSetting::getValue, true);
      Setting<Integer> alphaDisplaySetting = this.alphaSetting;
      SettingLabelCondition alphaDisplayPolicy = SettingLabelCondition.MIN;
      alphaDisplaySetting.setBoundaryLabel("None", alphaDisplayPolicy);
      instance = this;
      this.setDrawn(false);
      this.colorSetting.setConfigName("HighlightColor");
      this.privateMessageSoundModeSetting.setConfigName("PMSoundMode");
      this.privateMessageVolumeSetting.setConfigName("PMVolume");
   }

   enum FormatChoice implements NamedEntry {
      HOURS(new SimpleDateFormat("HH"), "Hours"),
      MINUTES(new SimpleDateFormat("HH:mm"), "Minutes"),
      SECONDS(new SimpleDateFormat("HH:mm:ss"), "Seconds");

      private final DateFormat dateFormat;
      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      FormatChoice(DateFormat dateFormat, String displayName) {
         this.dateFormat = dateFormat;
         this.displayName = displayName;
      }

      public String format(Date date) {
         return this.dateFormat.format(date);
      }
   }

   public enum TypeChoice implements NamedEntry {
      VERTICAL("Vertical"),
      HORIZONTAL("Horizontal"),
      BOTH("Both"),
      BOUNCE("Bounce"),
      NONE("None");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      TypeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
