/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.render.ResizableHudWidgetRenderer;
import dev.zio.client.modules.HudWidgetModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TotemsModule extends HudWidgetModule {
   private ItemStack totemStack;
   private final Setting<Boolean> whiteSetting;

   @Override
   public float[] calculateDimensions() {
      float[] dimensions = new float[2];
      dimensions[0] = 16.0F;
      dimensions[1] = 16.0F;
      return dimensions;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent ignoredEvent) {
      LocalPlayer localPlayer = ClientAccess.zioMinecraft.player;
      if (!localPlayer.isSpectator()) {
         this.totemStack = new ItemStack(
            Items.TOTEM_OF_UNDYING,
            InventoryUtils.countItems((Predicate<ItemStack>)candidateStack -> {
               Item totemItemForMatch = Items.TOTEM_OF_UNDYING;
               return candidateStack.is(totemItemForMatch);
            })
         );
      }
   }

   @Override
   public void renderContent(PoseStack poses) {
      if (this.totemStack != null) {
         ItemStack stackForEmptyCheck = this.totemStack;
         if (stackForEmptyCheck.getCount() != 0) {
            ItemStack stackToRender = this.totemStack;

            GuiDrawUtils.drawItem(
               poses, stackToRender, 0.0F, 0.0F, -150.0F, true
            );
            ItemStack stackForOverlayCheck = this.totemStack;
            if (stackForOverlayCheck.getCount() > 1) {
               int totemCount = this.totemStack.getCount();
               String countText = String.valueOf(totemCount);
               poses.pushPose();

               double textDepth = 0.1D;
               double textTranslateY = 0.0;
               double textTranslateX = 0.0;
               poses.translate(textTranslateX, textTranslateY, textDepth);
               FontRenderer font = FontRenderer.INSTANCE;

               float textX = 17.0F
                  - FontRenderer.INSTANCE.getWidth(countText);
               float textY = 9.0F;
               Setting<Boolean> whiteOption = this.whiteSetting;
               Boolean useWhite = whiteOption.getValue();
               Color textColor;
               if (useWhite) {
                  textColor = Color.white;
               } else {

                  textColor = this.getHudColor(9.0F);
               }

               font.drawWithShadow(poses, countText, textX, textY, textColor);
               poses.popPose();
            }

            return;
         }
      }
   }

   public TotemsModule() {
      super("Totems", new String[0]);

      this.whiteSetting = this.add(new BooleanSetting("White", false));
      this.setRenderer(new ResizableHudWidgetRenderer(this));
   }

}
