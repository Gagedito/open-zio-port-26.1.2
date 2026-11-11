/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * MC 26.1.2: bobView/bobHurt reciben CameraRenderState primero (el partial
 * tick llego empaquetado en el estado extraido); los invokers se ajustan a
 * (CameraRenderState, PoseStack).
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
   @Accessor("resourcePool")
   CrossFrameResourcePool getPool();

   @Accessor("globalSettingsUniform")
   GlobalSettingsUniform getGlobalSettings();

   /**
    * MC 26.1.2: GameRenderer.fogRenderer es private final; exponerlo para
    * poder pedir el slice de fog por FogMode (p. ej. NONE = emptyBuffer
    * vanilla, fog sin efecto — asi renderiza la GUI vanilla sus items).
    */
   @Accessor("fogRenderer")
   net.minecraft.client.renderer.fog.FogRenderer getFogRenderer();

   @Invoker("bobView")
   void zio$bobView(CameraRenderState cameraState, PoseStack poses);

   @Invoker("bobHurt")
   void zio$bobViewHurt(CameraRenderState cameraState, PoseStack poses);
}
