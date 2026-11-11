/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.AbstractClientPlayerTickEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.utils.combat.ExtraBreakTarget;
import dev.zio.client.audio.BuiltinSound;
import dev.zio.client.audio.SoundClip;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.utils.collections.NamedIdentifier;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.IdentifierSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class AutoWalkModule extends Module {
   private static SpeedMineModule speedMineModule;
   private final Setting<Boolean> autoJumpSetting;
   private final Setting<Boolean> stuckNotifySetting;
   private final Setting<NamedIdentifier> soundSetting;
   private final Setting<Float> volumeSetting;
   private final Setting<Boolean> pauseSetting;
   private final Setting<Boolean> miningSetting;
   private final Setting<Boolean> eatingSetting;
   private int stuckTicks;

   private boolean isMining() {
      if (!speedMineModule.isToggled()) {
         return ClientAccess.zioMinecraft.gameMode.isDestroying();
      }
      BlockPos miningPosition = speedMineModule.getMiningPos();
      ExtraBreakTarget extraTarget = speedMineModule.getExtraBreakTarget();
      return WorldUtils.canMineBlock(miningPosition)
         || extraTarget != null && WorldUtils.canMineBlock(extraTarget.getPosition());
   }

   static {
      ClientAccess.eventBus.subscribeStatic(AutoWalkModule.class);
   }

   private boolean shouldPauseForAction() {
      return this.eatingSetting.getValue() && ClientAccess.zioMinecraft.player.isUsingItem()
         || this.miningSetting.getValue() && this.isMining();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (this.shouldPauseForAction()) {
         return;
      }
      double verticalDistance = Math.abs(ClientAccess.zioMinecraft.player.getY() - ClientAccess.zioMinecraft.player.yo);
      if (ClientContext.movementSpeedTracker.getCurrentSpeed() != 0.0 || !(verticalDistance < 0.2D)) {
         this.stuckTicks = 0;
         return;
      }
      if (this.stuckTicks == 5 && this.stuckNotifySetting.getValue()) {
         ChatUtils.printChat(Component.literal("You are stuck!"), ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.HIGH);
         SoundClip sound = ClientContext.soundManager.getSound(this.soundSetting.getValue());
         sound.play(this.volumeSetting.getValue());
      }
      this.stuckTicks++;
   }

   @EventHandler
   private void onAbstractClientPlayerTickEvent(AbstractClientPlayerTickEvent event) {
      if (!this.shouldPauseForAction()) {
         ClientContext.inputOverrides.requestForwardMovement(1.0F);
         ClientContext.inputOverrides.requestForward(true);
         if (this.autoJumpSetting.getValue()) {
            ClientContext.inputOverrides.requestJump(true);
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      speedMineModule = (SpeedMineModule)registry.getModule(SpeedMineModule.class);
   }

   public AutoWalkModule() {
      super("AutoWalk", "Lets your forward key rest.", ModuleCategory.MOVEMENT, new String[0]);
      this.autoJumpSetting = this.add(new BooleanSetting("AutoJump", false));
      this.stuckNotifySetting = this.add(new BooleanSetting("StuckNotify", false).markVisibilityDependency());
      this.soundSetting = this.add(new IdentifierSetting("Sound", BuiltinSound.WARNING.getIdentifier()).dependsOn(this.stuckNotifySetting));
      this.volumeSetting = this.add(new NumberSetting<>("Volume", 1.0F, 0.0F, 1.0F).dependsOn(this.stuckNotifySetting));
      this.pauseSetting = this.add(new BooleanSetting("Pause", false).markAsGroup());
      this.miningSetting = this.add(new BooleanSetting("Mining", false).dependsOn(this.pauseSetting));
      this.eatingSetting = this.add(new BooleanSetting("Eating", false).dependsOn(this.pauseSetting));
   }
}
