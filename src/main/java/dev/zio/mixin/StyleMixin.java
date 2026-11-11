/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.render.TextColorSupplierAccess;
import dev.zio.client.render.DynamicColorStyle;
import java.util.function.Supplier;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Style.class)
public class StyleMixin implements DynamicColorStyle {
   @Unique
   @Override
   public Style zio$withColor(Supplier supplier) {
      Style style = (Style)(Object)this;
      if (style.getColor() != null) {
         ((TextColorSupplierAccess)(Object)style.getColor()).setSupplier(supplier);
      }

      return style;
   }
}
