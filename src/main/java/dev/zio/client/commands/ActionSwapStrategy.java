/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.ClientAccess;

import net.minecraft.world.item.Item;

public interface ActionSwapStrategy extends ClientAccess {
   SwapSlots findSlots(Item[] items);

   void apply(SwapSlots slots);

   void undo(SwapSlots slots);
}
