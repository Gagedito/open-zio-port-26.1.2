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
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
public class WorldOpenFlowsMixin {

   @Inject(method = "openWorld", at = @At("HEAD"))
   public void clearServerDataForWorldOpen(
      String worldName, Runnable onCancel, CallbackInfo callback
   ) {
      ClientContext.serverStateManager
         .setServerData((ServerData)null);
   }
}
