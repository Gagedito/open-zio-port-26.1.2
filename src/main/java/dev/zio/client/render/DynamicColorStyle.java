/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import java.util.function.Supplier;
import net.minecraft.network.chat.Style;

public interface DynamicColorStyle {
   Style zio$withColor(Supplier colorSupplier);
}
