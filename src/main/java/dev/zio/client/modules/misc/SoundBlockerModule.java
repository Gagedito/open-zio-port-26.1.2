/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.events.EventHandler;
import dev.zio.client.events.SoundEnginePlayEvent;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import java.util.Set;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class SoundBlockerModule extends Module {
   private final Setting<Set<SoundEvent>> soundsSetting;

   public SoundBlockerModule() {
      super("SoundBlocker", "Blocks certain sounds from being played.", ModuleCategory.MISC, new String[0]);
      this.soundsSetting = this.add(new RegistrySetSetting<>("Sounds", BuiltInRegistries.SOUND_EVENT, new SoundEvent[0]));
   }

   @EventHandler
   private void onSoundEnginePlayEvent(SoundEnginePlayEvent event) {
      for (SoundEvent sound : this.soundsSetting.getValue()) {
         Identifier blockedIdentifier = sound.location();
         SoundInstance playingSound = event.getSoundInstance();
         if (blockedIdentifier.equals(playingSound.getIdentifier())) {
            event.cancel();
            break;
         }
      }
   }
}
