/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.render.ChamsColorProvider;
import dev.zio.client.modules.render.ChamsModule;
import dev.zio.client.settings.NamedEntry;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.world.entity.Entity;
public enum ChamsStyle implements ChamsColorProvider, NamedEntry {
   BOTH("Both"),
   FILL("Fill") {
      @Override
      public Color[] getColors(ChamsModule chams) {
         return new Color[]{TRANSPARENT_COLOR, chams.fillSetting.getValue()};
      }
   },
   LINE("Line") {
      @Override
      public Color[] getColors(ChamsModule chams) {
         Color[] colors = new Color[2];
         colors[0] = chams.outlineSetting.getValue();
         colors[1] = TRANSPARENT_COLOR;
         return colors;
      }
   },
   PLAIN("Plain") {
      @Override
      public void renderEntity(
         ChamsModule chams, Entity entity, PoseStack poseStack
      ) {
      }
   },
   OFF("Off");
   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }
   ChamsStyle(String displayName) {
      this.displayName = displayName;
   }
   @Override
   public boolean isEnabled() {
      return this != OFF;
   }
}
