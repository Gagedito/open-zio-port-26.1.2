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

import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import net.minecraft.client.player.LocalPlayer;

public class NoBobModule extends Module {
    public final Setting<Float> multiplierSetting;

    public NoBobModule() {
        super("NoBob", "Modifies the bobbing animation.", ModuleCategory.RENDER, new String[0]);
        multiplierSetting = add(new NumberSetting<>("Multiplier", 0.0F, 0.0F, 1.0F));
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        if (isWorldUnavailable()) return;

        Float multiplier = multiplierSetting.getValue();
        if (multiplier <= 0.0F) {
            LocalPlayer player = ClientAccess.zioMinecraft.player;
            player.avatarState().walkDist = 0.0F;
        }
    }
}
