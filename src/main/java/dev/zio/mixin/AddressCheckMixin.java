/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import net.minecraft.client.multiplayer.resolver.AddressCheck;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AddressCheck.class)
public interface AddressCheckMixin {
   @Inject(method = "createFromService", at = @At("HEAD"), cancellable = true)
   private static void returnPermissiveAddressCheck(CallbackInfoReturnable<AddressCheck> callback) {
      callback.setReturnValue(new AddressCheck() {

         @Override
         public boolean isAllowed(ResolvedServerAddress address) {
            return true;
         }

         @Override
         public boolean isAllowed(ServerAddress address) {
            return true;
         }
      });
   }
}
