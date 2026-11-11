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
import dev.zio.client.events.HudRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.utils.FloatAnimation;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.SocialRole;

import com.mojang.authlib.GameProfile;
import java.awt.Color;
import java.util.Comparator;
import java.util.function.Function;
import net.minecraft.Optionull;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

public class ExtraTabModule extends Module {
   public final Setting<DisplayChoice> displaySetting;
   public final Setting<LatencyChoice> latencySetting;
   public final Setting<Integer> limitSetting;
   public final Setting<Float> scaleSetting;
   public final Setting<Boolean> coloredSetting;
   public final Setting<Boolean> smoothSetting;
   public final Setting<Boolean> betterSkinSetting;
   public final Setting<Boolean> highlightSetting;
   public final Setting<Color> selfSetting;
   public final Setting<Boolean> socialSetting;
   public final Setting<Boolean> sortSetting;
   public final Setting<ModeChoice> modeSetting;
   public final Setting<Boolean> reverseSetting;
   private final FloatAnimation openAnimation;

   public float getTabScale() {
      if (!this.isToggled()) {

         return 1.0F;
      } else {
         return this.scaleSetting.getValue();
      }
   }

   public float getOpenProgress() {

      float remainderBase = 1.0F;
      FloatAnimation animationSnapshot = this.openAnimation;

      double remainderCapture = remainderBase - animationSnapshot.getValue();

      return (float)((1.0D) - Math.pow((remainderCapture), 5.0D));
   }

   public Comparator createPlayerComparator() {
      Comparator<PlayerInfo> comparator = Comparator.comparingInt((PlayerInfo spectatorInfo) -> {
         int spectatorWeight = spectatorInfo.getGameMode() == GameType.SPECTATOR ? 1 : 0;
         Setting<Boolean> spectatorReverseSnapshot = this.reverseSetting;
         Boolean spectatorReverse = spectatorReverseSnapshot.getValue();
         if (spectatorReverse) {
            spectatorWeight *= -1;
         }

         return spectatorWeight;
      }).thenComparing(teamInfo -> {
         PlayerTeam team = teamInfo.getTeam();
         Function<PlayerTeam, String> teamName = PlayerTeam::getName;
         return Optionull.mapOrDefault(team, teamName, (""));
      }).thenComparing(this::getSortWeight).thenComparing(nameInfo -> {
         GameProfile profile = nameInfo.getProfile();
         return profile.name();
      }, String::compareToIgnoreCase);
      Setting<Boolean> reverseSnapshot = this.reverseSetting;
      Boolean reverse = reverseSnapshot.getValue();
      if (reverse) {
         comparator = comparator.reversed();
      }

      return comparator;
   }

   @EventHandler
   private void updateOpenAnimation(HudRenderEvent event) {
      FloatAnimation animationSnapshot = this.openAnimation;
      Setting<Boolean> smoothSnapshot = this.smoothSetting;
      Boolean smoothEnabled = smoothSnapshot.getValue();
      boolean openTarget = false;
      if (smoothEnabled) {
         KeyMapping tabKey = ClientAccess.zioMinecraft.options.keyPlayerList;
         if (tabKey.isDown()) {
            Minecraft minecraft = ClientAccess.zioMinecraft;
            openTarget = !minecraft.isLocalServer();
         }
      }

      animationSnapshot.setTarget(openTarget, 200L);
   }

   int getSortWeight(PlayerInfo playerInfo) {
      Setting<ModeChoice> modeSnapshot = this.modeSetting;
      ExtraTabModule.ModeChoice mode = modeSnapshot.getValue();
      return switch (mode.ordinal()) {
         case 0 -> 0;
         case 1 -> {
            SocialRegistry socialManagerCapture = ClientContext.socialRegistry;
            GameProfile socialProfile = playerInfo.getProfile();
            String socialName = socialProfile.name();
            SocialRole socialType = socialManagerCapture.getRole(socialName);
            int socialWeight = 0;
            if (socialType == SocialRole.FRIEND) {
               socialWeight = -1;
            }

            if (socialType == SocialRole.ENEMY) {

               socialWeight = -2;
            }

            yield socialWeight;
         }
         case 2 -> -playerInfo.getLatency();
         case 3 -> {
            Font fontCapture = ClientAccess.zioMinecraft.font;
            GameProfile widthProfile = playerInfo.getProfile();
            String widthName = widthProfile.name();
            yield -fontCapture.width(widthName);
         }
         default -> throw new MatchException(null, null);
      };
   }

   public ExtraTabModule() {
      super("ExtraTab", "Allows you to customize your TAB-screen.", ModuleCategory.MISC, new String[0]);
      this.displaySetting = this.add(new EnumSetting<>("Display", DisplayChoice.ALL));
      this.latencySetting = this.add(new EnumSetting<>("Latency", LatencyChoice.VANILLA));
      this.limitSetting = this.add(new NumberSetting<>("Limit", 500, 10, 500));
      this.scaleSetting = this.add(new NumberSetting<>("Scale", 1.0F, 0.5F, 1.5F));
      Predicate<Object> coloredVisible = ignored -> this.latencySetting.getValue() == LatencyChoice.TEXT;
      this.coloredSetting = this.add(new BooleanSetting("Colored", true, coloredVisible));
      this.smoothSetting = this.add(new BooleanSetting("Smooth", false));
      this.betterSkinSetting = this.add(new BooleanSetting("BetterSkin", true));
      BooleanSetting highlight = new BooleanSetting("Highlight", true);
      highlight.markVisibilityDependency();
      this.highlightSetting = this.add(highlight);
      Predicate<Object> selfVisible = ignored -> this.highlightSetting.isExpanded();
      this.selfSetting = this.add(new ColorSetting("Self", new Color(-65281, true), selfVisible));
      Predicate<Object> socialVisible = ignored -> this.highlightSetting.isExpanded();
      this.socialSetting = this.add(new BooleanSetting("Social", true, socialVisible));
      BooleanSetting sort = new BooleanSetting("Sort", false);
      sort.markVisibilityDependency();
      this.sortSetting = this.add(sort);
      this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.ALPHABET).dependsOn(this.sortSetting));
      this.reverseSetting = this.add(new BooleanSetting("Reverse", false).dependsOn(this.sortSetting));
      this.openAnimation = new FloatAnimation();
      Setting<Integer> limitLabelSetting = this.limitSetting;
      SettingLabelCondition maximumLabelPoint = SettingLabelCondition.MAX;
      limitLabelSetting.setBoundaryLabel("Unlimited", maximumLabelPoint);

      this.setDrawn(false);
   }

   public enum DisplayChoice implements NamedEntry {
      ALL("All"),
      SOCIALS("Socials");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      DisplayChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   public enum LatencyChoice implements NamedEntry {
      VANILLA("Vanilla"),
      TEXT("Text"),
      NONE("None");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      LatencyChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   public enum ModeChoice implements NamedEntry {
      ALPHABET("Alphabet"),
      SOCIALS("Socials"),
      LATENCY("Latency"),
      LENGTH("Length");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
