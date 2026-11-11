/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.utils.FloatAnimation;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.HudRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.render.RenderTargetUtils;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.render.GuiDeferredDraws;
import dev.zio.client.utils.combat.DamageUtils;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.render.PostProcessShaderPass;
import dev.zio.client.utils.inventory.EquipmentUtils;
import dev.zio.client.render.ResizableHudWidgetRenderer;
import dev.zio.client.modules.HudWidgetModule;
import dev.zio.client.modules.client.ColorsModule;
import dev.zio.client.modules.client.FontsModule;
import dev.zio.client.modules.client.HUDModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.ColorsSchemeChoice;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class ArmorModule extends HudWidgetModule {
   private static final HUDModule hudModule;
   private static ColorsModule colorsModule;
   private final Setting<Boolean> barSetting;
   private final Setting<Boolean> durabilitySetting;
   private final Setting<Boolean> percentageSetting;
   private final Setting<Boolean> barColorSetting;
   private final Setting<Boolean> animationSetting;
   private final FloatAnimation[] durabilityAnimations = {
      new FloatAnimation(), new FloatAnimation(), new FloatAnimation(), new FloatAnimation()
   };
   private final boolean[] durabilityInitialized = new boolean[4];

   static {
      hudModule = ClientContext.getModule(HUDModule.class);
      colorsModule = ClientContext.getModule(ColorsModule.class);
   }

   private int getArmorBottomOffset() {
      int bottomOffset;
      offsetSelection: {
         airBarCheck: {
            LocalPlayer playerForSubmersion = ClientAccess.zioMinecraft.player;
            if (!playerForSubmersion.isUnderWater()) {
               int currentAir = ClientAccess.zioMinecraft.player.getAirSupply();
               LocalPlayer playerForMaximumAir = ClientAccess.zioMinecraft.player;
               if (currentAir == playerForMaximumAir.getMaxAirSupply()) {
                  break airBarCheck;
               }
            }

            LocalPlayer playerForUnderwaterCreative = ClientAccess.zioMinecraft.player;
            if (!playerForUnderwaterCreative.isCreative()) {

               bottomOffset = 65;
               break offsetSelection;
            }
         }

         LocalPlayer playerForPassenger = ClientAccess.zioMinecraft.player;
         if (playerForPassenger.isPassenger()) {
            LocalPlayer playerForVehicle = ClientAccess.zioMinecraft.player;
            if (playerForVehicle.getVehicle() instanceof LivingEntity mount) {
               double mountOffsetBase = MathConstants.FORTY_FIVE_DEGREES;
               float adjustedMaximumHealth = mount.getMaxHealth();

               adjustedMaximumHealth -= 1.0F;

               double healthRows = adjustedMaximumHealth / 20.0F;
               double mountHealthRows = Math.ceil(healthRows);

               bottomOffset = (int)(mountOffsetBase + mountHealthRows * 10.0D);
               break offsetSelection;
            }
         }

         LocalPlayer playerForCreativeOffset = ClientAccess.zioMinecraft.player;
         int creativeOffset;
         if (playerForCreativeOffset.isCreative()) {

            creativeOffset = 17;
         } else {
            creativeOffset = 0;
         }

         bottomOffset = 55 - creativeOffset;
      }

      return bottomOffset;
   }

   @Override
   public float[] calculateDimensions() {
      float[] dimensions = new float[2];
      dimensions[0] = 0.0F;
      dimensions[1] = 0.0F;
      return dimensions;
   }

   @EventHandler(priority = 1)
   private void onHudRender(HudRenderEvent event) {
      if (ClientAccess.zioMinecraft.player == null) {
         java.util.Arrays.fill(this.durabilityInitialized, false);
         return;
      }
      HUDModule hudForEnabledCheck = hudModule;
      if (hudForEnabledCheck.isToggled() || this.isInHudEditor()) {
         GuiGraphicsExtractor graphicsForWidth = event.getGuiGraphics();
         int screenWidth = graphicsForWidth.guiWidth();
         GuiGraphicsExtractor graphicsForHeight = event.getGuiGraphics();
         int screenHeight = graphicsForHeight.guiHeight();

         int xOffset = 15;
         RenderUtils.clearColor(
            PostProcessShaderPass.POST_TARGET
         );
         RenderUtils.clearDepth(
            PostProcessShaderPass.POST_TARGET
         );
         RenderTargetUtils.overrideRenderTargets(
            PostProcessShaderPass.POST_TARGET
         );
         LocalPlayer playerForArmor = ClientAccess.zioMinecraft.player;
         List<ItemStack> armorStacks = EquipmentUtils.getArmorStacks(playerForArmor);

         for (int armorIndex = 3; armorIndex >= 0; armorIndex--) {
            ItemStack stack = armorStacks.get(armorIndex);
            if (!stack.isEmpty()) {

               int bottomOffset = this.getArmorBottomOffset();
               PoseStack itemPoses = event.getPoseStack();

               int itemXPosition = screenWidth / 2 + xOffset;
               int itemY = screenHeight - bottomOffset;
               int itemX = itemXPosition;
               this.renderArmorItem(itemPoses, stack, itemX, itemY);
               if (stack.isDamageableItem()) {
                  Setting<Boolean> durabilityOption = this.durabilitySetting;
                  Boolean showDurability = durabilityOption.getValue();
                  if (showDurability) {

                     Object[] durabilityFormatArguments = new Object[2];
                     int durabilityPercent = DamageUtils.getDurabilityPercent(stack);
                     durabilityFormatArguments[0] = this.getDisplayedDurability(armorIndex, durabilityPercent);
                     durabilityFormatArguments[1] = "%";
                     String durabilityFormat = "%d%s";
                     String durabilityText = String.format(durabilityFormat, durabilityFormatArguments);
                     FontsModule fontsForEnabledCheck = FontsModule.INSTANCE;
                     float percentageTextScale;
                     if (fontsForEnabledCheck.isToggled()) {

                        percentageTextScale = 0.7F;
                     } else {

                        percentageTextScale = 0.65F;
                     }

                     float textScale = percentageTextScale;
                     Setting<Boolean> percentageOptionForText = this.percentageSetting;
                     Boolean showPercentageForText = percentageOptionForText.getValue();
                     if (!showPercentageForText) {
                        int substringEnd = durabilityText.length() - 1;
                        byte substringStart = 0;
                        String textForSubstring = durabilityText;
                        durabilityText = textForSubstring.substring(substringStart, substringEnd);

                        textScale = 1.0F;
                     }

                     Setting<ColorsSchemeChoice> colorSchemeOption = colorsModule.schemeSetting;
                     ColorsSchemeChoice colorScheme = colorSchemeOption.getValue();
                     Color itemBarColor = new Color(stack.getBarColor(), false);
                     Color durabilityColor = colorScheme.transform(itemBarColor);
                     float itemTop = screenHeight - bottomOffset;

                     float textYBeforePercentageOffset = itemTop - 3.8F;
                     Setting<Boolean> percentageOptionForPosition = this.percentageSetting;
                     Boolean showPercentageForPosition = percentageOptionForPosition.getValue();
                     int plainTextYOffset;
                     if (showPercentageForPosition) {
                        plainTextYOffset = 0;
                     } else {

                        plainTextYOffset = 2;
                     }

                     float textY = textYBeforePercentageOffset - plainTextYOffset;
                     FontRenderer textRenderer = FontRenderer.INSTANCE;
                     PoseStack textPoses = event.getPoseStack();
                     float screenWidthForText = screenWidth;

                     float centeredItemX = screenWidthForText / 2.0F + xOffset;

                     float textWidth = FontRenderer.INSTANCE
                        .getWidth(durabilityText);

                     // 26.1.2: alineacion pedida por el usuario (%/icono/barra
                     // en la misma columna). El baseline centraba con
                     // +9 - textWidth*0.5*scale - scale; el termino -scale
                     // (offset de sombra del font vanilla) desplazaba el
                     // centro a itemX+9-scale, no al centro real del icono
                     // (itemX+8). Centrar exacto sobre la columna del icono
                     // de 16px; la barra durability (x+2..x+15) ya queda
                     // centrada por diseno, asi que los tres elementos
                     // comparten eje vertical.
                     float textX = itemX + 8.0F - textWidth * 0.5F * textScale;
                     Setting<Boolean> barColorOption = this.barColorSetting;
                     Boolean useBarColor = barColorOption.getValue();
                     textRenderer.drawWithShadow(
                        textPoses,
                        durabilityText,
                        textX,
                        textY,
                        textScale,
                        useBarColor ? durabilityColor : this.getHudColor(textY)
                     );
                  }
               }

               xOffset += 18;
            } else {
               this.durabilityInitialized[armorIndex] = false;
            }
         }

         RenderUtils.INSTANCE
            .flushEntities();
         RenderTargetUtils.restoreRenderTargets();
         RenderUtils.clearDepth(
            PostProcessShaderPass.POST_TARGET
         );
         RenderUtils.blitToMain(
            PostProcessShaderPass.POST_TARGET
         );
      }
   }

   private void renderArmorItem(
      PoseStack poses, ItemStack stack, int x, int y
   ) {
      // 26.1.2: icono via ruta vanilla GuiItemRenderState (GuiDrawUtils.drawItem:
      // bake en GuiItemAtlas + blit exacto (x, y, x+16, y+16) en el endFrame) y
      // barra via ColoredRectangleRenderState (GuiDrawUtils.drawDurabilityBar):
      // los estratos de GuiRenderState pintan la barra DESPUES del icono.
      // El estado capturado previo (pose translate(x+8,y+8,150)+scale(16,-16)
      // congelada + submit en task del dren) dejaba la tinta del icono corrida
      // ~+4px dentro de la celda y el shade oscuro: la matematica del bake de
      // items cambio en 26.1.2. El historial git conserva ese bloque.
      GuiDrawUtils.drawItem(poses, stack, x, y, 0.0F, false);
      if (this.barSetting.getValue() && stack.isBarVisible()) {
         GuiDrawUtils.drawDurabilityBar(new PoseStack(), stack, x, y);
      }
   }

   private int getDisplayedDurability(int slot, int percentage) {
      FloatAnimation animation = this.durabilityAnimations[slot];
      if (!this.animationSetting.getValue() || !this.durabilityInitialized[slot]) {
         animation.setValue(percentage);
         this.durabilityInitialized[slot] = true;
         return percentage;
      }
      animation.setTarget(percentage, 150L);
      return Math.round(animation.getValue());
   }

   public ArmorModule() {
      super("Armor", new String[0]);

      this.barSetting = this.add(new BooleanSetting("Bar", true));
      this.durabilitySetting = this.add(new BooleanSetting("Durability", true).markVisibilityDependency());
      this.animationSetting = this.add(new BooleanSetting("Animation", false).dependsOn(this.durabilitySetting));
      this.percentageSetting = this.add(new BooleanSetting("Percentage", true).dependsOn(new Setting<?>[]{this.durabilitySetting}));
      this.barColorSetting = this.add(new BooleanSetting("BarColor", true).dependsOn(new Setting<?>[]{this.durabilitySetting}));
      this.setRenderer(new ResizableHudWidgetRenderer(this) {
         @Override
         public boolean isEnabled() {
            return false;
         }
      });
   }

}
