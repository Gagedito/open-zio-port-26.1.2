/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import dev.zio.client.utils.inventory.TradeOfferSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;

public final class VillagerOfferCache {
   private final Map<Integer, Entry> entries = new HashMap<>();

   public long getLastUpdatedMillis(int entityId) {
      Entry entry = this.entries.get(entityId);
      return entry == null ? 0L : entry.lastUpdatedMillis;
   }

   public synchronized void updateOffers(AbstractVillager villager, Iterable<MerchantOffer> offers) {
      if (villager != null) {
         Map<Integer, Entry> capturedEntries = this.entries;
         int entityId = villager.getId();
         Entry entry = capturedEntries.computeIfAbsent(
            entityId, ignoredEntityId -> new Entry()
         );
         entry.lastUpdatedMillis = System.currentTimeMillis();
         entry.offers.clear();
         Iterator<MerchantOffer> offerIterator = offers.iterator();

         while (offerIterator.hasNext()) {
            MerchantOffer offer = offerIterator.next();
            if (offer.isOutOfStock()) {
               entry.offers.add(TradeOfferSnapshot.OUT_OF_STOCK);
            } else {
               TradeOfferSnapshot snapshot = TradeOfferSnapshot.fromOffer(offer);
               entry.offers.add(snapshot);
            }
         }
      }
   }

   public List<TradeOfferSnapshot> getOffers(int entityId) {
      Entry entry = this.entries.get(entityId);
      return entry == null ? null : entry.offers;
   }

   private static class Entry {
      private final List<TradeOfferSnapshot> offers;
      private long lastUpdatedMillis;

      private Entry() {
         this.offers = new ArrayList<>(10);
      }

   }
}
