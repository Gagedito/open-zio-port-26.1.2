/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;

record StackGroupKey(
   Item item,
   DataComponentMap components
) {

}
