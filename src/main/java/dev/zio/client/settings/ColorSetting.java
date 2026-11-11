/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings;

import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.ClientContext;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.config.ColorJsonConverter;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.client.ColorsModule;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.function.Predicate;

public final class ColorSetting extends Setting<Color> {
   private static ColorsModule colorsModule;
   private final ColorJsonConverter colorCodec = new ColorJsonConverter();
   private boolean rainbow;
   private boolean synced;

   public ColorSetting(String name, Color defaultColor, Predicate<? super Color> visibility) {
      super(name, defaultColor, visibility);
   }

   public ColorSetting(String name, Color defaultColor) {
      super(name, defaultColor);
   }

   static {
      colorsModule = (ColorsModule)ClientContext.getModule(ColorsModule.class);
   }

   @Override
   public Color getValue() {
      return this.getColor();
   }

   public void setRainbow(boolean rainbow) {
      this.rainbow = rainbow;
   }

   public boolean isRainbow() {
      if (this == colorsModule.themeColorSetting) {
         return this.rainbow;
      }

      if (!this.rainbow) {
         if (this.isSynced()) {
            ColorSetting themeSetting = (ColorSetting)colorsModule.themeColorSetting;

            if (themeSetting.isRainbow()) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public boolean isThemeColor() {
      return this == colorsModule.themeColorSetting;
   }

   public void setColor(Color color) {
      if (this.opaqueColor) {
         super.setValue(new Color(color.hashCode(), false));
      } else {
         super.setValue(color);
      }
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json.isJsonArray()) {
         ColorJsonConverter codec = this.colorCodec;
         this.setColor(codec.fromJson(json));
      } else {
         JsonObject object = json.getAsJsonObject();
         ColorJsonConverter codec = this.colorCodec;
         JsonElement rgba = object.get("rgba");
         this.setColor(codec.fromJson(rgba));
         this.setRainbow(object.get("rainbow").getAsBoolean());
         this.setSynced(object.get("sync").getAsBoolean());
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject json = new JsonObject();
      ColorJsonConverter codec = this.colorCodec;
      Color color = this.getColor();
      json.add("rgba", codec.toJson(color));
      json.addProperty("rainbow", this.isRainbow());
      json.addProperty("sync", this.isSynced());
      return json;
   }

   public Color applyRainbow(Color color, int phaseOffset) {
      int red = color.getRed();

      int green = color.getGreen();

      int blue = color.getBlue();
      float[] hsb = Color.RGBtoHSB(red, green, blue, null);
      float rainbowPeriodFactor = MathConstants.FULL_TURN_DEGREES;
      ColorsModule colorsSnapshot = colorsModule;

      int rainbowPeriod = (int)(rainbowPeriodFactor * colorsSnapshot.getRainbowPeriod());
      float saturation = hsb[1];

      float brightness = hsb[2];

      return ColorUtils.rainbow(
         rainbowPeriod, phaseOffset, saturation, brightness, color.getAlpha()
      );
   }

   public Color getColor() {
      Color color = super.getValue();
      if (this.synced) {
         Setting<Color> themeSetting = colorsModule.themeColorSetting;
         Color themeColor = themeSetting.getValue();

         return ColorUtils.withAlpha(themeColor, color.getAlpha());
      } else if (this.rainbow) {
         return this.applyRainbow(color, 0);
      } else {
         return color;
      }
   }

   public boolean isSynced() {
      return colorsModule != null
            && this == colorsModule.themeColorSetting
         ? false
         : this.synced;
   }

   public void setSynced(boolean synced) {
      if (colorsModule == null
         || this != colorsModule.themeColorSetting) {
         this.synced = synced;
      }
   }

   @Override
   public void parseValue(String text) {
      try {
         Color parsedColor = ColorUtils.parseHexColor(text);
         this.setColor(parsedColor);
      } catch (Throwable failure) {
      }
   }

   @Override
   public void setValue(Color value) {
      this.setColor(value);
   }

   public static void bindModuleRegistry(ModuleRegistry registry) {
      colorsModule = (ColorsModule)registry.getModule(ColorsModule.class);
   }
}
