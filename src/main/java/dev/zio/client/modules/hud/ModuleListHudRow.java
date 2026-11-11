/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import dev.zio.client.modules.Module;

import net.minecraft.network.chat.Component;

public class ModuleListHudRow extends HudTextRow {
   private final Module module;

   public ModuleListHudRow(ModuleListModule owner, Module module) {
      super(() -> {
         String infoText = module.getInfoString();
         return Component.literal(infoText);
      }, () -> {
         return module.isToggled()
            && module.isDrawn()
            && (!module.getKeybind().isUnbound()
               || !owner.onlyBoundSetting.getValue());
      });
      this.module = module;
   }

   public Module getModule() {
      return this.module;
   }

}
