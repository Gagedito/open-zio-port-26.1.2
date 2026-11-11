/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.ItemUseEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.MultiPlayerGameModeDestroyBlockEvent;
import dev.zio.client.utils.movement.MovementSpeedTracker;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.SocialRole;
import dev.zio.client.utils.math.MathUtils;

import com.mojang.authlib.GameProfile;
import java.awt.Color;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class AnnouncerModule extends Module {
   private final Setting<Boolean> clientSideSetting;
   private final Setting<Boolean> compactSetting;
   private final Setting<Boolean> joinsSetting;
   private final Setting<Boolean> friendsSetting;
   private final Setting<Boolean> enemiesSetting;
   private final Setting<Boolean> othersSetting;
   private final Setting<Boolean> moveSetting;
   private final Setting<Boolean> breakSetting;
   private final Setting<Boolean> eatSetting;
   private final Setting<Double> delaySetting;
   private final ElapsedTimer announcementTimer;
   private double distanceTravelled;
   private int foodConsumed;
   private int blocksBroken;

   private String chooseMovementMessage() {
      String[] messages = {
         "I just flew over {blocks} blocks thanks to https://github.com/spectrumdevs/zio!",
         "Я только что пролетел над {blocks} блоками с помощью https://github.com/spectrumdevs/zio!",
         "https://github.com/spectrumdevs/zio sayesinde {blocks} blok uçtum!",
         "我刚刚用 https://github.com/spectrumdevs/zio 走了 {blocks} 米!",
         "Dank https://github.com/spectrumdevs/zio bin ich gerade über {blocks} Blöcke geflogen!",
         "Jag hoppade precis över {blocks} blocks tack vare https://github.com/spectrumdevs/zio!",
         "Właśnie przeleciałem ponad {blocks} bloki dzięki https://github.com/spectrumdevs/zio!",
         "Es tikko nolidoju {blocks} blokus, paldies https://github.com/spectrumdevs/zio!",
         "Я щойно пролетів понад {blocks} блоками завдяки https://github.com/spectrumdevs/zio!",
         "I just fwew ovew {blocks} bwoccs thanks to https://github.com/spectrumdevs/zio! :3",
         "Ho appena camminato per {blocks} blocchi grazie a https://github.com/spectrumdevs/zio!",
         "עכשיו עפתי {blocks} הודות ל https://github.com/spectrumdevs/zio!",
         "Právě jsem proletěl {blocks} bloků díky https://github.com/spectrumdevs/zio!",
         "Lensin juuri yli {blocks} blokkia https://github.com/spectrumdevs/zio:n ansiosta!",
         "Ravnokar sem preletel {blocks} blokov v zahvali https://github.com/spectrumdevs/zio!",
         "أنا هلق طاير فوق {blocks} بلوكس بفضل https://github.com/spectrumdevs/zio!"
      };
      return messages[ThreadLocalRandom.current().nextInt(messages.length)];
   }

   @Override
   public void onEnable() {
      this.foodConsumed = 0;
      this.blocksBroken = 0;
      this.announcementTimer.reset();
   }

   private void announce(String message) {
      MessageSignature signature = ChatUtils.moduleSignature(this);
      this.scheduleAnnouncement(message, signature);
   }

   private String chooseEatingMessage() {
      String[] messages = {
         "I just ate {amount} {name} thanks to https://github.com/spectrumdevs/zio!",
         "Я только что съел {amount} {name} с помощью https://github.com/spectrumdevs/zio!",
         "Tam olarak {amount} tane {name} yedim. TeŞekkürler https://github.com/spectrumdevs/zio",
         "我刚用 https://github.com/spectrumdevs/zio 吃了 {amount} 个 {name}!",
         "Ich habe gerade {amount} {name} dank https://github.com/spectrumdevs/zio gegessen!",
         "Jag åt precis {amount} {name} tack vare https://github.com/spectrumdevs/zio",
         "Właśnie zjadłem {amount} {name} dzięki https://github.com/spectrumdevs/zio",
         "Es tikko apēdu {amount} {name} ar https://github.com/spectrumdevs/zio spēku!",
         "Я щойно з’їв {amount} {name} завдяки https://github.com/spectrumdevs/zio!",
         "I just ate {amount} {name} thanks to https://github.com/spectrumdevs/zio! ^-^",
         "Ho appena mangiato {amount} {name} grazie a https://github.com/spectrumdevs/zio!",
         "כרגע אכלתי {amount} {name} הודות לhttps://github.com/spectrumdevs/zio!",
         "Právě jsem snědl {amount} {name} díky https://github.com/spectrumdevs/zio",
         "Söin juuri {amount} {name}a https://github.com/spectrumdevs/zio:n ansiosta!",
         "Ravnokar sem pojedel {amount} {name} v zahvali https://github.com/spectrumdevs/zio",
         "أنا هلق أكلت {amount} {name} بفضل https://github.com/spectrumdevs/zio!"
      };
      return messages[ThreadLocalRandom.current().nextInt(messages.length)];
   }

   private String chooseBreakingMessage() {
      String[] messages = {
         "I just destroyed {amount} {name} with the power of https://github.com/spectrumdevs/zio!",
         "Я только что разрушил {amount} {name} с помощью https://github.com/spectrumdevs/zio!",
         "Az önce {amount} tane {name} kırdım. TeŞekkürler https://github.com/spectrumdevs/zio!",
         "我刚刚用 https://github.com/spectrumdevs/zio 破坏了 {amount} {name}!",
         "Ich habe gerade {amount} {name} mit der Kraft von https://github.com/spectrumdevs/zio zerstört!",
         "Jag förstörde precis {amount} {name} tack vare https://github.com/spectrumdevs/zio!",
         "Właśnie zniszczyłem {amount} {name} za pomocą https://github.com/spectrumdevs/zio",
         "Es tikko salauzu {amount} {name} ar spēku https://github.com/spectrumdevs/zio!",
         "Я щойно знищив {amount} {name} за допомогою https://github.com/spectrumdevs/zio!",
         "I just destwoyed {amount} {name} with the powew of https://github.com/spectrumdevs/zio! :3",
         "Ho appena distrutto {amount} {name} grazie al potere di https://github.com/spectrumdevs/zio!",
         "בדיוק חצבתי {amount} {name} בעזרת הכוח של https://github.com/spectrumdevs/zio!",
         "Právě jsem zničil {amount} {name} díky https://github.com/spectrumdevs/zio!",
         "Rikoin juuri {amount} {name}ia https://github.com/spectrumdevs/zio:n ansiosta!",
         "Ravnokar sem uničil {amount} {name} z močjo https://github.com/spectrumdevs/zio",
         "أنا هلق دمّرت {amount} {name} بقوة https://github.com/spectrumdevs/zio!"
      };
      return messages[ThreadLocalRandom.current().nextInt(messages.length)];
   }

   @EventHandler
   private void announceMovement(LocalPlayerTickEvent event) {
      if (!this.isWorldUnavailable()) {
         double previousDistance = this.distanceTravelled;
         MovementSpeedTracker movementTracker = ClientContext.movementSpeedTracker;
         double speed = movementTracker.getCurrentSpeed();

         double distancePerSecond = speed / 3.6D;

         this.distanceTravelled = previousDistance
            + distancePerSecond / 20.0D;
         double distanceSnapshot = this.distanceTravelled;

         if (distanceSnapshot >= 1000.0D) {
            this.distanceTravelled = 0.0;
         }

         Setting<Boolean> moveSettingSnapshot = this.moveSetting;
         Boolean moveValue = moveSettingSnapshot.getValue();
         if (moveValue) {
            distanceSnapshot = this.distanceTravelled;

            if (distanceSnapshot >= 1.0D) {
               ElapsedTimer delayTimer = this.announcementTimer;

               double millisPerSecond = 1000.0D;
               Setting<Double> delaySettingSnapshot = this.delaySetting;
               Double delayValue = delaySettingSnapshot.getValue();
               long delayMillis = (long)(millisPerSecond * delayValue);
               if (delayTimer.hasElapsed(delayMillis)) {
                  String template = this.chooseMovementMessage();

                  DecimalFormat distanceFormat = new DecimalFormat("0.00");
                  double announcedDistance = this.distanceTravelled;
                  String message = template.replace("{blocks}", distanceFormat.format(announcedDistance));

                  this.announce(message);
                  this.distanceTravelled = 0.0;
                  this.announcementTimer.reset();
               }
            }
         }
      }
   }

   private void scheduleAnnouncement(String message, MessageSignature signature) {
      ClientAccess.zioMinecraft.executeIfPossible(() -> {
         Setting<Boolean> clientSideSettingSnapshot = this.clientSideSetting;
         Boolean clientSideValue = clientSideSettingSnapshot.getValue();
         if (clientSideValue) {
            ChatUtils.printChat(Component.literal(message).withStyle(style -> {
               Color color = new Color(
                  161,
                  161,
                  161
               );
               int colorValue = color.hashCode();
               return style.withColor(colorValue);
            }), signature);
         } else {
            ClientPacketListener connection = ClientAccess.zioMinecraft.player.connection;
            connection.sendChat(message);
         }
      });
   }

   @EventHandler
   private void announceEating(
      ItemUseEvent.BeforeFinish event
   ) {
      if (!this.isWorldUnavailable()) {
         int announceCount = MathUtils.randomInt(1, (6));
         Setting<Boolean> eatSettingSnapshot = this.eatSetting;
         Boolean eatValue = eatSettingSnapshot.getValue();
         if (eatValue) {
            if (event.getStack().has(DataComponents.FOOD)) {
               this.foodConsumed++;
               if (this.foodConsumed >= announceCount) {
                  ElapsedTimer timer = this.announcementTimer;

                  double millisPerSecond = 1000.0D;
                  Setting<Double> delaySettingSnapshot = this.delaySetting;
                  Double delayValue = delaySettingSnapshot.getValue();
                  long delayMillis = (long)(millisPerSecond * delayValue);
                  if (timer.hasElapsed(delayMillis)) {
                     String template = this.chooseEatingMessage();

                     int announcedCount = this.foodConsumed;
                     String countedMessage = template.replace("{amount}", String.valueOf(announcedCount));
                     ItemStack finishedStack = event.getStack();
                     Item food = finishedStack.getItem();
                     String message = countedMessage.replace("{name}", food.getName(finishedStack).getString());

                     this.announce(message);
                     this.foodConsumed = 0;
                     timer = this.announcementTimer;

                     timer.reset();
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void announceBlockBreak(MultiPlayerGameModeDestroyBlockEvent event) {
      if (!this.isWorldUnavailable()) {
         int announceCount = MathUtils.randomInt(1, (6));
         this.blocksBroken++;
         Setting<Boolean> breakSettingSnapshot = this.breakSetting;
         Boolean breakValue = breakSettingSnapshot.getValue();
         if (breakValue && this.blocksBroken >= announceCount) {
            ElapsedTimer timer = this.announcementTimer;

            double millisPerSecond = 1000.0D;
            Setting<Double> delaySettingSnapshot = this.delaySetting;
            Double delayValue = delaySettingSnapshot.getValue();
            long delayMillis = (long)(millisPerSecond * delayValue);
            if (timer.hasElapsed(delayMillis)) {
               String[] blockNames = new String[4];
               BlockPos position = event.getPos();

               Block block = BlockInteractionUtils.getBlock(
                  position
               );
               blockNames[0] = block.getName().getString();

               blockNames[1] = "Air";

               blockNames[(2)] = "Bedrock";

               blockNames[3] = "Barrier";

               String template = this.chooseBreakingMessage();

               int announcedCount = this.blocksBroken;
               String countedMessage = template.replace(
                  "{amount}",
                  String.valueOf(announcedCount)
               );

               Random random = new Random();
               int blockNameCount = blockNames.length;
               String message = countedMessage.replace("{name}", blockNames[random.nextInt(blockNameCount)]);

               this.announce(message);
               this.blocksBroken = 0;
               timer = this.announcementTimer;

               timer.reset();
            }
         }
      }
   }

   @EventHandler
   private void resetDelayAfterChat(
      PacketEvent.Send event
   ) {
      if (event.getPacket() instanceof ServerboundChatPacket) {
         this.announcementTimer.reset();
      }
   }

   private boolean shouldAnnounceRelationship(SocialRole relationship) {
      Setting<Boolean> friendsSettingSnapshot = this.friendsSetting;
      Boolean friendsValue = friendsSettingSnapshot.getValue();
      if (!friendsValue && relationship == SocialRole.FRIEND) {
         return false;
      }

      Setting<Boolean> enemiesSettingSnapshot = this.enemiesSetting;
      Boolean enemiesValue = enemiesSettingSnapshot.getValue();
      if (!enemiesValue && relationship == SocialRole.ENEMY) {
         return false;
      }

      Setting<Boolean> othersSettingSnapshot = this.othersSetting;
      Boolean othersValue = othersSettingSnapshot.getValue();
      return othersValue || relationship != null;
   }

   @EventHandler
   private void announcePlayerListChanges(
      PacketEvent.Receive event
   ) {
      Setting<Boolean> joinsSettingSnapshot = this.joinsSetting;
      Boolean joinsValue = joinsSettingSnapshot.getValue();
      if (joinsValue) {
         ElapsedTimer timer = this.announcementTimer;
         Setting<Double> delaySettingSnapshot = this.delaySetting;
         double delaySeconds = delaySettingSnapshot.getValue();
         TimeUnit seconds = TimeUnit.SECONDS;
         if (timer.hasElapsed(delaySeconds, seconds)) {
            if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket joinPacket) {
               Iterator<Entry> joinEntries = joinPacket.newEntries().iterator();

               while (joinEntries.hasNext()) {
                  Entry entry = joinEntries.next();
                  if (entry.listed()) {
                     GameProfile joiningProfile = entry.profile();
                     String joiningName = joiningProfile.name();
                     LocalPlayer localPlayer = ClientAccess.zioMinecraft.player;
                     if (!joiningName.equals(localPlayer.getName().getString())) {
                        SocialRegistry joinRelationships = ClientContext.socialRegistry;

                        SocialRole joiningRelationship = joinRelationships.getRole(
                           joiningName
                        );
                        if (this.shouldAnnounceRelationship(joiningRelationship)) {
                           joinAnnouncement: {
                              Setting<Boolean> clientSideSettingSnapshot = this.clientSideSetting;
                              Boolean clientSideValue = clientSideSettingSnapshot.getValue();
                              if (clientSideValue) {
                                 Setting<Boolean> compactSettingSnapshot = this.compactSetting;
                                 Boolean compactValue = compactSettingSnapshot.getValue();
                                 if (compactValue) {
                                    joiningName = (joiningName) + " joined";
                                    if (joiningRelationship == SocialRole.FRIEND) {
                                       joiningName = (joiningName) + " [friend]";
                                    } else if (joiningRelationship == SocialRole.ENEMY) {
                                       joiningName = (joiningName) + " [enemy]";
                                    }

                                    String compactJoinMessage = joiningName;

                                    MessageSignature compactJoinSignature = ChatUtils.createSignature(-254721);
                                    this.scheduleAnnouncement(compactJoinMessage, compactJoinSignature);
                                    break joinAnnouncement;
                                 }
                              }

                              if (joiningRelationship == SocialRole.FRIEND) {
                                 joiningName = "My friend " + (joiningName);
                              }

                              String joinMessage = joiningName + " has joined the game!";

                              MessageSignature joinSignature = ChatUtils.createSignature(-254721);
                              this.scheduleAnnouncement(joinMessage, joinSignature);
                           }

                           timer = this.announcementTimer;

                           timer.reset();
                        }
                     }
                  }
               }
            }
            if (event.getPacket() instanceof ClientboundPlayerInfoRemovePacket leavePacket) {
               Iterator<UUID> departingIds = leavePacket.profileIds().iterator();

               while (departingIds.hasNext()) {
                  UUID departingId = departingIds.next();
                  ClientPacketListener connection = ClientAccess.zioMinecraft.player.connection;
                  Iterator<PlayerInfo> onlinePlayers = connection.getListedOnlinePlayers().iterator();

                  while (onlinePlayers.hasNext()) {
                     PlayerInfo onlinePlayer = onlinePlayers.next();
                     GameProfile onlineProfile = onlinePlayer.getProfile();
                     if (onlineProfile.id().equals(departingId)) {
                        GameProfile departingProfile = onlinePlayer.getProfile();
                        String departingName = departingProfile.name();
                        SocialRegistry leaveRelationships = ClientContext.socialRegistry;

                        SocialRole departingRelationship = leaveRelationships.getRole(
                           departingName
                        );
                        if (this.shouldAnnounceRelationship(departingRelationship)) {
                           leaveAnnouncement: {
                              Setting<Boolean> leaveClientSideSetting = this.clientSideSetting;
                              Boolean leaveClientSideEnabled = leaveClientSideSetting.getValue();
                              if (leaveClientSideEnabled) {
                                 Setting<Boolean> leaveCompactSetting = this.compactSetting;
                                 Boolean leaveCompactEnabled = leaveCompactSetting.getValue();
                                 if (leaveCompactEnabled) {
                                    departingName = (departingName) + " left";
                                    if (departingRelationship == SocialRole.FRIEND) {
                                       departingName = (departingName) + " [friend]";
                                    } else if (departingRelationship == SocialRole.ENEMY) {
                                       departingName = (departingName) + " [enemy]";
                                    }

                                    String compactLeaveMessage = departingName;

                                    MessageSignature compactLeaveSignature = ChatUtils.createSignature(-254722);
                                    this.scheduleAnnouncement(compactLeaveMessage, compactLeaveSignature);
                                    break leaveAnnouncement;
                                 }
                              }

                              if (departingRelationship == SocialRole.FRIEND) {
                                 departingName = "My friend " + (departingName);
                              }

                              String leaveMessage = departingName + " has left the game!";

                              MessageSignature leaveSignature = ChatUtils.createSignature(-254722);
                              this.scheduleAnnouncement(leaveMessage, leaveSignature);
                           }

                           timer = this.announcementTimer;

                           timer.reset();
                        }
                     }
                  }
               }
            }

            return;
         }
      }
   }

   public AnnouncerModule() {
      super("Announcer", "Lets the others know what you're doing.", ModuleCategory.MISC, new String[0]);
      BooleanSetting clientSideOption = new BooleanSetting("ClientSide", false);
      clientSideOption.markVisibilityDependency();
      this.clientSideSetting = this.add(clientSideOption);
      this.compactSetting = this.add(new BooleanSetting("Compact", false).dependsOn(this.clientSideSetting));
      BooleanSetting joinsOption = new BooleanSetting("Joins", false);
      joinsOption.markVisibilityDependency();
      this.joinsSetting = this.add(joinsOption);
      this.friendsSetting = this.add(new BooleanSetting("Friends", true).dependsOn(this.joinsSetting));
      this.enemiesSetting = this.add(new BooleanSetting("Enemies", true).dependsOn(this.joinsSetting));
      this.othersSetting = this.add(new BooleanSetting("Others", true).dependsOn(this.joinsSetting));
      this.moveSetting = this.add(new BooleanSetting("Move", true));
      this.breakSetting = this.add(new BooleanSetting("Break", true));
      this.eatSetting = this.add(new BooleanSetting("Eat", true));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 10.0, 0.0, 30.0).withUnit("s"));
      this.announcementTimer = new ElapsedTimer();
      Setting<Double> delaySettingSnapshot = this.delaySetting;

      SettingLabelCondition minimumChoice = SettingLabelCondition.MIN;
      delaySettingSnapshot.setBoundaryLabel("None", minimumChoice);

      this.setDrawn(false);
   }

}
