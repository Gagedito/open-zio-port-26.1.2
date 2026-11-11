/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.client;

import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.core.ClientAsset.Texture;
import net.minecraft.resources.Identifier;

public final class CapesModule extends Module {
   private final EnumSetting<Cape> capeSetting;

   public CapesModule() {
      super("Capes", "Displays a cape on your player.", ModuleCategory.CLIENT);
      this.capeSetting = this.add(new EnumSetting<>("Cape", Cape.ZIO));
   }

   public Texture getCapeTexture() {
      return this.capeSetting.getValue().texture;
   }

   private enum Cape implements NamedEntry {
      ZIO("Zio", "zio.png"),
      PEPSI("Pepsi", "pepsi.png"),
      TETRIS("Tetris", "tetris.png"),
      SUCKMY("SuckMy", "suckmy.png");

      private final String displayName;
      private final ResourceTexture texture;

      Cape(String displayName, String fileName) {
         this.displayName = displayName;
         Identifier textureId = Identifier.fromNamespaceAndPath("zio", "capes/" + fileName);
         this.texture = new ResourceTexture(textureId, textureId);
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }
}
