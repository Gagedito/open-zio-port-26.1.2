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
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Options;
import net.minecraft.world.entity.player.PlayerModelPart;

public class SkinFlickerModule extends Module {
   private static final PlayerModelPart[] HORIZONTAL_PART_ORDER;
   private static final PlayerModelPart[] VERTICAL_PART_ORDER;
   private final Setting<ModeChoice> modeSetting;
   private final Setting<Boolean> capeSetting;
   private final Setting<Float> delaySetting;
   private final Map<PlayerModelPart, Boolean> savedModelParts;
   private final int modelPartCount;
   private final ElapsedTimer flickerTimer;
   private boolean optionsDirty;
   private int sequenceIndex;

   static {

      PlayerModelPart[] horizontalParts = new PlayerModelPart[(6)];
      horizontalParts[0] = PlayerModelPart.LEFT_SLEEVE;
      horizontalParts[1] = PlayerModelPart.JACKET;

      horizontalParts[2] = PlayerModelPart.HAT;

      horizontalParts[3] = PlayerModelPart.LEFT_PANTS_LEG;

      horizontalParts[4] = PlayerModelPart.RIGHT_PANTS_LEG;

      horizontalParts[(5)] = PlayerModelPart.RIGHT_SLEEVE;
      HORIZONTAL_PART_ORDER = horizontalParts;

      PlayerModelPart[] verticalParts = new PlayerModelPart[6];
      verticalParts[0] = PlayerModelPart.HAT;
      verticalParts[1] = PlayerModelPart.JACKET;

      verticalParts[(2)] = PlayerModelPart.LEFT_SLEEVE;

      verticalParts[3] = PlayerModelPart.RIGHT_SLEEVE;

      verticalParts[4] = PlayerModelPart.LEFT_PANTS_LEG;

      verticalParts[(5)] = PlayerModelPart.RIGHT_PANTS_LEG;
      VERTICAL_PART_ORDER = verticalParts;
   }

   @Override
   public void onEnable() {

      for (PlayerModelPart part : PlayerModelPart.values()) {
         Map<PlayerModelPart, Boolean> savedPartsSnapshot = this.savedModelParts;
         Options options = ClientAccess.zioMinecraft.options;
         boolean enabled = options.isModelPartEnabled(part);
         savedPartsSnapshot.put(part, enabled);
      }
   }

   @Override
   public void onDisable() {
      Map<PlayerModelPart, Boolean> savedPartsSnapshot = this.savedModelParts;
      Options options = ClientAccess.zioMinecraft.options;
      savedPartsSnapshot.forEach(options::setModelPart);
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      ElapsedTimer timerSnapshot = this.flickerTimer;
      Setting<Float> delaySettingSnapshot = this.delaySetting;
      Float delayValue = delaySettingSnapshot.getValue();
      double delaySeconds = delayValue.floatValue();
      TimeUnit delayUnit = TimeUnit.SECONDS;
      if (timerSnapshot.hasElapsed(delaySeconds, delayUnit)) {
         Setting<ModeChoice> modeSettingSnapshot = this.modeSetting;
         SkinFlickerModule.ModeChoice mode = modeSettingSnapshot.getValue();
         switch (mode.ordinal()) {
            case 0:

               for (PlayerModelPart part : PlayerModelPart.values()) {
                  if (this.shouldTogglePart(part)) {
                     this.togglePart(part);
                  }
               }
               break;
            case 1:
            case 2:
               Setting<ModeChoice> sequenceModeSnapshot = this.modeSetting;
               PlayerModelPart[] partOrder = sequenceModeSnapshot.getValue() == SkinFlickerModule.ModeChoice.HORIZONTAL
                  ? HORIZONTAL_PART_ORDER
                  : VERTICAL_PART_ORDER;
               int indexSnapshot = this.sequenceIndex;

               PlayerModelPart sequencePart = partOrder[indexSnapshot % (6)];
               if (this.shouldTogglePart(sequencePart)) {
                  this.togglePart(sequencePart);
                  int nextIndex = this.sequenceIndex + 1;

                  this.sequenceIndex = nextIndex % (6);
               }
               break;
            case 3:
               PlayerModelPart[] allParts = PlayerModelPart.values();
               ThreadLocalRandom randomSnapshot = ThreadLocalRandom.current();
               int randomBound = this.modelPartCount;
               PlayerModelPart randomPart = allParts[randomSnapshot.nextInt(randomBound)];
               if (this.shouldTogglePart(randomPart)) {
                  this.togglePart(randomPart);
               }
         }

         if (this.optionsDirty) {
            Options options = ClientAccess.zioMinecraft.options;
            options.broadcastOptions();
            this.optionsDirty = false;
         }

         timerSnapshot = this.flickerTimer;

         timerSnapshot.reset();
      }
   }

   private void togglePart(PlayerModelPart part) {
      this.optionsDirty = true;
      Options writeOptionsSnapshot = ClientAccess.zioMinecraft.options;
      Options readOptions = ClientAccess.zioMinecraft.options;
      boolean enabled = !readOptions.isModelPartEnabled(part);
      writeOptionsSnapshot.setModelPart(part, enabled);
   }

   private boolean shouldTogglePart(PlayerModelPart part) {
      Setting<Boolean> capeSettingSnapshot = this.capeSetting;
      Boolean includeCape = capeSettingSnapshot.getValue();
      return includeCape || part != PlayerModelPart.CAPE;
   }

   public SkinFlickerModule() {
      super("SkinFlicker", "Flicks your skin parts.", ModuleCategory.MISC, new String[]{"skinblinker"});
      this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.FULL));
      this.capeSetting = this.add(new BooleanSetting("Cape", false));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 1.0F, 0.0F, 10.0F).withUnit("s"));
      this.savedModelParts = new HashMap<>();
      this.modelPartCount = PlayerModelPart.values().length;
      this.flickerTimer = new ElapsedTimer();
   }

   public enum ModeChoice implements NamedEntry {
      FULL("Full"),
      VERTICAL("Vertical"),
      HORIZONTAL("Horizontal"),
      RANDOM("Random");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
