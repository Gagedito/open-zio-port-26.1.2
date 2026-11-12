package dev.zio.mixin;

import java.util.Set;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiRenderState.class)
public interface GuiRenderStateAccessor {
   @Accessor("itemModelIdentities")
   Set<Object> getItemModelIdentities();
}
