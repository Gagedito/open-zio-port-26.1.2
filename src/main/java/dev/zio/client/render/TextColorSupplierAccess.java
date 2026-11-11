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
import org.jetbrains.annotations.Nullable;

public interface TextColorSupplierAccess {
   @Nullable
   Supplier getSupplier();

   void setSupplier(@Nullable Supplier supplier);
}
