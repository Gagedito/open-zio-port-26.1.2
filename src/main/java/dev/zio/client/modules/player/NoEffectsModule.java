/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import dev.zio.client.events.StatusEffectQueryEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;

import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

public class NoEffectsModule extends Module {
    private final Setting<Set<MobEffect>> effectsSetting;

    public NoEffectsModule() {
        super("NoEffects", "Allows you to cancel certain effects.", ModuleCategory.PLAYER);

        effectsSetting = add(new RegistrySetSetting<>("Effects", BuiltInRegistries.MOB_EFFECT, new MobEffect[0]));
        List<MobEffect> defaults = List.of(MobEffects.LEVITATION.value(), MobEffects.SLOW_FALLING.value());
        selectedEffects().addAll(defaults);
        defaultEffects().addAll(defaults);
    }

    public boolean blocksEffect(Holder<MobEffect> effect) {
        return selectedEffects().contains(effect.value());
    }

    @EventHandler
    private void onEffectQuery(StatusEffectQueryEvent event) {
        if (blocksEffect(event.getEffect())) {
            event.cancel();
        }
    }

    @SuppressWarnings("unchecked")
    private Set<MobEffect> selectedEffects() {
        return effectsSetting.getValue();
    }

    @SuppressWarnings("unchecked")
    private Set<MobEffect> defaultEffects() {
        return effectsSetting.getDefaultValue();
    }
}
