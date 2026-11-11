/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {
   /**
    * MC 26.1.2: GuiGraphicsExtractor.renderTooltip(Font, List, int, int,
    * ClientTooltipPositioner, Identifier) se volvio publica con el nombre
    * tooltip(Font, List<ClientTooltipComponent>, ...); el invoker apunta ahi.
    */
   @Invoker("tooltip")
   void drawTooltipsHook(Font font, List components, int mouseX, int mouseY, ClientTooltipPositioner positioner, @Nullable Identifier background);

   @Accessor("hoveredTextStyle")
   void zio$hoverStyle(Style hoveredTextStyle);

   @Accessor("clickableTextStyle")
   void zio$clickStyle(Style clickableTextStyle);

   @Accessor("mouseX")
   int zio$mouseX();

   @Accessor("mouseY")
   int zio$mouseY();
}
