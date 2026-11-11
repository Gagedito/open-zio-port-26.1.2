/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client;

import dev.zio.client.events.bus.EventBus;
import dev.zio.client.utils.inventory.ItemRegistryJsonAdapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ClientAccess {
   Logger logger = LoggerFactory.getLogger(ZioClient.NAME);
   Minecraft zioMinecraft = Minecraft.getInstance();
   Gson gson = new GsonBuilder()
      .registerTypeAdapter(Item.class, new ItemRegistryJsonAdapter.ItemAdapter())
      .registerTypeAdapter(BlockItem.class, new ItemRegistryJsonAdapter.BlockItemAdapter())
      .setPrettyPrinting()
      .create();
   EventBus eventBus = new EventBus();
   ExecutorService executorService = Executors.newCachedThreadPool();

   default boolean isWorldUnavailable() {
      return ClientAccess.zioMinecraft.player == null
         || ClientAccess.zioMinecraft.level == null;
   }

   static String getVersion() {
      return ZioClient.VERSION;
   }

   static boolean isLocalPlayer(Object candidate) {
      return ClientAccess.zioMinecraft.player == candidate;
   }

}
