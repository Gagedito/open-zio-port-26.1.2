/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.settings.options;

import dev.zio.client.ClientAccess;
import dev.zio.client.utils.math.FloatBounds;
import dev.zio.client.render.HudVerticalAlignment;
import dev.zio.client.render.HudHorizontalAlignment;
import dev.zio.client.settings.NamedEntry;

import java.util.function.BiFunction;
import net.minecraft.util.Mth;

public enum HudAnchor implements ClientAccess, NamedEntry {
   TOP_LEFT(
      "top_left",
      HudVerticalAlignment.TOP,
      HudHorizontalAlignment.LEFT,
      (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(
         0.0F, 0.0F, 10.0F, 10.0F
      )
   ),
   TOP_RIGHT(
      "top_right",
      HudVerticalAlignment.TOP,
      HudHorizontalAlignment.RIGHT,
      (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(
         screenWidth - 10, 0.0F, 10.0F, 10.0F
      )
   ),
   BOTTOM_LEFT(
      "bottom_left",
      HudVerticalAlignment.BOTTOM,
      HudHorizontalAlignment.LEFT,
      (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(
         0.0F, screenHeight - 10, 10.0F, 10.0F
      )
   ),
   BOTTOM_RIGHT(
      "bottom_right",
      HudVerticalAlignment.BOTTOM,
      HudHorizontalAlignment.RIGHT,
      (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(
         screenWidth - 10, screenHeight - 10, 10.0F, 10.0F
      )
   ),
   TOP_CENTER(
      "top_center",
      HudVerticalAlignment.TOP,
      HudHorizontalAlignment.CENTER,
      (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(
         screenWidth.intValue() / 2.0F - 10.0F, 0.0F, 10.0F, 10.0F
      )
   ),
   NONE(
      "none",
      HudVerticalAlignment.FREE,
      HudHorizontalAlignment.FREE,
      (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(
         0.0F, 0.0F, screenWidth.intValue(), screenHeight.intValue()
      )
   );

   private final String name;
   private final HudVerticalAlignment verticalAlignment;
   private final HudHorizontalAlignment horizontalAlignment;
   private final BiFunction<Integer, Integer, FloatBounds> boundsFactory;

   @Override
   public String getName() {
      return this.name;
   }

   HudAnchor(String name, HudVerticalAlignment verticalAlignment, HudHorizontalAlignment horizontalAlignment, BiFunction<Integer, Integer, FloatBounds> boundsFactory) {
      this.name = name;
      this.verticalAlignment = verticalAlignment;
      this.horizontalAlignment = horizontalAlignment;
      this.boundsFactory = boundsFactory;
   }

   public boolean contains(double mouseX, double mouseY) {
      return this.getBounds()
         .contains(
            Mth.clamp(
               mouseX, 0.0, ClientAccess.zioMinecraft.getWindow().getGuiScaledWidth()
            ),
            Mth.clamp(
               mouseY, 0.0, ClientAccess.zioMinecraft.getWindow().getGuiScaledHeight()
            )
         );
   }

   public FloatBounds getBounds() {
      return this.boundsFactory
         .apply(
            ClientAccess.zioMinecraft.getWindow().getGuiScaledWidth(),
            ClientAccess.zioMinecraft.getWindow().getGuiScaledHeight()
         );
   }

   public HudHorizontalAlignment getHorizontalAlignment() {
      return this.horizontalAlignment;
   }

   public HudVerticalAlignment getVerticalAlignment() {
      return this.verticalAlignment;
   }
}
