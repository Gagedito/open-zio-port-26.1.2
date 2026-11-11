/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import dev.zio.client.utils.inventory.TradeDefinition;

public record TradePanelRequest(
   TradeDefinition trade,
   TradePanelMode mode
) {
   public static TradePanelRequest forCreate() {
      return new TradePanelRequest(null, TradePanelMode.CREATE);
   }

   public static TradePanelRequest forEdit(
      TradeDefinition selectedTrade
   ) {
      return new TradePanelRequest(selectedTrade, TradePanelMode.EDIT);
   }

}
