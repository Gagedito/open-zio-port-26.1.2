/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.client;

import dev.zio.client.utils.collections.NamedIdentifier;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.MinecraftTickEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.gui.panels.Panel;
import dev.zio.client.gui.screens.ClickGuiScreen;
import dev.zio.client.gui.screens.ClientScreen;
import dev.zio.client.managers.TaskScheduler;
import dev.zio.client.gui.CursorType;
import dev.zio.client.render.text.LanguageTable;
import dev.zio.client.utils.world.Season;
import dev.zio.client.audio.BuiltinSound;
import dev.zio.client.ZioClient;
import dev.zio.client.modules.Keybind;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.IdentifierSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.platform.Window;
import java.awt.Color;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import org.lwjgl.glfw.GLFW;

public final class UIModule extends Module {
   public static UIModule INSTANCE;
   private final AtomicBoolean panelResetPending = new AtomicBoolean(false);
   private final Setting<Boolean> resetSetting;
   public final Setting<StyleChoice> styleSetting;
   public final Setting<Float> animSpeedSetting;
   public final Setting<Float> scaleSetting;
   public final Setting<Boolean> snowSetting;
   public final Setting<Boolean> gamesSetting;
   public final Setting<Boolean> descriptionsSetting;
   final Setting<LanguageChoice> languageSetting;
   public final Setting<Boolean> settingsSetting;
   public final Setting<Boolean> elementsSetting;
   public final Setting<Boolean> aeroSetting;
   public final Setting<Boolean> elementGradientSetting;
   public final Setting<Boolean> lineSetting;
   public final Setting<Boolean> bindsSetting;
   public final Setting<Boolean> gearSetting;
   public final Setting<Boolean> soundsSetting;
   public final Setting<Boolean> hoverSetting;
   public final Setting<NamedIdentifier> hoverSoundSetting;
   public final Setting<Float> hoverVolumeSetting;
   public final Setting<Boolean> leftClickSetting;
   public final Setting<NamedIdentifier> leftClickSoundSetting;
   public final Setting<Float> leftClickVolumeSetting;
   public final Setting<Boolean> rightClickSetting;
   public final Setting<NamedIdentifier> rightClickSoundSetting;
   public final Setting<Float> rightClickVolumeSetting;
   public final Setting<Boolean> textSetting;
   public final Setting<Color> textColorSetting;
   public final Setting<Color> enabledColorSetting;
   final Setting<Boolean> colorsSetting;
   public final Setting<Color> colorSetting;
   public final Setting<Color> backgroundSetting;
   public final Setting<Color> buttonSetting;
   public final Setting<Color> enabledSetting;
   public final Setting<Color> tintSetting;
   public final Setting<Color> gradientColorSetting;

   @Override
   public void onEnable() {
      if (this.isWorldUnavailable() || ClientAccess.zioMinecraft.screen instanceof ClickGuiScreen) {
         return;
      }
      if (ClientAccess.zioMinecraft.screen instanceof ChatScreen) {
         TaskScheduler guiScheduler = ClientContext.taskScheduler;
         Runnable guiOpenTask = () -> {
            ClickGuiScreen deferredScreen = ClientContext.getClickGui();
            deferredScreen.open();
         };
         guiScheduler.scheduleRotationTask(guiOpenTask, 0);
      } else {
         ClickGuiScreen screen = ClientContext.getClickGui();
         screen.open();
      }
   }

   @Override
   public Keybind getKeybind() {
      Keybind keybind = super.getKeybind();
      if (keybind.mode() != Keybind.Mode.TOGGLE) {
         keybind = keybind.withMode(Keybind.Mode.TOGGLE);
         this.setKeybind(keybind);
      }
      return keybind;
   }

   @Override
   public void onDisable() {
      if (ClientAccess.zioMinecraft.screen instanceof ClickGuiScreen) {
         ClientContext.getClickGui()
            .closeWithTransition();
      }
   }

   @EventHandler
   private void onMinecraftTick(MinecraftTickEvent event) {
      Minecraft minecraft = ClientAccess.zioMinecraft;
      Window window = minecraft.getWindow();
      long windowHandle = window.handle();
      if (GLFW.glfwGetMouseButton(windowHandle, 0) == 1) {
         return;
      }
      AtomicBoolean pendingReset = this.panelResetPending;
      if (!pendingReset.get()) {
         return;
      }
      ClickGuiScreen screen = ClientContext.getClickGui();
      screen.resetPanelPositions();
      this.resetSetting.setValue(true);
      this.panelResetPending.set(false);
   }

   @EventHandler
   private void onLocalPlayerTick(LocalPlayerTickEvent event) {
      if (!(ClientAccess.zioMinecraft.screen instanceof ClientScreen)) {
         this.toggle();
      }
   }

   @EventHandler
   private static void onCursorTick(MinecraftTickEvent event) {
      if (!(ClientAccess.zioMinecraft.screen instanceof ClientScreen)) {
         CursorType cursor = CursorType.STANDARD;
         cursor.apply();
      }
   }

   @EventHandler
   private void onPacketReceive(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundDisconnectPacket) {
         this.disable();
      }
   }

   public float getAnimationSpeed() {
      return this.animSpeedSetting.getValue();
   }

   public UIModule() {
      super("UI", "Displays the client's click gui.", ModuleCategory.CLIENT, new String[0]);
      this.resetSetting = this.add(new BooleanSetting(("Reset"), true));
      this.styleSetting = this.add(new EnumSetting<>(("Style"), UIModule.StyleChoice.PLAIN));
      this.animSpeedSetting = this.add(new NumberSetting<>("AnimSpeed", 1.0F, 0.5F, 2.5F));
      this.scaleSetting = this.add(new NumberSetting<>("Scale", 1.0F, 0.1F, 2.0F));
      this.snowSetting = this.add(new BooleanSetting("Snow", true));
      this.gamesSetting = this.add(new BooleanSetting("Games", true));
      BooleanSetting descriptionsOption = new BooleanSetting("Descriptions", true);
      descriptionsOption.markVisibilityDependency();
      this.descriptionsSetting = this.add(descriptionsOption);
      this.languageSetting = this.add(new EnumSetting<>("Language", UIModule.LanguageChoice.ENGLISH)
         .dependsOn(this.descriptionsSetting));
      this.settingsSetting = this.add(new BooleanSetting("Settings", true)
         .dependsOn(this.descriptionsSetting));
      BooleanSetting elementsOption = new BooleanSetting("Elements", true);
      elementsOption.markAsGroup();
      elementsOption.markVisibilityDependency();
      this.elementsSetting = this.add(elementsOption);
      this.aeroSetting = this.add(new BooleanSetting("Aero", true)
         .dependsOn(this.elementsSetting));
      this.elementGradientSetting = this.add(new BooleanSetting("Gradient", true)
         .dependsOn(this.elementsSetting));
      Predicate<Object> lineVisibility = ignored -> this.elementsSetting.isExpanded();
      this.lineSetting = this.add(new BooleanSetting("Line", true, lineVisibility));
      Predicate<Object> bindsVisibility = ignored -> this.elementsSetting.isExpanded();
      this.bindsSetting = this.add(new BooleanSetting("Binds", false, bindsVisibility));
      Predicate<Object> gearVisibility = ignored -> this.elementsSetting.isExpanded();
      this.gearSetting = this.add(new BooleanSetting("Gear", true, gearVisibility));
      BooleanSetting soundsOption = new BooleanSetting("Sounds", true);
      soundsOption.markAsGroup();
      soundsOption.markVisibilityDependency();
      this.soundsSetting = this.add(soundsOption);
      Predicate<Object> hoverVisibility = ignored -> this.soundsSetting.isExpanded();
      BooleanSetting hoverOption = new BooleanSetting("Hover", true, hoverVisibility);
      hoverOption.markVisibilityDependency();
      this.hoverSetting = this.add(hoverOption);
      String hoverSoundKey = "Sound";
      this.hoverSoundSetting = this.add(new IdentifierSetting(hoverSoundKey, BuiltinSound.HOVER.getIdentifier())
         .dependsOn(this.soundsSetting, this.hoverSetting));
      NumberSetting<Float> hoverVolumeOption = new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F);
      this.hoverVolumeSetting = this.add(hoverVolumeOption.dependsOn(this.soundsSetting, this.hoverSetting));
      Predicate<Object> leftClickVisibility = ignored -> this.soundsSetting.isExpanded();
      BooleanSetting leftClickOption = new BooleanSetting("LeftClick", true, leftClickVisibility);
      leftClickOption.markVisibilityDependency();
      this.leftClickSetting = this.add(leftClickOption);
      this.leftClickSoundSetting = this.add(new IdentifierSetting("Sound", BuiltinSound.CLICK.getIdentifier())
         .dependsOn(this.soundsSetting, this.leftClickSetting));
      NumberSetting<Float> leftClickVolumeOption = new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F);
      this.leftClickVolumeSetting = this.add(leftClickVolumeOption.dependsOn(this.soundsSetting, this.leftClickSetting));
      Predicate<Object> rightClickVisibility = ignored -> this.soundsSetting.isExpanded();
      BooleanSetting rightClickOption = new BooleanSetting("RightClick", true, rightClickVisibility);
      rightClickOption.markVisibilityDependency();
      this.rightClickSetting = this.add(rightClickOption);
      this.rightClickSoundSetting = this.add(new IdentifierSetting("Sound", BuiltinSound.RCLICK.getIdentifier())
         .dependsOn(this.soundsSetting, this.rightClickSetting));
      NumberSetting<Float> rightClickVolumeOption = new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F);
      this.rightClickVolumeSetting = this.add(rightClickVolumeOption.dependsOn(this.soundsSetting, this.rightClickSetting));
      BooleanSetting textOption = new BooleanSetting("Text", false);
      textOption.markAsGroup();
      textOption.markVisibilityDependency();
      this.textSetting = this.add(textOption);
      String textColorKey = "TextColor";
      this.textColorSetting = this.add(new ColorSetting(textColorKey, new Color(-1973791, true))
         .dependsOn(this.textSetting));
      this.enabledColorSetting = this.add(new ColorSetting("EnabledColor", new Color(-1, true))
         .dependsOn(this.textSetting));
      BooleanSetting colorsOption = new BooleanSetting("Colors", false);
      colorsOption.markAsGroup();
      colorsOption.markVisibilityDependency();
      this.colorsSetting = this.add(colorsOption);
      Predicate<Object> colorVisibility = ignored -> this.colorsSetting.isExpanded();
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-2100849420, true), colorVisibility));
      Predicate<Object> backgroundVisibility = ignored -> this.colorsSetting.isExpanded();
      this.backgroundSetting = this.add(new ColorSetting("Background", new Color(516396276, true), backgroundVisibility));
      Predicate<Object> buttonVisibility = ignored -> this.colorsSetting.isExpanded();
      this.buttonSetting = this.add(new ColorSetting("Button", new Color(1942459636, true), buttonVisibility));
      Predicate<Object> enabledVisibility = ignored -> this.colorsSetting.isExpanded();
      this.enabledSetting = this.add(new ColorSetting("Enabled", new Color(1523029236, true), enabledVisibility));
      Predicate<Object> tintVisibility = ignored -> this.colorsSetting.isExpanded();
      this.tintSetting = this.add(new ColorSetting("Tint", new Color(1679822128, true), tintVisibility));
      Predicate<Object> gradientColorVisibility = ignored -> this.colorsSetting.isExpanded();
      this.gradientColorSetting = this.add(new ColorSetting("Gradient", new Color(264738036, true), gradientColorVisibility));
      Class<UIModule> eventOwner = UIModule.class;
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribeStatic(eventOwner);

      this.ignoreState();

      this.setDrawn(false);
      Function<Keybind, Keybind> keybindTransform = keybind -> {
         return keybind.withCode(344);
      };
      this.modifyKeybind(keybindTransform);
      INSTANCE = this;
      Setting<Boolean> gamesCallbackSetting = this.gamesSetting;
      Runnable gamesChangeCallback = () -> {
         ClickGuiScreen screen = ClientContext.clickGui;

         if (!screen.isGamePanelUnlocked()) {
            screen = ClientContext.clickGui;

            ArrayList gamePanels = screen.getPanels();
            Predicate<Panel> gamePanelPredicate = panel -> panel.getName().equals("Game");
            gamePanels.removeIf(gamePanelPredicate);
            Setting<Boolean> games = this.gamesSetting;
            if (games.getValue()) {
               screen = ClientContext.clickGui;

               screen.addGamePanel();
            }
         }
      };
      gamesCallbackSetting.setChangeCallback(gamesChangeCallback);
      Setting<Boolean> resetCallbackSetting = this.resetSetting;
      Runnable resetChangeCallback = () -> {
         Setting<Boolean> reset = this.resetSetting;
         if (!(reset.getValue())) {
            this.resetSetting.setValue(true);
            if (ClientContext.clickGui != null) {

               ClickGuiScreen screen = ClientContext.getClickGui();

               screen.resetPanelPositions();
            }
         }
      };
      resetCallbackSetting.setChangeCallback(resetChangeCallback);
      Setting<StyleChoice> styleCallbackSetting = this.styleSetting;
      Runnable styleChangeCallback = () -> this.panelResetPending.set(true);
      styleCallbackSetting.setChangeCallback(styleChangeCallback);
      Setting<LanguageChoice> languageCallbackSetting = this.languageSetting;
      Runnable languageChangeCallback = () -> {
         LanguageTable tableCapture = ClientContext.languageTable;
         Setting<LanguageChoice> language = this.languageSetting;
         UIModule.LanguageChoice selectedLanguage = language.getValue();
         InputStream languageStream = selectedLanguage.openStream();
         tableCapture.load(languageStream);
         if (selectedLanguage == LanguageChoice.CHINESE && FontsModule.INSTANCE.isToggled()) {
            FontsModule.INSTANCE.validateFont(false);
         }
      };
      languageCallbackSetting.setChangeCallback(languageChangeCallback);
      this.hoverSoundSetting.setConfigName("HoverSound");
      this.hoverVolumeSetting.setConfigName("HoverVolume");
      this.leftClickSoundSetting.setConfigName("LeftClickSound");
      this.leftClickVolumeSetting.setConfigName("LeftClickVolume");
      this.rightClickSoundSetting.setConfigName("RightClickSound");
      this.rightClickVolumeSetting.setConfigName("RightClickVolume");
      this.backgroundSetting.setConfigName("BgColor");
      this.buttonSetting.setConfigName("BgButton");
      this.enabledSetting.setConfigName("BgEnabled");
      this.gradientColorSetting.setConfigName("GradientColor");
      Season winterCapture = Season.WINTER;
      LocalDate today = LocalDate.now();
      int month = today.getMonthValue();
      if (!winterCapture.containsMonth(month)) {
         this.snowSetting.setValue(false);
         this.unregister(this.snowSetting);
      }
   }

   public enum LanguageChoice implements NamedEntry {
      ENGLISH("English", "en_us"),
      CHINESE("Chinese", "zh_cn");

      private final String displayName;
      private final String languageCode;

      public InputStream openStream() {
         String languageCode = this.languageCode;
         return ZioClient.class.getResourceAsStream("/assets/zio/lang/%s.ini".formatted(languageCode));
      }

      @Override
      public String getName() {
         return this.displayName;
      }

      LanguageChoice(String displayName, String languageCode) {
         this.displayName = displayName;
         this.languageCode = languageCode;
      }
   }

   public enum StyleChoice implements NamedEntry {
      PLAIN("Plain"),
      SLIM("Slim");

      private final String displayName;

      public int getHeightPadding() {
         return this == SLIM ? 3 : 4;
      }

      public int getWidthPadding() {
         return this == SLIM ? 4 : 7;
      }

      @Override
      public String getName() {
         return this.displayName;
      }

      StyleChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
