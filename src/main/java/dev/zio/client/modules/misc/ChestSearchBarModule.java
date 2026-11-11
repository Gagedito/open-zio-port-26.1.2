/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.AbstractContainerScreenEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.utils.inventory.ChestSearchController;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

public class ChestSearchBarModule extends Module {
   public final Setting<Boolean> highlightFullSetting;
   private final Setting<Boolean> chestStealerSetting;
   private final Setting<Boolean> onlyFullSetting;

   public boolean match(ItemStack stack, boolean requireFull) {
      if (ChestSearchController.searchBox == null) {
         return true;
      }

      EditBox searchBox = ChestSearchController.searchBox;
      String query = searchBox.getValue();
      if (query.isEmpty()) {
         return true;
      }

      if (requireFull && !ChestSearchController.isFull(stack, query)) {
         return false;
      }

      return ChestSearchController.namesMatch(stack, query);
   }

   public boolean shouldSkip(ItemStack stack) {
      if (!this.isToggled()) {
         return false;
      }
      Setting<Boolean> chestStealerSettingSnapshot = this.chestStealerSetting;
      Boolean filterChestStealer = chestStealerSettingSnapshot.getValue();
      if (!filterChestStealer) {
         return false;
      }
      Setting<Boolean> onlyFullSettingSnapshot = this.onlyFullSetting;
      boolean requireFullSnapshot = onlyFullSettingSnapshot.getValue();

      return !this.match(stack, requireFullSnapshot);
   }

   @EventHandler
   private void onRender(AbstractContainerScreenEvent event) {
      ChestSearchController searchControllerSnapshot = ChestSearchController.INSTANCE;
      GuiGraphicsExtractor graphicsSnapshot = event.getGraphics();
      Screen screenSnapshot = event.getScreen();
      PoseStack poseSnapshot = event.getPoseStack();
      float mouseXSnapshot = event.getMouseX();
      float mouseYSnapshot = event.getMouseY();
      float renderValueSnapshot = RenderUtils.getPartialTick();

      searchControllerSnapshot.onDrawGui(graphicsSnapshot, screenSnapshot, poseSnapshot, mouseXSnapshot, mouseYSnapshot, renderValueSnapshot);
      if (ChestSearchController.searchBox != null) {

         if (KeyUtils.isPhysicalKeyDown(70)) {

            if (KeyUtils.isControlDown()) {
               Screen focusScreen = ClientAccess.zioMinecraft.screen;
               EditBox focusTarget = ChestSearchController.searchBox;
               focusScreen.setFocused(focusTarget);
            }
         }

         if (KeyUtils.isPhysicalKeyDown(257)) {
            Screen focusScreen = ClientAccess.zioMinecraft.screen;
            focusScreen.setFocused(null);
         }
      }
   }

   public ChestSearchBarModule() {
      super("ChestSearchBar", "Highlights items in chests and shulker boxes.", ModuleCategory.MISC, new String[0]);
      Boolean highlightFullDefault = false;
      this.highlightFullSetting = this.add(new BooleanSetting(("HighlightFull"), highlightFullDefault));
      Boolean chestStealerDefault = false;
      BooleanSetting chestStealerOption = new BooleanSetting(("ChestStealer"), chestStealerDefault);
      chestStealerOption.markVisibilityDependency();
      this.chestStealerSetting = this.add(chestStealerOption);
      Boolean onlyFullDefault = false;
      BooleanSetting onlyFullOption = new BooleanSetting(("OnlyFull"), onlyFullDefault);
      this.onlyFullSetting = this.add(onlyFullOption.dependsOn(this.chestStealerSetting));
      ChestSearchController searchController = ChestSearchController.INSTANCE;
      searchController.init();

      this.setDrawn(false);
   }

}
