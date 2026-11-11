/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.hud;

import java.util.Set;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.modules.HudWidgetModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.HudAnchor;
import dev.zio.client.settings.options.ListFilterMode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

public class EffectsModule extends HudWidgetModule {
   private final Setting<ListFilterMode> selectionSetting;
   private final Setting<Set<MobEffect>> effectsSetting;
   private final Setting<Boolean> vanillaSetting;
   private final List<EffectHudRow> effectRows;

   @Override
   public void onEnable() {
      this.effectRows.clear();
      BuiltInRegistries.MOB_EFFECT.listElements().forEach(
         effectReference -> this.effectRows.add(
            new EffectHudRow(effectReference, () -> {
               LocalPlayer player = ClientAccess.zioMinecraft.player;
               if (!player.getActiveEffectsMap().containsKey(effectReference)) {
                  return false;
               }
               Setting<ListFilterMode> selectionOption = this.selectionSetting;
               ListFilterMode selection = selectionOption.getValue();
               MobEffect effect = effectReference.value();
               Setting<Set<MobEffect>> effectsOption = this.effectsSetting;
               return ListFilterMode.matches(selection, effect, effectsOption);
            })
         )
      );
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent ignoredEvent) {
      this.effectRows.sort(
         Comparator.comparing(
            (EffectHudRow row) -> {
               MobEffect effect = row.getEffectHolder().value();
               return effect.getDisplayName().getString();
            }
         )
      );
   }

   public EffectsModule() {
      super("Effects", new String[]{"potions"});
      this.selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.ANY));
      this.effectsSetting = this.add(new RegistrySetSetting<>("Effects", BuiltInRegistries.MOB_EFFECT));
      BooleanSetting vanillaOption = new BooleanSetting("Vanilla", false);
      this.vanillaSetting = this.add(vanillaOption);
      this.effectRows = new ArrayList<>();
      HudTextListRenderer rowRenderer = new HudTextListRenderer(this, this.effectRows) {
         @Override
         public Color getRowColor(float rowPosition, HudTextRow row) {
            EffectHudRow effectRow = (EffectHudRow)row;
            MobEffect effect = effectRow.getEffectHolder().value();
            Setting<Boolean> vanillaOption = EffectsModule.this.vanillaSetting;
            Boolean useVanillaColor = vanillaOption.getValue();
            if (useVanillaColor) {
               return new Color(getVanillaEffectColor(effect), false);
            } else {
               return super.getRowColor(rowPosition, row);
            }
         }

         private int getVanillaEffectColor(MobEffect effect) {
            return effect.getColor();
         }
      };
      this.setRenderer(rowRenderer);
      this.getRenderer()
         .setAnchor(HudAnchor.BOTTOM_RIGHT);
      Setting<Set<MobEffect>> effectsForVisibility = this.effectsSetting;
      Predicate<Object> visibilityPredicate = ignoredValue -> {
         return this.selectionSetting.getValue() != ListFilterMode.ANY;
      };
      effectsForVisibility.addVisibilityPredicate(visibilityPredicate);
   }
}
