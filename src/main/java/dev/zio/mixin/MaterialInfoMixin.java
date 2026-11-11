/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.mojang.blaze3d.platform.Transparency;
import dev.zio.client.ClientContext;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.render.NoRenderModule;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reemplazo de <b>MaterialMixin</b> para MC 26.1.2.
 * <p>
 * En 1.21.11 el render type de un material se resolvia por frame en
 * {@code Material.renderType(Function)}; ahi se inyectaba la transicion a
 * {@code RenderTypes.entityTranslucent(atlas)} cuando la opacidad de la mano
 * (NoRender) estaba activa. En 26.1.2 esa resolucion pasa a ser de <i>bake
 * time</i>: {@code BakedQuad$MaterialInfo.of(Material$Baked, Transparency, ...)}
 * es el unico punto por donde bloque/entidad/item hornean su RenderType
 * (llamado desde {@code FaceBakery.bakeQuad} y de
 * {@code ItemModelGenerator$ItemLayerKey.compute}). Ahi se replica el mismo
 * cuerpo forzando {@code Transparency.TRANSLUCENT} cuando el hook de opacidad
 * aplica.
 */
@Mixin(BakedQuad.MaterialInfo.class)
public class MaterialInfoMixin {
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

   @Inject(method = "of", at = @At("HEAD"), cancellable = true)
   private static void zio$applyHandOpacity(
      Material.Baked material, Transparency transparency, int tintIndex, boolean shade, int lightEmission,
      CallbackInfoReturnable<BakedQuad.MaterialInfo> callback
   ) {
      // Misma condicion que el hook de Material.renderType en 1.21.11.
      if (noRenderModule.getHandOpacity()
            != 1.0F
         && RenderUtils.getRenderColor()[3]
            != 255) {
         callback.setReturnValue(zio$translucentInfo(material, tintIndex, shade, lightEmission));
      }
   }

   @Unique
   private static BakedQuad.MaterialInfo zio$translucentInfo(Material.Baked material, int tintIndex, boolean shade, int lightEmission) {
      // Replica del cuerpo de MaterialInfo.of con transparencia TRANSLUCENT.
      // No llama a of() para evitar re-entrar en la propia inyeccion.
      ChunkSectionLayer layer = ChunkSectionLayer.byTransparency(Transparency.TRANSLUCENT);
      Identifier atlas = material.sprite().atlasLocation();
      RenderType renderType = atlas.equals(TextureAtlas.LOCATION_BLOCKS)
         ? Sheets.translucentBlockItemSheet()
         : Sheets.translucentItemSheet();
      return new BakedQuad.MaterialInfo(material.sprite(), layer, renderType, tintIndex, shade, lightEmission);
   }

   @Unique
   private static void zio$bindMaterialInfoModules(ModuleRegistry moduleRegistry) {
      noRenderModule = (NoRenderModule)moduleRegistry.getModule(
         NoRenderModule.class
      );
   }
}
