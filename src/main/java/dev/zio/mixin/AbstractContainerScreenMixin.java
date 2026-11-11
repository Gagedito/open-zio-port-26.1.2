/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.zio.client.events.AbstractContainerScreenEvent;
import dev.zio.client.events.AbstractContainerScreenRenderTooltipEvent;
import dev.zio.client.modules.misc.ChestSearchBarModule;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.utils.inventory.ContainerTooltipComponent;
import dev.zio.client.access.TooltipComponentCapture;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.utils.inventory.ChestSearchController;
import dev.zio.client.modules.player.InventoryTweaksModule;
import dev.zio.client.modules.render.TooltipsModule;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin implements ClientAccess {
   private static final ChestSearchBarModule zio$chestSearchBar = ClientContext.getModule(ChestSearchBarModule.class);
   private static final TooltipsModule zio$tooltips = ClientContext.getModule(TooltipsModule.class);
   private static final InventoryTweaksModule zio$inventoryTweaks = ClientContext.getModule(InventoryTweaksModule.class);
   @Shadow
   @Nullable
   protected Slot hoveredSlot;
   @Unique
   private int zio$mouseX;
   @Unique
   private int zio$mouseY;
   @Unique
   private int zio$tooltipX;
   @Unique
   private int zio$tooltipY;
   @Unique
   private Slot zio$cachedHoveredSlot;
   @Unique
   private MapItemSavedData zio$slotMapData;
   @Unique
   private int zio$slotMapId;
   @Unique
   private final AbstractContainerScreenEvent zio$containerScreenEvent = new AbstractContainerScreenEvent(
      (AbstractContainerScreen)(Object)this
   );

   @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
   public void zio$preventInventoryCloseDuringSearch(
      KeyEvent keyEvent, CallbackInfoReturnable<Boolean> callback
   ) {
      if (zio$chestSearchBar != null
         && zio$chestSearchBar.isToggled()
         && ChestSearchController.searchBox != null
         && ChestSearchController.searchBox.isFocused()
         && ClientAccess.zioMinecraft.options.keyInventory.matches(keyEvent)) {
         callback.setReturnValue(true);
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void zio$runContainerTickCallback(CallbackInfo callback) {
      this.zio$containerScreenEvent
         .runTickCallback();
   }

   @Inject(method = "renderTooltip", at = @At("HEAD"))
   private void zio$postContainerScreenEvent(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo callback
   ) {
      this.zio$containerScreenEvent
         .setGraphics(graphics);
      this.zio$containerScreenEvent
         .setMouseX(mouseX);
      this.zio$containerScreenEvent
         .setMouseY(mouseY);
      ClientAccess.eventBus.post(
         this.zio$containerScreenEvent
      );
   }

   @Inject(method = "renderContents", at = @At("HEAD"))
   private void zio$captureHoveredSlotAndMouse(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback
   ) {
      if (!KeyUtils.isPhysicalKeyDown(342)) {
         this.zio$cachedHoveredSlot = this.hoveredSlot;
      }

      this.zio$mouseX = mouseX;
      this.zio$mouseY = mouseY;
   }

   @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
   private void zio$useInspectedHoveredSlot(
      double mouseX, double mouseY, CallbackInfoReturnable<Slot> callback
   ) {
      if (this.zio$cachedHoveredSlot != null
         && zio$tooltips.shouldInspectContents(
            this.zio$cachedHoveredSlot.getItem()
         )) {
         callback.setReturnValue(this.zio$cachedHoveredSlot);
      }
   }

   @ModifyExpressionValue(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasClickedOutside(DDII)Z"))
   public boolean zio$keepInspectedClickInside(boolean originalInside) {
      return (this.zio$cachedHoveredSlot == null
         || !zio$tooltips.shouldInspectContents(this.zio$cachedHoveredSlot.getItem()))
         && originalInside;
   }

   @ModifyArgs(
      method = "mouseClicked",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", ordinal = 1)
   )
   private void zio$useBundleInspectButton(Args arguments) {
      int mouseButton = arguments.<Integer>get(2);
      if (this.zio$cachedHoveredSlot != null
         && zio$tooltips.shouldInspectContents(
            this.zio$cachedHoveredSlot.getItem()
         )
         && mouseButton == 0
         && this.zio$cachedHoveredSlot
            .getItem()
            .is(ItemTags.BUNDLES)) {
         arguments.set(2, 1);
      }
   }

   @ModifyArgs(
      method = "mouseReleased",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", ordinal = 9)
   )
   private void zio$swapBundleReleaseButton(Args arguments) {
      Slot slot = arguments.get(0);
      int mouseButton = arguments.<Integer>get(2);
      if (zio$inventoryTweaks.isToggled()
         && zio$inventoryTweaks.shouldSwapBundleClickButton(
            slot, mouseButton
         )) {
         arguments.set(
            2,
            mouseButton == 0 ? 1 : 0
         );
      }
   }

   @WrapWithCondition(
      method = "renderTooltip",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"
      )
   )
   private boolean zio$renderTooltipWithEvent(
      GuiGraphicsExtractor graphics, Font font, List text, Optional image, int tooltipX, int tooltipY, Identifier background
   ) {
      if (this.zio$cachedHoveredSlot != null
         && zio$tooltips.shouldInspectContents(
            this.zio$cachedHoveredSlot.getItem()
         )) {
         tooltipX = this.zio$tooltipX;
         tooltipY = this.zio$tooltipY;
      } else {
         this.zio$tooltipX = tooltipX;
         this.zio$tooltipY = tooltipY;
      }

      List components = TooltipComponentCapture.captureComponents(graphics, text, image);
      AbstractContainerScreenRenderTooltipEvent event = new AbstractContainerScreenRenderTooltipEvent(
         (Screen)(Object)this,
         components,
         tooltipX,
         tooltipY,
         this.zio$mouseX,
         this.zio$mouseY
      );
      ClientAccess.eventBus
         .post(event);
      if (!event.isCancelled()) {
         ((GuiGraphicsAccessor)graphics)
            .drawTooltipsHook(
               font,
               event.getTooltipComponents(),
               tooltipX,
               tooltipY,
               DefaultTooltipPositioner.INSTANCE,
               null
            );
      }

      return false;
   }

   @Inject(method = "renderSlot", at = @At("HEAD"))
   private void zio$captureSlotMapData(
      GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo callback
   ) {
      this.zio$slotMapData = null;
      ItemStack stack = slot.getItem();
      if (stack.is(Items.FILLED_MAP)) {
         int mapId = stack.getOrDefault(DataComponents.MAP_ID, new MapId(-1)).id();
         if (mapId != -1) {
            this.zio$slotMapId = mapId;
            this.zio$slotMapData = ClientContext.mapDataCache
               .getMapData(stack, mapId);
         }
      }
   }

   @WrapWithCondition(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
   private boolean zio$shouldRenderSlotItem(
      GuiGraphicsExtractor graphics, ItemStack stack, int itemX, int itemY, int seed
   ) {
      return !zio$tooltips.isToggled()
         || !zio$tooltips.mapOverlaySetting.getValue()
         || this.zio$slotMapData == null;
   }

   @WrapWithCondition(
      method = "renderSlot",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V")
   )
   private boolean zio$renderMapOverlay(
      GuiGraphicsExtractor graphics, Font font, ItemStack stack, int itemX, int itemY, String countText, @Local(argsOnly = true) Slot slot
   ) {
      if (!zio$tooltips.isToggled() || !zio$tooltips.mapOverlaySetting.getValue() || this.zio$slotMapData == null) {
         return true;
      }
      ContainerTooltipComponent.renderMapOverlay(
         graphics,
         this.zio$slotMapData,
         this.zio$slotMapId,
         itemX,
         itemY
      );
      if (!KeyUtils.isPhysicalKeyDown(342)) {
         graphics.itemDecorations(font, stack, itemX, itemY);
      }

      return false;
   }
}
