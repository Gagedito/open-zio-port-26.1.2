/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.render.NoRenderModule;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HumanoidMobRenderer.class)
public class HumanoidMobRendererMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

   @ModifyReturnValue(method = "getEquipmentIfRenderable", at = @At("RETURN"))
   private static ItemStack suppressElytraEquipment(ItemStack originalStack) {
      return noRenderModule.isToggled()
            && noRenderModule.elytraSetting.getValue()
            && originalStack.is(Items.ELYTRA)
         ? ItemStack.EMPTY
         : originalStack;
   }
}
