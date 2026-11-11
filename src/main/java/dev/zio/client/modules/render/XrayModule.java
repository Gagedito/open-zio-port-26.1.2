/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import java.util.Set;

import dev.zio.client.ClientAccess;
import dev.zio.client.render.IrisShaderBridge;
import dev.zio.client.render.GameOptionsOverrides;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.ListFilterMode;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class XrayModule extends Module {
   private static XrayModule instance;
   private final Setting<Set<Block>> blocksSetting;
   private final Setting<ListFilterMode> selectionSetting;
   public final Setting<Integer> alphaSetting;
   private final Setting<Boolean> bypassSetting;
   private double previousGamma;

   @Override
   public void onEnable() {
      OptionInstance<Double> previousGammaOption = ClientAccess.zioMinecraft.options.gamma();
      this.previousGamma = previousGammaOption.get();
      OptionInstance<Double> gammaOption = ClientAccess.zioMinecraft.options.gamma();
      GameOptionsOverrides.forceSetOptionValue(gammaOption, 1000.0D);
   }

   @Override
   public void onToggle() {
      WorldUtils.reloadRenderer();
   }

   @Override
   public void onDisable() {
      Options options = ClientAccess.zioMinecraft.options;
      OptionInstance<Double> gammaOption = options.gamma();
      double savedGamma = this.previousGamma;
      GameOptionsOverrides.forceSetOptionValue(gammaOption, savedGamma);
   }

   private void reloadRendererIfEnabled() {
      if (this.isToggled()) {
         WorldUtils.reloadRenderer();
      }
   }

   public boolean shouldRenderBlock(
      BlockPos position, Block block
   ) {
      if (block == null || position == null || this.isWorldUnavailable()) {
         return false;
      }
      ListFilterMode selectionMode = this.selectionSetting.getValue();
      if (selectionMode.isAny()) {
         return false;
      }

      ListFilterMode mode = this.selectionSetting.getValue();
      Setting<Set<Block>> blocks = this.blocksSetting;
      if (!ListFilterMode.matches(mode, block, blocks)) {
         return false;
      }
      Setting<Boolean> bypass = this.bypassSetting;
      if (!(bypass.getValue())) {
         return true;
      }

      return this.hasNonOccludingNeighbor(position);
   }

   public boolean shouldHideUnselectedBlocks() {
      Setting<Integer> alpha = this.alphaSetting;
      if (!alpha.isAtMaximum()) {
         if (IrisShaderBridge.isHandRendererAvailable() && IrisShaderBridge.isShaderPackPresent()) {
            return true;
         }
      }

      return (this.alphaSetting.getValue()) == 0;
   }

   public static XrayModule getInstance() {
      return instance;
   }

   private boolean hasNonOccludingNeighbor(BlockPos position) {

      for (Direction direction : Direction.values()) {
         ClientLevel level = ClientAccess.zioMinecraft.level;
         BlockPos neighborPosition = position.relative(direction);
         BlockState neighborState = level.getBlockState(neighborPosition);
         if (!neighborState.canOcclude()) {
            return true;
         }
      }

      return false;
   }

   public XrayModule() {
      super(
         "Xray",
         "Wallhack on ores.",
         ModuleCategory.RENDER,
         new String[0]
      );
      this.blocksSetting = this.add(new RegistrySetSetting<>("Blocks", BuiltInRegistries.BLOCK, new Block[]{
         Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
         Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
         Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
         Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS
      }));
      this.selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.WHITELIST));
      this.alphaSetting = this.add(new NumberSetting<>("Alpha", 0, 0, 100).withUnit("%"));
      this.bypassSetting = this.add(new BooleanSetting("Bypass", false));
      instance = this;
      this.selectionSetting.setChangeCallback(this::reloadRendererIfEnabled);
      this.blocksSetting.setChangeCallback(this::reloadRendererIfEnabled);
      this.bypassSetting.setChangeCallback(this::reloadRendererIfEnabled);
      this.alphaSetting.setChangeCallback(this::reloadRendererIfEnabled);
   }
}
