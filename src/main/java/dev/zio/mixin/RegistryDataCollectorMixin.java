/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.bootstrap.ClientInitializer;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MC 26.1.2: vanilla bindea los data components de los registros de juego en
 * updateComponents, que corre al entrar al primer mundo (ClientConfiguration
 * PacketListenerImpl). Consolid ahi el retry de CustomItemRegistries y la
 * resolucion diferida de valores de configuracion (RegistrySetSetting) que el
 * bind temprano del boot no pudo completar.
 */
@Mixin(RegistryDataCollector.class)
public class RegistryDataCollectorMixin {
   @Inject(method = "updateComponents(Lnet/minecraft/core/RegistryAccess$Frozen;Z)V", at = @At("TAIL"))
   private static void zio$onGameRegistriesComponentsBound(RegistryAccess.Frozen access, boolean memoryConnection, CallbackInfo callback) {
      ClientInitializer.onGameRegistriesComponentsBound();
   }
}
