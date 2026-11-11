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
import dev.zio.client.render.RenderUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.render.NoRenderModule;
import java.util.function.Function;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SUPERSEDED - MC 26.1.2. Ya no esta registrado en zio.mixins.json.
 * <p>
 * En 1.21.11 este mixin interceptaba {@code Material.renderType(Function)} y
 * devolvia {@code RenderTypes.entityTranslucent(atlasLocation)} cuando la
 * opacidad de mano de NoRenderModule aplicaba. En 26.1.2:
 * <ul>
 *   <li>{@code net.minecraft.client.resources.model.Material} se movio a
 *       {@code ...model.sprite.Material} y paso a ser un record
 *       ({@code sprite}, {@code forceTranslucent}) sin metodo renderType.</li>
 *   <li>El atlas residio en el campo {@code sprite} (renombrado), no
 *       {@code atlasLocation}.</li>
 *   <li>La seleccion de RenderType/transparencia es ahora de bake time: el
 *       equivalente 1:1 de este hook vive en {@link MaterialInfoMixin}
 *       (inject en {@code BakedQuad$MaterialInfo.of}).</li>
 * </ul>
 * Se conserva como comentario historico; no debe volver a registrarse sin
 * re-verificar los members contra el oraculo.
 */
@Mixin(Material.class)
public class MaterialMixin {
   // MC 26.1.2: el campo fue renombrado a 'sprite' en el record
   // net.minecraft.client.resources.model.sprite.Material; 'atlasLocation' ya no existe.
   @Shadow
   @Final
   private Identifier atlasLocation;
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

   // MC 26.1.2: el metodo 'renderType' ya no existe en el record Material.
   /*@Inject(method = "renderType", at = @At("HEAD"), cancellable = true)
   private void applyTranslucentHandMaterial(Function renderTypeFactory, CallbackInfoReturnable<RenderType> callback) {
      if (noRenderModule.getHandOpacity()
            != 1.0F
         && RenderUtils.getRenderColor()[3]
            != 255) {
         callback.setReturnValue(RenderTypes.entityTranslucent(this.atlasLocation));
         callback.cancel();
      }
   }*/

   @Unique
   private static void zio$bindMaterialModules(ModuleRegistry moduleRegistry) {
      noRenderModule = (NoRenderModule)moduleRegistry.getModule(
         NoRenderModule.class
      );
   }
}
