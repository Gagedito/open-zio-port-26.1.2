/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.events.bus.EventBus;
import net.fabricmc.loader.api.FabricLoader;

public final class EntityCullingOverrides implements ClientAccess {
   private static final EntityCullingAdapter CULLING_ACCESS;
   private static boolean cullingOverrideActive;
   private static boolean savedCullingEnabled;
   private static boolean savedTickCullingEnabled;
   private static int tickCullingRestoreDelay;

   static {
      CULLING_ACCESS = FabricLoader.getInstance().isModLoaded("entityculling") ? new EntityCullingAdapter() : null;
      Class<EntityCullingOverrides> subscriber = EntityCullingOverrides.class;
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribeStatic(subscriber);
   }

   public static void restoreCulling() {
      if (cullingOverrideActive
         && CULLING_ACCESS != null) {
         cullingOverrideActive = false;
         CULLING_ACCESS.setCullingEnabled(savedCullingEnabled);
      }
   }

   @EventHandler
   public static void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      MinecraftRunTickEvent tickEvent = event;
      if (tickEvent.hasPendingTicks()
         && tickCullingRestoreDelay > 0) {
         tickCullingRestoreDelay--;
         if (tickCullingRestoreDelay == 0) {
            CULLING_ACCESS.setTickCullingEnabled(savedTickCullingEnabled);
         }
      }
   }

   public static void disableCulling() {
      if (!cullingOverrideActive
         && CULLING_ACCESS != null) {
         cullingOverrideActive = true;
         savedCullingEnabled = CULLING_ACCESS.isCullingEnabled();
         CULLING_ACCESS.setCullingEnabled(false);
         disableTickCulling();
      }
   }

   private static void disableTickCulling() {
      if (tickCullingRestoreDelay == 0) {
         savedTickCullingEnabled = CULLING_ACCESS.isTickCullingEnabled();
      }
      tickCullingRestoreDelay = 2;
      CULLING_ACCESS.setTickCullingEnabled(false);
   }

}
