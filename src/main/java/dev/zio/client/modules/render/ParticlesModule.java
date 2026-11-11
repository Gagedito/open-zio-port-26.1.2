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
import dev.zio.client.ClientContext;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.ParticleEngineAddEvent;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.render.ParticleInitialAlpha;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.ColorsModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.awt.Color;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.particle.AshParticle;
import net.minecraft.client.particle.BaseAshSmokeParticle;
import net.minecraft.client.particle.CritParticle;
import net.minecraft.client.particle.FireworkParticles.SparkParticle;
import net.minecraft.client.particle.FireworkParticles.Starter;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.particle.WhiteAshParticle;
import net.minecraft.client.player.LocalPlayer;

public class ParticlesModule extends Module {
   private final Random random = new Random();
   private static ColorsModule colors;
   public final Setting<Boolean> totemsSetting;
   public final Setting<Float> totemsScaleSetting;
   public final Setting<Float> totemsVelocitySetting;
   public final Setting<Color> totemsColor1Setting;
   public final Setting<Color> totemsColor2Setting;
   public final Setting<Boolean> ignoreSelfSetting;
   public final Setting<Boolean> rocketsSetting;
   public final Setting<Float> rocketScaleSetting;
   public final Setting<Color> rocketColorSetting;
   public final Setting<Boolean> damageSetting;
   public final Setting<Float> damageScaleSetting;
   public final Setting<Float> damageVelocitySetting;
   public final Setting<Color> damageColorSetting;
   public final Setting<Boolean> portalSetting;
   public final Setting<Float> portalScaleSetting;
   public final Setting<Color> portalColorSetting;
   public final Setting<Boolean> dustSetting;
   public final Setting<Color> fromSetting;
   public final Setting<Color> toSetting;

   static {

      colors = (ColorsModule)ClientContext.getModule(ColorsModule.class);
   }

   private void setParticleColor(SingleQuadParticle particle, int argb) {
      float alpha = ((argb >>> 24) & 0xFF) / 255.0F;
      float red = ((argb >>> 16) & 0xFF) / 255.0F;
      float green = ((argb >>> 8) & 0xFF) / 255.0F;
      float blue = (argb & 0xFF) / 255.0F;

      particle.setColor(red, green, blue);
      ((ParticleInitialAlpha)particle).zio$setInitialAlpha(alpha);
   }

   @EventHandler
   private void onParticleEngineAddEvent(ParticleEngineAddEvent event) {
      if (event.getParticle() instanceof TotemParticle totem) {
         if (this.ignoreSelfSetting.getValue()) {
            LocalPlayer player = ClientAccess.zioMinecraft.player;
            if (player.getBoundingBox().intersects(totem.getBoundingBox())) {
               event.cancel();
            }
         }

         if (!this.totemsSetting.getValue()) {
            return;
         }

         Setting<Color> colorSetting = this.random.nextInt(4) == 0
            ? this.totemsColor1Setting
            : this.totemsColor2Setting;
         this.setParticleColor(totem, this.getParticleColor(colorSetting));
         totem.scale(this.totemsScaleSetting.getValue());
         totem.setPower(this.totemsVelocitySetting.getValue());
      }

      if (this.rocketsSetting.getValue()) {
         if (event.getParticle() instanceof Starter starter) {
            starter.scale(this.rocketScaleSetting.getValue());
         }
         if (event.getParticle() instanceof SparkParticle spark) {
            this.setParticleColor(spark, this.getParticleColor(this.rocketColorSetting));
            spark.scale(this.rocketScaleSetting.getValue());
         }
      }

      if (this.damageSetting.getValue()
         && event.getParticle() instanceof CritParticle damageParticle) {
         this.setParticleColor(damageParticle, (this.damageColorSetting.getValue()).getRGB());
         damageParticle.scale(this.damageScaleSetting.getValue());
         damageParticle.setPower(this.damageVelocitySetting.getValue());
      }

      if (this.portalSetting.getValue()
         && event.getParticle() instanceof PortalParticle portalParticle) {
         this.setParticleColor(portalParticle, (this.portalColorSetting.getValue()).getRGB());
         portalParticle.scale(this.portalScaleSetting.getValue());
      }

      if (this.dustSetting.getValue()
         && event.getParticle() instanceof BaseAshSmokeParticle dustParticle) {
         if (dustParticle instanceof WhiteAshParticle) {
            Color whiteAshColor = (this.toSetting.getValue()).brighter();
            this.setParticleColor(dustParticle, whiteAshColor.getRGB());
         }
         if (dustParticle instanceof AshParticle) {
            Color from = this.fromSetting.getValue();
            Color to = this.toSetting.getValue();
            Color blended = ColorUtils.blend(from, to, ThreadLocalRandom.current().nextFloat());
            this.setParticleColor(dustParticle, blended.getRGB());
         }
      }
   }

   private int getParticleColor(Setting<Color> setting) {
      Color color = setting.getValue();
      ColorSetting colorSetting = (ColorSetting)setting;
      if (!colorSetting.isRainbow()) {
         return color.getRGB();
      }

      float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
      int period = (int)(MathConstants.FULL_TURN_DEGREES * colors.getRainbowPeriod());
      period = Math.max(period, 1);
      int offset = this.random.nextInt(period);
      Color rainbow = ColorUtils.rainbow(period, offset, hsb[1], hsb[2], color.getAlpha());
      return rainbow.getRGB();
   }

   public static void bindColorsModule(ModuleRegistry registry) {
      colors = (ColorsModule)registry.getModule(
         ColorsModule.class
      );
   }

   public ParticlesModule() {
      super("Particles", "Allows you to adjust certain particles.", ModuleCategory.RENDER, new String[0]);
      this.totemsSetting = this.add(new BooleanSetting("Totems", false).markVisibilityDependency());
      this.totemsScaleSetting = this.add(new NumberSetting<>("TotemsScale", 1.0F, 0.0F, 2.0F).dependsOn(this.totemsSetting));
      this.totemsVelocitySetting = this.add(new NumberSetting<>("TotemsVelocity", 1.0F, 0.0F, 5.0F).dependsOn(this.totemsSetting));
      this.totemsColor1Setting = this.add(new ColorSetting("TotemsColor1", new Color(-4351489, true)).dependsOn(this.totemsSetting));
      this.totemsColor2Setting = this.add(new ColorSetting("TotemsColor2", new Color(-9286761, true)).dependsOn(this.totemsSetting));
      this.ignoreSelfSetting = this.add(new BooleanSetting("IgnoreSelf", false).dependsOn(this.totemsSetting));
      this.rocketsSetting = this.add(new BooleanSetting("Rockets", false).markVisibilityDependency());
      this.rocketScaleSetting = this.add(new NumberSetting<>("RocketScale", 1.0F, 0.0F, 2.0F).dependsOn(this.rocketsSetting));
      this.rocketColorSetting = this.add(new ColorSetting("RocketColor", new Color(-4351489, true)).dependsOn(this.rocketsSetting));
      this.damageSetting = this.add(new BooleanSetting("Damage", false).markVisibilityDependency());
      this.damageScaleSetting = this.add(new NumberSetting<>("DamageScale", 1.0F, 0.0F, 2.0F).dependsOn(this.damageSetting));
      this.damageVelocitySetting = this.add(new NumberSetting<>("DamageVelocity", 1.0F, 0.0F, 5.0F).dependsOn(this.damageSetting));
      this.damageColorSetting = this.add(new ColorSetting("DamageColor", new Color(-4351489, true)).dependsOn(this.damageSetting));
      this.portalSetting = this.add(new BooleanSetting("Portal", false).markVisibilityDependency());
      this.portalScaleSetting = this.add(new NumberSetting<>("PortalScale", 1.0F, 0.0F, 2.0F).dependsOn(this.portalSetting));
      this.portalColorSetting = this.add(new ColorSetting("PortalColor", new Color(-4351489, true)).dependsOn(this.portalSetting));
      this.dustSetting = this.add(new BooleanSetting("Dust", false).markVisibilityDependency());
      this.fromSetting = this.add(new ColorSetting("From", new Color(-4351489, true)).dependsOn(this.dustSetting));
      this.toSetting = this.add(new ColorSetting("To", new Color(-4351489, true)).dependsOn(this.dustSetting));

      this.setDrawn(false);
   }

}
