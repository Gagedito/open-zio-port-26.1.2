/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.panels;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.NamedFeature;
import dev.zio.client.gui.GuiElement;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.gui.components.ModuleComponent;
import dev.zio.client.render.TextUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;

import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;

public class ModuleCategoryPanel extends Panel implements ClientAccess {
   public ModuleCategory category;

   private void populateModules() {
      List<Module> modules = (List<Module>)ClientContext.moduleRegistry.getRegistry();
      modules.stream()
         .filter(module -> module.getCategory() == this.category)
         .sorted(Comparator.comparing(NamedFeature::getName))
         .forEach(module -> {
            ModuleComponent component = new ModuleComponent(module, this, 0);
            this.children.add(component);
         });
   }

   public ModuleCategoryPanel(ModuleCategory category) {
      super(FontRenderer.capitalize(category.getName()));
      this.category = category;
      this.populateModules();
   }

   @Override
   public void init() {
      this.updateLayout();
      super.init();
   }

   @Override
   protected boolean isChildVisible(GuiElement child) {
      EditBox searchBox = ClientContext.getClickGui()
         .getSearchBox();
      if (searchBox == null) {
         return true;
      }

      if (child instanceof ModuleComponent moduleComponent && !searchBox.getValue().isEmpty()) {
         for (String alias : moduleComponent.getModule()
            .getAliases()) {
            if (TextUtils.containsIgnoreCase(alias, TextUtils.normalizeKeyboardLayout(searchBox.getValue()))) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

}
