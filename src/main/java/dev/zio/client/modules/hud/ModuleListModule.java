/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import dev.zio.client.modules.client.HUDModule.ModuleSortMode;

import dev.zio.client.ClientContext;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.HudWidgetModule;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.client.HUDModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.HudAnchor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class ModuleListModule extends HudWidgetModule {
   public final Setting<Boolean> onlyBoundSetting;
   public final Setting<ModuleSortMode> sortSetting;
   private final List<ModuleListHudRow> moduleRows;

   @Override
   public void onEnable() {
      this.moduleRows.clear();
      ModuleRegistry registry = ClientContext.moduleRegistry;
      Stream<ModuleListHudRow> rowsStream = ((List<Module>)registry.getRegistry()).stream()
         .map(registeredModule -> new ModuleListHudRow(this, registeredModule));
      rowsStream.forEach(this.moduleRows::add);
   }

   @EventHandler
   public void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      Setting<ModuleSortMode> sortMode = this.sortSetting;
      if (sortMode.getValue() == HUDModule.ModuleSortMode.ALPHABET) {
         this.moduleRows.sort(
            Comparator.comparing(row -> {
               Module module = row.getModule();
               return module.getInfoString();
            })
         );
      } else {
         this.moduleRows.sort(
            Comparator.comparingDouble(row -> {
               FontRenderer font = FontRenderer.INSTANCE;
               Module module = row.getModule();
               return -font.getWidth(module.getInfoString());
            })
         );
      }
   }

   public ModuleListModule() {
      super("ModuleList", new String[]{"arraylist"});

      this.onlyBoundSetting = this.add(new BooleanSetting("OnlyBound", false));

      this.sortSetting = this.add(new EnumSetting<>("Sort", HUDModule.ModuleSortMode.LENGTH));
      this.moduleRows = new ArrayList<>();
      this.setRenderer(
         new HudTextListRenderer(this, this.moduleRows)
      );

      this.getRenderer()
         .setAnchor(HudAnchor.TOP_RIGHT);
   }

}
