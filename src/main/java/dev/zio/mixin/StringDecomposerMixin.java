/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.ClientContext;
import dev.zio.client.modules.player.NameProtectModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(StringDecomposer.class)
public class StringDecomposerMixin {
   private static final NameProtectModule nameProtectModule = ClientContext.getModule(NameProtectModule.class);

   @ModifyArg(
      method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/StringDecomposer;iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
         ordinal = 0
      ),
      index = 0
   )
   private static String protectNamesForFormatting(String text) {
      if (nameProtectModule.isToggled()
         && Minecraft.getInstance().level != null) {
         text = nameProtectModule.protectNames(
            text
         );
      }

      return text;
   }
}
