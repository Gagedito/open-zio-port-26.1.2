/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.client;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PlayerTotemEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.managers.AliasRegistry;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.events.ModuleToggleEvent;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.managers.ConfigurationManager;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.StringSetting;

import java.awt.Color;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.apache.commons.io.FileUtils;

public class NotificationsModule extends Module {
   public final Setting<Color> watermarkSetting;
   public final Setting<Color> bracketsSetting;
   private final Setting<Boolean> commandsSetting;
   private final Setting<Boolean> modulesSetting;
   private final Setting<Boolean> onlyDrawnSetting;
   final Setting<Boolean> totemPopsSetting;
   private final Setting<Boolean> selfSetting;
   private final Setting<Color> primarySetting;
   private final Setting<Color> secondarySetting;
   final Setting<Boolean> sayChatSetting;
   private final Setting<String> pathSetting;
   private final Setting<Float> delaySetting;
   private final Setting<Boolean> onlyEnemiesSetting;
   private final Setting<Boolean> randomSetting;
   private final Setting<Boolean> refreshSetting;
   private int totemMessageIndex;
   private final List<String> totemMessages;
   private final Random totemRandom;

   @Override
   public void onEnable() {
      this.reloadTotemMessages();
   }

   private void printTotemNotification(String playerName, String actionText, int popCount) {
      Options options = ClientAccess.zioMinecraft.options;
      OptionInstance visibilityOption = options.chatVisibility();
      if (visibilityOption.get() != ChatVisiblity.HIDDEN) {
         MutableComponent messageBuilder = Component.empty();
         messageBuilder = messageBuilder.append(Component.literal(playerName).withStyle(lambdaStyle -> {
            Setting<Color> lambdaColorOption = this.primarySetting;
            Color lambdaColor = lambdaColorOption.getValue();
            int lambdaColorArgb = lambdaColor.hashCode();
            return lambdaStyle.withColor(lambdaColorArgb);
         }));
         messageBuilder = messageBuilder.append(Component.literal(actionText).withStyle(lambdaStyle -> {
            Setting<Color> lambdaColorOption = this.secondarySetting;
            Color lambdaColor = lambdaColorOption.getValue();
            int lambdaColorArgb = lambdaColor.hashCode();
            return lambdaStyle.withColor(lambdaColorArgb);
         }));
         String countText = String.valueOf(popCount);
         messageBuilder = messageBuilder.append(Component.literal(countText).withStyle(lambdaStyle -> {
            Setting<Color> lambdaColorOption = this.primarySetting;
            Color lambdaColor = lambdaColorOption.getValue();
            int lambdaColorArgb = lambdaColor.hashCode();
            return lambdaStyle.withColor(lambdaColorArgb);
         }));

         String suffixText = popCount == 1 ? " totem." : " totems.";
         MutableComponent message = messageBuilder.append(Component.literal(suffixText).withStyle(lambdaStyle -> {
            Setting<Color> lambdaColorOption = this.secondarySetting;
            Color lambdaColor = lambdaColorOption.getValue();
            int lambdaColorArgb = lambdaColor.hashCode();
            return lambdaStyle.withColor(lambdaColorArgb);
         }));
         int nameHash = playerName.hashCode();
         int signatureId = Math.abs(nameHash) * -1;

         ChatUtils.printChat(message, ChatUtils.createSignature((signatureId)), ChatUtils.MessagePriority.MID);
      }
   }

   @EventHandler
   private void onModuleToggle(ModuleToggleEvent event) {
      if (this.isWorldUnavailable()) {
         return;
      }
      Setting<Boolean> modulesOption = this.modulesSetting;
      Boolean notifyModules = modulesOption.getValue();
      if (!notifyModules) {
         return;
      }
      if (event.getModule() instanceof HUDModule) {
         return;
      }
      if (event.getModule() instanceof UIModule) {
         return;
      }
      Setting<Boolean> drawnOption = this.onlyDrawnSetting;
      Boolean onlyDrawn = drawnOption.getValue();
      if (onlyDrawn && !event.getModule().isDrawn()) {
         return;
      }

      MutableComponent message = Component.empty();
      Module toggledModule = event.getModule();
      if (toggledModule.isToggled()) {
         String enabledOpening = "[";
         message.append(Component.literal(enabledOpening).withStyle(lambdaStyle -> {

            Color lambdaToggleColor = this.moduleToggleColor(true);
            Color lambdaDarkColor = lambdaToggleColor.darker();
            int lambdaColorArgb = lambdaDarkColor.hashCode();
            return lambdaStyle.withColor(lambdaColorArgb);
         }));
         String enabledSign = "+";
         message.append(Component.literal(enabledSign).withStyle(lambdaStyle -> {

            Color lambdaToggleColor = this.moduleToggleColor(true);
            int lambdaColorArgb = lambdaToggleColor.hashCode();
            return lambdaStyle.withColor(lambdaColorArgb);
         }));

         String enabledClosing = "] ";
         message.append(Component.literal(enabledClosing).withStyle(lambdaStyle -> {

            Color lambdaToggleColor = this.moduleToggleColor(true);
            Color lambdaDarkColor = lambdaToggleColor.darker();
            int lambdaColorArgb = lambdaDarkColor.hashCode();
            return lambdaStyle.withColor(lambdaColorArgb);
         }));
      } else {
         String disabledOpening = "[";
         message.append(Component.literal(disabledOpening).withStyle(lambdaStyle -> {

                  Color lambdaToggleColor = this.moduleToggleColor(false);
                  Color lambdaDarkColor = lambdaToggleColor.darker();
                  int lambdaColorArgb = lambdaDarkColor.hashCode();
                  return lambdaStyle.withColor(lambdaColorArgb);
               }));
         String disabledSign = "-";
         message.append(Component.literal(disabledSign).withStyle(lambdaStyle -> {

            Color lambdaToggleColor = this.moduleToggleColor(false);
            int lambdaColorArgb = lambdaToggleColor.hashCode();
            return lambdaStyle.withColor(lambdaColorArgb);
         }));

         String disabledClosing = "] ";
         message.append(Component.literal(disabledClosing).withStyle(lambdaStyle -> {

            Color lambdaToggleColor = this.moduleToggleColor(false);
            Color lambdaDarkColor = lambdaToggleColor.darker();
            int lambdaColorArgb = lambdaDarkColor.hashCode();
            return lambdaStyle.withColor(lambdaColorArgb);
         }));
      }

      AliasRegistry aliasRegistry = ClientContext.aliasRegistry;
      Module aliasModule = event.getModule();
      String moduleName = AliasRegistry.getModuleAlias(aliasRegistry, aliasModule);
      message.append(Component.literal(moduleName));
      ClientContext.taskScheduler.scheduleRunTickTask(() -> {
            Module lambdaSignatureModule = event.getModule();
            ChatUtils.printChat(message, ChatUtils.moduleSignature(lambdaSignatureModule));
         });
   }

   @EventHandler
   private void onPlayerTotem(PlayerTotemEvent event) {
      LocalPlayer localPlayer = ClientAccess.zioMinecraft.player;
      boolean self = localPlayer == event.getPlayer();
      Setting<Boolean> popsOption = this.totemPopsSetting;
      Boolean notifyPops = popsOption.getValue();
      if (!notifyPops) {
         return;
      }

      if (self) {
         Setting<Boolean> selfOption = this.selfSetting;
         Boolean notifySelf = selfOption.getValue();
         if (!notifySelf) {
            return;
         }
      }

      String displayName;
      if (self) {

         displayName = "You";
      } else {
         displayName = event.getName();
      }

      String playerName = displayName;
      int popCount = event.getPopCount();
      PlayerTotemEvent kindEvent = event;
      if (PlayerTotemEvent.Kind.TOTEM_POP != kindEvent.getKind()) {
         this.printTotemNotification(playerName, " has died after popping ", popCount);
         return;
      }

      String popVerb;
      if (self) {

         popVerb = " have";
      } else {

         popVerb = " has";
      }

      this.printTotemNotification(playerName, popVerb + " popped ", popCount);
      SocialRegistry friendRegistry = ClientContext.socialRegistry;

      if (friendRegistry.isFriendName(playerName) || self) {
         return;
      }

      SocialRegistry enemyRegistry = ClientContext.socialRegistry;

      if (!enemyRegistry.isEnemyName(playerName)) {
         Setting<Boolean> enemiesOption = this.onlyEnemiesSetting;
         Boolean onlyEnemies = enemiesOption.getValue();
         if (onlyEnemies) {
            return;
         }
      }

      Setting<Boolean> chatOption = this.sayChatSetting;
      Boolean sayChat = chatOption.getValue();
      if (!sayChat || this.totemMessages.isEmpty()) {
         return;
      }

      ClientAccess.executorService.submit(() -> {
            try {
               Setting<Float> lambdaDelayOption = this.delaySetting;
               Float lambdaDelay = lambdaDelayOption.getValue();
               if (lambdaDelay > 0.0F) {
                  Setting<Float> lambdaSleepDelayOption = this.delaySetting;
                  float lambdaDelaySeconds = lambdaSleepDelayOption.getValue();

                  long lambdaDelayMillis = (long)(lambdaDelaySeconds * 1e+03F);
                  Random lambdaJitterRandom = this.totemRandom;

                  long lambdaSleepMillis = lambdaDelayMillis + lambdaJitterRandom.nextLong(500L);
                  Thread.sleep(lambdaSleepMillis);
               }

               String lambdaChatMessage = null;
               Setting<Boolean> lambdaRandomOption = this.randomSetting;
               Boolean lambdaRandomOrder = lambdaRandomOption.getValue();
               if (lambdaRandomOrder) {
                  if (this.totemMessages.size()
                     == 1) {
                     lambdaChatMessage = this.totemMessages.get(0);
                  } else {
                     while (lambdaChatMessage == null) {
                        Random lambdaRandomSource = this.totemRandom;
                        int lambdaMessageCount = this.totemMessages.size();
                        int lambdaRandomIndex = lambdaRandomSource.nextInt(lambdaMessageCount);
                        if (this.totemMessageIndex != lambdaRandomIndex) {
                           this.totemMessageIndex = lambdaRandomIndex;
                           lambdaChatMessage = this.totemMessages.get(lambdaRandomIndex);
                        }
                     }
                  }
               } else {
                  lambdaChatMessage = this.totemMessages.get(this.totemMessageIndex);
                  this.totemMessageIndex = (
                        this.totemMessageIndex + 1
                     )
                     % this.totemMessages.size();
               }

               lambdaChatMessage = lambdaChatMessage.replace("{name}", playerName);

               lambdaChatMessage = lambdaChatMessage.replace("{totems}", Integer.toString(popCount));

               ChatUtils.sendChatOrCommand(lambdaChatMessage);
            } catch (Exception lambdaIgnored) {
            }
         });
   }

   private Color moduleToggleColor(boolean enabled) {
      return enabled ? new Color(0, 190, 50) : new Color(200, 0, 0);
   }

   public static boolean shouldNotifyCommands(NotificationsModule notifications) {
      return notifications.commandsSetting.getValue();
   }

   private void reloadTotemMessages() {
      try {
         Path configuredPath = ConfigurationManager.ROOT_DIRECTORY;
         Setting<String> pathOption = this.pathSetting;
         configuredPath = configuredPath.resolve(pathOption.getValue());
         Path messagePath = dev.zio.client.utils.FileUtils.findExistingWithSuffixes(configuredPath, ".txt");
         File messageFile = messagePath.toFile();
         if (!messageFile.exists()) {

            throw new RuntimeException("Invalid totem pop file path");
         }

         this.totemMessageIndex = 0;
         this.totemMessages.clear();
         List<String> messages = this.totemMessages;
         File messageReadFile = messagePath.toFile();
         Charset charset = StandardCharsets.UTF_8;
         messages.addAll(FileUtils.readLines(messageReadFile, charset));
      } catch (Exception exception) {
         Setting<Boolean> chatOption = this.sayChatSetting;
         Boolean sayChat = chatOption.getValue();
         if (sayChat) {

            MutableComponent errorMessage = Component.literal("Failed to open totem pop file")
               .withStyle(style -> style.withColor(ChatFormatting.RED));
            String errorDescription = exception.toString();
            int errorHash = errorDescription.hashCode();

            ChatUtils.printChat(errorMessage, ChatUtils.createSignature((errorHash)), ChatUtils.MessagePriority.LOW);
            exception.printStackTrace();
         }
      }
   }

   public int getBracketColor() {
      Color bracketColor = this.bracketsSetting.getValue();
      return bracketColor.hashCode();
   }

   public int getWatermarkColor() {
      Color watermarkColor = this.watermarkSetting.getValue();
      return watermarkColor.hashCode();
   }

   public NotificationsModule() {
      super("Notifications", "Lets you know about what's going on in the chat.", ModuleCategory.CLIENT, new String[0]);
      this.watermarkSetting = this.add(new ColorSetting(("Watermark"), new Color(-4351489, true)));
      this.bracketsSetting = this.add(new ColorSetting("Brackets", new Color(-4351489, true)));
      this.commandsSetting = this.add(new BooleanSetting(("Commands"), true));
      BooleanSetting modulesOption = new BooleanSetting("Modules", true);
      modulesOption.markVisibilityDependency();
      this.modulesSetting = this.add(modulesOption);
      this.onlyDrawnSetting = this.add(new BooleanSetting("OnlyDrawn", false)
         .dependsOn(this.modulesSetting));
      BooleanSetting popsOption = new BooleanSetting("TotemPops", false);
      popsOption.markVisibilityDependency();
      this.totemPopsSetting = this.add(popsOption);
      this.selfSetting = this.add(new BooleanSetting("Self", false)
         .dependsOn(this.totemPopsSetting));
      Predicate primaryVisibility = ignored -> this.totemPopsSetting.isExpanded();
      this.primarySetting = this.add(new ColorSetting("Primary", new Color(-2828, true), primaryVisibility));
      Predicate secondaryVisibility = ignored -> this.totemPopsSetting.isExpanded();
      this.secondarySetting = this.add(new ColorSetting("Secondary", new Color(-4351489, true), secondaryVisibility));
      Predicate sayChatVisibility = ignored -> this.totemPopsSetting.isExpanded();
      BooleanSetting sayChatOption = new BooleanSetting("SayChat", false, sayChatVisibility);
      sayChatOption.markVisibilityDependency();
      this.sayChatSetting = this.add(sayChatOption);
      Predicate pathVisibility = ignored -> this.totemPopsSetting.isExpanded() && this.sayChatSetting.isExpanded();
      this.pathSetting = this.add(new StringSetting(("Path"), "totempop.txt", pathVisibility));
      Predicate delayVisibility = ignored -> this.totemPopsSetting.isExpanded() && this.sayChatSetting.isExpanded();
      NumberSetting<Float> delayOption = new NumberSetting<>("Delay", 0.0F, 0.0F, 15.0F, delayVisibility);
      delayOption.withUnit(("s"));
      this.delaySetting = this.add(delayOption);
      this.onlyEnemiesSetting = this.add(new BooleanSetting("OnlyEnemies", false)
         .dependsOn(this.totemPopsSetting, this.sayChatSetting));
      Predicate randomVisibility = ignored -> this.totemPopsSetting.isExpanded() && this.sayChatSetting.isExpanded();
      this.randomSetting = this.add(new BooleanSetting("Random", true, randomVisibility));
      Predicate refreshVisibility = ignored -> this.totemPopsSetting.isExpanded() && this.sayChatSetting.isExpanded();
      this.refreshSetting = this.add(new BooleanSetting("Refresh", false, refreshVisibility));
      this.totemMessageIndex = 0;
      this.totemMessages = new ArrayList<>();
      this.totemRandom = new Random();

      this.setDrawn(false);
      this.sayChatSetting.setChangeCallback(() -> {
         Setting<Boolean> lambdaChatOption = this.sayChatSetting;
         Boolean lambdaSayChat = lambdaChatOption.getValue();
         if (lambdaSayChat) {
            this.reloadTotemMessages();
         }
      });
      this.refreshSetting.setChangeCallback(() -> {
         Setting<Boolean> lambdaRefreshOption = this.refreshSetting;
         Boolean lambdaRefreshEnabled = lambdaRefreshOption.getValue();
         if (lambdaRefreshEnabled) {
            this.refreshSetting.setValue(false);
            this.reloadTotemMessages();
         }
      });
      this.randomSetting.setChangeCallback(() -> this.totemMessageIndex = 0);
      this.pathSetting.setChangeCallback(this::reloadTotemMessages);
   }

}
