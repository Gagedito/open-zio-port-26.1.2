/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import dev.zio.client.integrations.ProtocolVersionAccess;
import dev.zio.client.integrations.ProtocolCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(ProtocolTranslator.class)
public class ProtocolTranslatorMixin {
   @Shadow(remap = false)
   private static ProtocolVersion targetVersion;

   @Inject(method = "setTargetVersion(Lcom/viaversion/viaversion/api/protocol/version/ProtocolVersion;Z)V", at = @At("HEAD"), remap = false)
   private static void syncTargetProtocolVersion(
      ProtocolVersion version, boolean ignoredProtocolFlag, CallbackInfo callback
   ) {
      ProtocolCompatibility.setTargetVersion((ProtocolVersionAccess)version);
   }

   @Inject(method = "<clinit>", at = @At("TAIL"))
   private static void initializeTargetProtocolVersion(CallbackInfo callback) {
      ProtocolCompatibility.setTargetVersion((ProtocolVersionAccess)targetVersion);
   }
}
