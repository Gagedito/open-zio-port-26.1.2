/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import net.minecraft.world.entity.player.Player;

public interface AntiPhasePlacementPlanner {
   AntiPhasePlacement findPlacement(Player target);
}
