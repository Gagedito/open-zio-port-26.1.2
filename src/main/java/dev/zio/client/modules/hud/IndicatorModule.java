/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LivingEntityAddEffectEvent;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.render.ResizableHudWidgetRenderer;
import dev.zio.client.modules.HudWidgetModule;
import dev.zio.client.modules.movement.WarpModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.utils.FloatAnimation;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class IndicatorModule extends HudWidgetModule {
   private static final WarpModule warp = ClientContext.getModule(WarpModule.class);
   private final Setting<Boolean> warpSetting;
   private final Setting<Boolean> resistanceSetting;
   private final Setting<Boolean> strengthSetting;
   private final Setting<RenderMode> renderSetting;
   private final Setting<Layout> layoutSetting;
   private final Setting<Boolean> vanillaSetting;
   private final Setting<Float> widthSetting;
   private final Setting<Boolean> prefixSetting;
   public final Setting<Boolean> hideSetting;
   private final FloatAnimation warpChargeAnimation = new FloatAnimation();
   private final EffectIndicator resistance = new EffectIndicator("Resistance", MobEffects.RESISTANCE, 2);
   private final EffectIndicator strength = new EffectIndicator("Strength", MobEffects.STRENGTH, 0);
   private final Map<DisplayMode, FloatAnimation> visibilityAnimations = new EnumMap<>(DisplayMode.class);

   public IndicatorModule() {
      super("Indicator", new String[0]);
      this.renderSetting = this.add(new EnumSetting<>("Render", RenderMode.PROGRESSBAR));
      this.layoutSetting = this.add(new EnumSetting<>("Layout", Layout.VERTICAL));
      this.vanillaSetting = this.add(new BooleanSetting("Vanilla", false,
         ignored -> this.renderSetting.getValue() == RenderMode.PROGRESSBAR));
      this.widthSetting = this.add(new NumberSetting<>("Width", 32.0F, 16.0F, 48.0F,
         ignored -> this.renderSetting.getValue() == RenderMode.PROGRESSBAR));
      this.prefixSetting = this.add(new BooleanSetting("Prefix", true,
         ignored -> this.renderSetting.getValue() == RenderMode.TEXT));
      this.hideSetting = this.add(new BooleanSetting("Hide", false));
      Setting<Boolean> indicators = this.add(new BooleanSetting("Indicators", false).markAsGroup());
      this.warpSetting = this.add(new BooleanSetting("Warp", true).dependsOn(indicators));
      this.resistanceSetting = this.add(new BooleanSetting("Resistance", false).dependsOn(indicators));
      this.strengthSetting = this.add(new BooleanSetting("Strength", false).dependsOn(indicators));
      for (DisplayMode mode : DisplayMode.values()) {
         this.visibilityAnimations.put(mode, new FloatAnimation());
      }
      this.setRenderer(new ResizableHudWidgetRenderer(this));
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json instanceof JsonObject module && module.get("settings") instanceof JsonObject settings
         && (settings.has("Display") || settings.get("Indicators") instanceof JsonArray)) {
         JsonObject migrated = module.deepCopy();
         JsonArray selected = new JsonArray();
         if (settings.get("Indicators") instanceof JsonArray array) selected = array;
         else selected.add(settings.get("Display"));
         JsonObject migratedSettings = migrated.getAsJsonObject("settings");
         migratedSettings.remove("Indicators");
         for (DisplayMode mode : DisplayMode.values()) {
            migratedSettings.addProperty(mode.getName(), false);
         }
         for (JsonElement entry : selected) {
            DisplayMode matched = null;
            for (DisplayMode mode : DisplayMode.values()) {
               if (mode.getName().equalsIgnoreCase(entry.getAsString())) matched = mode;
            }
            if (matched == null) throw new IllegalArgumentException("Unknown indicator: " + entry.getAsString());
            migratedSettings.addProperty(matched.getName(), true);
         }
         super.fromJson(migrated);
      } else {
         super.fromJson(json);
      }
   }

   private List<DisplayMode> selectedIndicators() {
      List<DisplayMode> selected = new ArrayList<>();
      if (this.warpSetting.getValue()) selected.add(DisplayMode.WARP);
      if (this.resistanceSetting.getValue()) selected.add(DisplayMode.RESISTANCE);
      if (this.strengthSetting.getValue()) selected.add(DisplayMode.STRENGTH);
      return selected;
   }

   private DisplayData getDisplayData(DisplayMode mode) {
      return switch (mode) {
         case WARP -> this.getWarpData();
         case RESISTANCE -> this.resistance.getData();
         case STRENGTH -> this.strength.getData();
      };
   }

   private DisplayData getWarpData() {
      int requiredCharge = warp.chargeSetting.getValue();
      float progress = requiredCharge <= 0 ? 1.0F
         : Mth.clamp(this.warpChargeAnimation.getValue() / requiredCharge, 0.0F, 1.0F);
      float eased = progress < 0.5F ? 2.0F * progress * progress
         : (float)(1.0D - Math.pow(-2.0F * progress + 2.0F, 2.0D) / 2.0D);
      return new DisplayData("Warp", String.valueOf(warp.getCharge()), eased);
   }

   private float indicatorWidth(DisplayData data) {
      if (this.renderSetting.getValue() == RenderMode.TEXT) {
         return FontRenderer.INSTANCE.getWidth(data.formatText(this.prefixSetting.getValue()));
      }
      return this.widthSetting.getValue() + 0.5F;
   }

   private float indicatorHeight() {
      if (this.renderSetting.getValue() == RenderMode.TEXT) {
         return FontRenderer.INSTANCE.getHeight();
      }
      return this.vanillaSetting.getValue() ? 5.0F : 3.0F;
   }

   @Override
   public float[] calculateDimensions() {
      if (ClientAccess.zioMinecraft.player == null) {
         return new float[]{0.0F, 0.0F};
      }
      float width = 0.0F;
      float height = 0.0F;
      int count = 0;
      boolean horizontal = this.layoutSetting.getValue() == Layout.HORIZONTAL;
      for (DisplayMode mode : this.selectedIndicators()) {
         float itemWidth = this.indicatorWidth(this.getDisplayData(mode));
         float itemHeight = this.indicatorHeight();
         width = horizontal ? width + itemWidth : Math.max(width, itemWidth);
         height = horizontal ? Math.max(height, itemHeight) : height + itemHeight;
         count++;
      }
      if (count > 1) {
         if (horizontal) width += (count - 1) * 4.0F;
         else height += (count - 1) * 4.0F;
      }
      float scale = hud.getTextScale();
      return new float[]{width * scale, height * scale};
   }

   @Override
   public void renderContent(PoseStack poses) {
      if (ClientAccess.zioMinecraft.player == null) return;
      this.warpChargeAnimation.setTarget(warp.getCharge(), 100L);
      poses.pushPose();
      try {
         float scale = hud.getTextScale();
         poses.scale(scale, scale, 1.0F);
         float x = 0.0F;
         float y = 0.0F;
         for (DisplayMode mode : this.selectedIndicators()) {
            DisplayData data = this.getDisplayData(mode);
            FloatAnimation visibility = this.visibilityAnimations.get(mode);
            boolean hidden = this.hideSetting.getValue()
               && (mode == DisplayMode.WARP ? data.progress() >= 1.0F : data.progress() <= 0.0F);
            if (hidden) visibility.setTarget(0.0F, 500L);
            else visibility.setValue(1.0F);
            float opacity = visibility.getValue();
            if (this.renderSetting.getValue() == RenderMode.TEXT) {
               FontRenderer.INSTANCE.drawWithShadow(poses,
                  data.formatText(this.prefixSetting.getValue()), x, y,
                  ColorUtils.withAlpha(this.getHudColor(y), opacity));
            } else {
               this.renderBar(poses, mode, data.progress(), x, y, opacity);
            }
            if (this.layoutSetting.getValue() == Layout.HORIZONTAL) x += this.indicatorWidth(data) + 4.0F;
            else y += this.indicatorHeight() + 4.0F;
         }
      } finally {
         poses.popPose();
      }
   }

   private void renderBar(PoseStack poses, DisplayMode mode, float progress, float x, float y, float opacity) {
      float width = this.widthSetting.getValue();
      float clamped = Mth.clamp(progress, 0.0F, 1.0F);
      if (this.vanillaSetting.getValue()) {

         Color fill = switch (mode) {
            case WARP -> new Color(128, 255, 32);
            case RESISTANCE -> new Color(MobEffects.RESISTANCE.value().getColor());
            case STRENGTH -> new Color(MobEffects.STRENGTH.value().getColor());
         };
         GuiDrawUtils.fillRect(poses, x, y, x + width + 0.5F, y + 5.0F,
            ColorUtils.withAlpha(Color.black, opacity));
         GuiDrawUtils.fillRect(poses, x + 0.5F, y + 0.5F, x + 0.5F + Math.max(width * clamped - 0.5F, 0.0F), y + 4.5F,
            ColorUtils.withAlpha(fill, opacity));
         return;
      }
      Color base = ColorUtils.withAlpha(this.getHudColor(y + 9.0F), opacity);
      Color background = new Color(ColorUtils.modulateRgb(base.hashCode(), 63, 63, 63));
      Color fill = ColorUtils.withAlpha(base, 0.7F * opacity);
      GuiDrawUtils.fillRect(poses, x, y, x + width + 0.5F, y + 3.0F,
         ColorUtils.withAlpha(background, 0.7F * opacity));
      float fillWidth = Math.max(width * clamped - 0.5F, 0.0F);
      GuiDrawUtils.fillHorizontalGradient(poses, x + 0.5F, y + 0.5F, fillWidth, 2.0F,
         ColorUtils.darken(fill, 0.83F).hashCode(), fill.hashCode());
   }

   @EventHandler
   public void onLivingEntityAddEffectEvent(LivingEntityAddEffectEvent event) {
      MobEffectInstance incoming = event.getEffect();
      if (ClientAccess.zioMinecraft.player == null) return;
      this.resistance.update(incoming);
      this.strength.update(incoming);
   }

   private static boolean hasShorterDuration(MobEffectInstance current, MobEffectInstance incoming) {
      return !current.isInfiniteDuration()
         && (current.getDuration() < incoming.getDuration() || incoming.isInfiniteDuration());
   }

   public enum RenderMode implements NamedEntry {
      TEXT("Text"), PROGRESSBAR("ProgressBar");
      private final String name;
      RenderMode(String name) { this.name = name; }
      @Override public String getName() { return this.name; }
   }

   public enum DisplayMode implements NamedEntry {
      WARP("Warp"), RESISTANCE("Resistance"), STRENGTH("Strength");
      private final String name;
      DisplayMode(String name) { this.name = name; }
      @Override public String getName() { return this.name; }
   }

   public enum Layout implements NamedEntry {
      HORIZONTAL("Horizontal"), VERTICAL("Vertical");
      private final String name;
      Layout(String name) { this.name = name; }
      @Override public String getName() { return this.name; }
   }

   private static final class EffectIndicator {
      private final String name;
      private final Holder<MobEffect> effect;
      private final int minimumAmplifier;
      private final FloatAnimation animation = new FloatAnimation();
      private int durationTicks;

      EffectIndicator(String name, Holder<MobEffect> effect, int minimumAmplifier) {
         this.name = name;
         this.effect = effect;
         this.minimumAmplifier = minimumAmplifier;
      }

      void update(MobEffectInstance incoming) {
         if (incoming.getEffect() != this.effect || incoming.getAmplifier() < this.minimumAmplifier) return;
         MobEffectInstance current = ClientAccess.zioMinecraft.player.getEffect(this.effect);
         if (current == null || current.getAmplifier() < this.minimumAmplifier
            || incoming.getAmplifier() > current.getAmplifier()
            || incoming.getAmplifier() == current.getAmplifier() && hasShorterDuration(current, incoming)) {
            this.durationTicks = incoming.getDuration();
            this.animation.setValue(1.0F);
         }
      }

      DisplayData getData() {
         MobEffectInstance current = ClientAccess.zioMinecraft.player.getEffect(this.effect);
         if (current != null && current.getAmplifier() >= this.minimumAmplifier && current.isInfiniteDuration()) {
            this.animation.setValue(1.0F);
            return new DisplayData(this.name, "∞", 1.0F);
         }
         int duration = current != null && current.getAmplifier() >= this.minimumAmplifier ? current.getDuration() : 0;
         if (duration > this.durationTicks) this.durationTicks = duration;
         float remaining = this.durationTicks <= 0 ? 0.0F
            : Mth.clamp((float)(duration / 20 * 20) / this.durationTicks, 0.0F, 1.0F);
         this.animation.setTarget(remaining, 500L);
         float progress = this.animation.getValue(sample -> (float)(1.0D - Math.pow(1.0F - (Float)sample, 3.0D)));
         return new DisplayData(this.name, (int)Math.ceil(duration / 20.0F) + "s", progress);
      }
   }

   private record DisplayData(String label, String valueText, float progress) {
      String formatText(boolean prefix) {
         return prefix ? this.label + ChatFormatting.WHITE + " " + this.valueText : this.valueText;
      }
   }
}
