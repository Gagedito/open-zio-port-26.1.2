/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.screens;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.gui.components.TradeEditorPanel;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.utils.inventory.TradeDefinition;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public class TradeEditorScreen extends ClientScreen {
   private final TradeEditorPanel editorPanel;
   private ItemStack tooltipStack;

   public TradeEditorScreen(String tradeName) {
      TradeDefinition trade = ClientContext.tradeRegistry
         .createDefault(tradeName);
      this.editorPanel = new TradeEditorPanel(this, trade, "Create");
      this.getPanels()
         .add(this.editorPanel);
   }

   public TradeEditorScreen(TradeDefinition trade) {
      this.editorPanel = new TradeEditorPanel(this, trade, "Edit");
      this.getPanels()
         .add(this.editorPanel);
   }

   @Override
   public void renderContents(
      GuiGraphicsExtractor graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick
   ) {
      GuiDrawUtils.flush();
      if (this.tooltipStack != null) {
         graphics.setTooltipForNextFrame(
            ClientAccess.zioMinecraft.font,
            this.tooltipStack,
            mouseX,
            mouseY
         );
      }

      this.tooltipStack = null;
   }

   public void setTooltipStack(ItemStack stack) {
      this.tooltipStack = stack;
   }

}
