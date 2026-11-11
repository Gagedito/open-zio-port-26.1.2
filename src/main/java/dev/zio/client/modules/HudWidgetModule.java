/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.gui.screens.HudEditorScreen;
import dev.zio.client.render.HudAnchorGroup;
import dev.zio.client.config.EnumJsonConverter;
import dev.zio.client.render.HudWidgetRenderer;
import dev.zio.client.render.HudVerticalAlignment;
import dev.zio.client.render.HudHorizontalAlignment;
import dev.zio.client.modules.client.HudColorMode;
import dev.zio.client.modules.client.HUDModule;
import dev.zio.client.settings.JsonConfigurable;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.HudAnchor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.Iterator;

public abstract class HudWidgetModule extends Module {

   private static final EnumJsonConverter<HudAnchor> anchorCodec = new EnumJsonConverter<>(
      HudAnchor.class
   );
   protected static HUDModule hud = ClientContext.getModule(HUDModule.class);
   protected HudWidgetRenderer renderer;

   public HudWidgetModule(String name, String... aliases) {
      super(name, ModuleCategory.HUD, aliases);
   }

   @Override
   public boolean isDrawn() {
      return false;
   }

   public int getAnchorIndex() {
      int index = 0;
      HudAnchorGroup anchorGroup = ClientContext.getHudEditor()
         .getAnchorGroup(
            this.renderer
               .getAnchor()
         );

      for (Iterator renderers = anchorGroup.getWidgets().iterator();
         renderers.hasNext();
         index++
      ) {
         HudWidgetRenderer candidate = (HudWidgetRenderer)renderers.next();
         if (candidate.getWidgetModule().equals(this)) {
            return index;
         }
      }

      return -1;
   }

   @Override
   public void fromJson(JsonElement json) {
      super.fromJson(json);
      JsonObject config = json.getAsJsonObject();
      if (config.has("hud")) {
         config = config.getAsJsonObject("hud");
         EnumJsonConverter<HudAnchor> codec = anchorCodec;
         this.setAnchor((HudAnchor)codec.fromJson(config.get("anchor")));
         if (this.renderer.getAnchor() == HudAnchor.NONE) {
            int horizontalOrdinal = config.has("h") ? config.get("h").getAsInt() : 0;
            int verticalOrdinal = config.has("v") ? config.get("v").getAsInt() : 0;
            this.renderer.setHorizontalAlignment(HudHorizontalAlignment.values()[horizontalOrdinal]);
            this.renderer.setVerticalAlignment(HudVerticalAlignment.values()[verticalOrdinal]);
            this.renderer.setX(config.get("x").getAsFloat(), false);
            this.renderer.setY(config.get("y").getAsFloat(), false);
         }
         ClientContext.getHudEditor().requestDirectionRefresh();
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject root = new JsonObject();
      root.addProperty("enabled", this.isToggled());
      JsonObject settingsJson = new JsonObject();
      Iterator settings = this.getSettings().iterator();
      while (settings.hasNext()) {
         Setting<?> setting = (Setting<?>)settings.next();
         if (!setting.isGroupOnly()) {
            String configName = setting.getConfigName();
            JsonElement settingJson = ((JsonConfigurable)setting).toJson();
            settingsJson.add(configName, settingJson);
         }
      }
      root.add("settings", settingsJson);
      JsonObject hudJson = new JsonObject();
      EnumJsonConverter<HudAnchor> codec = anchorCodec;
      HudAnchor anchor = this.renderer.getAnchor();
      hudJson.add("anchor", codec.toJson(anchor));
      hudJson.addProperty("index", this.getAnchorIndex());
      if (this.renderer.getAnchor() == HudAnchor.NONE) {
         hudJson.addProperty("h", this.renderer.getHorizontalAlignment().ordinal());
         hudJson.addProperty("v", this.renderer.getVerticalAlignment().ordinal());
         hudJson.addProperty("x", this.renderer.getRelativeX());
         hudJson.addProperty("y", this.renderer.getRelativeY());
      }
      root.add("hud", hudJson);
      return root;
   }

   public float[] calculateDimensions() {
      throw new RuntimeException();
   }

   public void renderContent(PoseStack poses) {
   }

   public void setAnchor(HudAnchor anchor) {
      this.renderer
         .setAnchor(anchor);
      HudEditorScreen editorForRemoval = ClientContext.getHudEditor();
      HudWidgetRenderer rendererForRemoval = this.renderer;

      editorForRemoval.removeWidget(rendererForRemoval);

      ClientContext.getHudEditor()
         .addWidget(
            anchor, this.renderer
         );
   }

   protected void setRenderer(HudWidgetRenderer renderer) {
      this.renderer = renderer;
   }

   public Color getHudColor(float position) {
      Setting<HudColorMode> fadeSetting = hud.fadeSetting;
      HudColorMode fadeMode = fadeSetting.getValue();
      HUDModule capturedHud = hud;
      return fadeMode.getColor(capturedHud, position);
   }

   public HudWidgetRenderer getRenderer() {
      return this.renderer;
   }

   public HudAnchor getAnchor() {
      return this.renderer
         .getAnchor();
   }

   protected boolean isInHudEditor() {
      return ClientAccess.zioMinecraft.screen instanceof HudEditorScreen;
   }

}
