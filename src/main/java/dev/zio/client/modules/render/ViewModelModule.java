/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import dev.zio.client.ClientAccess;
import dev.zio.client.gui.screens.ViewModelEditorScreen;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import org.joml.Vector3f;

public class ViewModelModule extends Module {
   private final Setting<Boolean> adjustSetting;
   public final Setting<Boolean> instantSwapSetting;
   public final Setting<Boolean> eatingSetting;
   public final Setting<Boolean> lighterSetting;
   private final Setting<Boolean> mainHandSetting;
   public final Setting<Boolean> armSetting;
   public final Setting<Float> mainXSetting;
   public final Setting<Float> mainYSetting;
   public final Setting<Float> mainZSetting;
   public final Setting<Float> mainScaleXSetting;
   public final Setting<Float> mainScaleYSetting;
   public final Setting<Float> mainScaleZSetting;
   public final Setting<Float> mainRotateXSetting;
   public final Setting<Float> mainRotateYSetting;
   public final Setting<Float> mainRotateZSetting;
   private final Setting<Boolean> offHandSetting;
   public final Setting<Float> offXSetting;
   public final Setting<Float> offYSetting;
   public final Setting<Float> offZSetting;
   public final Setting<Float> offScaleXSetting;
   public final Setting<Float> offScaleYSetting;
   public final Setting<Float> offScaleZSetting;
   public final Setting<Float> offRotateXSetting;
   public final Setting<Float> offRotateYSetting;
   public final Setting<Float> offRotateZSetting;
   public final Setting<Boolean> miscSetting;
   public final Setting<Float> eatMultiplierSetting;
   public final Setting<Boolean> noSwaySetting;
   public final Setting<Boolean> noTridentAnimSetting;
   public final Setting<Boolean> swingProgressSetting;
   public final Setting<Boolean> staticSetting;
   private final Setting<Boolean> mainHandSwingSetting;
   public final Setting<Float> mainHandSwingProgressAmountSetting;
   private final Setting<Boolean> offHandSwingSetting;
   public final Setting<Float> offHandSwingProgressAmountSetting;
   public final Setting<Boolean> viewModelFovSetting;
   public final Setting<Integer> fovAmountSetting;
   private final Vector3f scaleVector;

   public Vector3f getScale(InteractionHand hand) {
      if (hand == InteractionHand.MAIN_HAND) {
         Vector3f mainScale = this.scaleVector;
         float mainX = this.mainScaleXSetting.getValue();
         float mainY = this.mainScaleYSetting.getValue();
         float mainZ = this.mainScaleZSetting.getValue();
         return mainScale.set(mainX, mainY, mainZ);
      } else {
         Vector3f offScale = this.scaleVector;
         float offX = this.offScaleXSetting.getValue();
         float offY = this.offScaleYSetting.getValue();
         float offZ = this.offScaleZSetting.getValue();
         return offScale.set(offX, offY, offZ);
      }
   }

   public float applySwingProgressOverride(InteractionHand hand, float originalProgress) {
      if (!this.isToggled() || !this.swingProgressSetting.getValue()) {
         return originalProgress;
      }
      if (hand == InteractionHand.MAIN_HAND && this.mainHandSwingSetting.getValue()) {
         boolean mainStatic = this.staticSetting.getValue();
         float mainProgress = mainStatic ? 0.0F : originalProgress;
         float mainMinimum = this.mainHandSwingProgressAmountSetting.getValue();
         return Math.max(mainProgress, mainMinimum);
      }
      if (hand == InteractionHand.OFF_HAND && this.offHandSwingSetting.getValue()) {
         boolean offStatic = this.staticSetting.getValue();
         float offProgress = offStatic ? 0.0F : originalProgress;
         float offMinimum = this.offHandSwingProgressAmountSetting.getValue();
         return Math.max(offProgress, offMinimum);
      }
      return originalProgress;
   }

   public ViewModelModule() {
      super("ViewModel", "Transforms your 1st person view model.", ModuleCategory.RENDER, new String[0]);
      this.adjustSetting = this.add(new BooleanSetting("Adjust", false));
      this.instantSwapSetting = this.add(new BooleanSetting("InstantSwap", false));
      this.eatingSetting = this.add(new BooleanSetting("Eating", true));
      this.lighterSetting = this.add(new BooleanSetting("Lighter", false));
      this.mainHandSetting = this.add(new BooleanSetting("MainHand", true).markAsGroup().markVisibilityDependency());
      this.armSetting = this.add(new BooleanSetting("Arm", false).dependsOn(this.mainHandSetting));
      this.mainXSetting = this.add(new NumberSetting<>("MainX", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting));
      this.mainYSetting = this.add(new NumberSetting<>("MainY", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting));
      this.mainZSetting = this.add(new NumberSetting<>("MainZ", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting));
      this.mainScaleXSetting = this.add(new NumberSetting<>("MainScaleX", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting));
      this.mainScaleYSetting = this.add(new NumberSetting<>("MainScaleY", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting));
      this.mainScaleZSetting = this.add(new NumberSetting<>("MainScaleZ", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting));
      this.mainRotateXSetting = this.add(new NumberSetting<>("MainRotateX", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting));
      this.mainRotateYSetting = this.add(new NumberSetting<>("MainRotateY", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting));
      this.mainRotateZSetting = this.add(new NumberSetting<>("MainRotateZ", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting));
      this.offHandSetting = this.add(new BooleanSetting("OffHand", true).markAsGroup().markVisibilityDependency());
      this.offXSetting = this.add(new NumberSetting<>("OffX", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting));
      this.offYSetting = this.add(new NumberSetting<>("OffY", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting));
      this.offZSetting = this.add(new NumberSetting<>("OffZ", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting));
      this.offScaleXSetting = this.add(new NumberSetting<>("OffScaleX", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting));
      this.offScaleYSetting = this.add(new NumberSetting<>("OffScaleY", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting));
      this.offScaleZSetting = this.add(new NumberSetting<>("OffScaleZ", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting));
      this.offRotateXSetting = this.add(new NumberSetting<>("OffRotateX", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting));
      this.offRotateYSetting = this.add(new NumberSetting<>("OffRotateY", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting));
      this.offRotateZSetting = this.add(new NumberSetting<>("OffRotateZ", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting));
      this.miscSetting = this.add(new BooleanSetting("Misc", false).markAsGroup().markVisibilityDependency());
      this.eatMultiplierSetting = this.add(new NumberSetting<>("EatMultiplier", 1.0F, 0.0F, 1.0F).dependsOn(this.miscSetting));
      this.noSwaySetting = this.add(new BooleanSetting("NoSway", false).dependsOn(this.miscSetting));
      this.noTridentAnimSetting = this.add(new BooleanSetting("NoTridentAnim", false).dependsOn(this.miscSetting));
      this.swingProgressSetting = this.add(new BooleanSetting("SwingProgress", false).markVisibilityDependency().dependsOn(this.miscSetting));
      this.staticSetting = this.add(new BooleanSetting("Static", true).dependsOn(this.miscSetting, this.swingProgressSetting));
      this.mainHandSwingSetting = this.add(new BooleanSetting("MainHand", false).dependsOn(this.miscSetting, this.swingProgressSetting));
      this.mainHandSwingProgressAmountSetting = this.add(new NumberSetting<>("SwingProgressAmount", 1.0F, 0.0F, 1.0F).dependsOn(this.miscSetting, this.swingProgressSetting, this.mainHandSwingSetting));
      this.offHandSwingSetting = this.add(new BooleanSetting("OffHand", false).dependsOn(this.miscSetting, this.swingProgressSetting));
      this.offHandSwingProgressAmountSetting = this.add(new NumberSetting<>("SwingProgressAmount", 1.0F, 0.0F, 1.0F).dependsOn(this.miscSetting, this.swingProgressSetting, this.offHandSwingSetting));
      this.viewModelFovSetting = this.add(new BooleanSetting("ViewModelFov", false).markVisibilityDependency().dependsOn(this.miscSetting));
      this.fovAmountSetting = this.add(new NumberSetting<>("FovAmount", 90, 70, 180).dependsOn(this.miscSetting, this.viewModelFovSetting));

      this.scaleVector = new Vector3f(1.0F);

      this.mainHandSwingSetting.setConfigName("SwingMainHand");

      this.offHandSwingSetting.setConfigName("SwingOffHand");

      this.offHandSwingProgressAmountSetting.setConfigName("SwingProgressAmountOffHand");
      Setting<Boolean> callbackSetting = this.adjustSetting;
      Runnable callback = () -> {
         Boolean openAdjust = this.adjustSetting.getValue();
         if (openAdjust) {
            Minecraft minecraft = ClientAccess.zioMinecraft;
            ViewModelEditorScreen screen = new ViewModelEditorScreen(this);
            minecraft.setScreen(screen);
            this.adjustSetting.setValue(false);
         }
      };
      callbackSetting.setChangeCallback(callback);
   }

}
