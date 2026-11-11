/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.bootstrap;

import dev.zio.client.utils.world.BlockBreakTracker;
import dev.zio.client.utils.inventory.ChestSearchController;
import dev.zio.client.modules.PlacementModule;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.ServerStateManager;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.client.BaritoneModule;
import dev.zio.client.modules.client.ColorsModule;
import dev.zio.client.modules.client.HUDModule;
import dev.zio.client.modules.combat.AnchorAuraModule;
import dev.zio.client.modules.combat.AntiFeetPlaceModule;
import dev.zio.client.modules.combat.AntiPhaseModule;
import dev.zio.client.modules.combat.AuraModule;
import dev.zio.client.modules.combat.AutoArmorModule;
import dev.zio.client.modules.combat.AutoClickerModule;
import dev.zio.client.modules.combat.AutoCrystalModule;
import dev.zio.client.modules.combat.AutoDropModule;
import dev.zio.client.modules.combat.AutoExpModule;
import dev.zio.client.modules.combat.AutoLogModule;
import dev.zio.client.modules.combat.AutoMaceModule;
import dev.zio.client.modules.combat.AutoMineModule;
import dev.zio.client.modules.combat.AutoTrapModule;
import dev.zio.client.modules.combat.BlockerModule;
import dev.zio.client.modules.combat.CriticalsModule;
import dev.zio.client.modules.combat.FeetPlaceModule;
import dev.zio.client.modules.combat.KeyPearlModule;
import dev.zio.client.modules.combat.MainhandModule;
import dev.zio.client.modules.combat.OffhandModule;
import dev.zio.client.modules.combat.PlainHoleFillPolicy;
import dev.zio.client.modules.combat.PusherModule;
import dev.zio.client.modules.combat.WebAuraModule;
import dev.zio.client.modules.exploit.ChorusControlModule;
import dev.zio.client.modules.exploit.FastLatencyModule;
import dev.zio.client.modules.exploit.PhaseModule;
import dev.zio.client.modules.exploit.ReachModule;
import dev.zio.client.modules.exploit.RocketExtenderModule;
import dev.zio.client.modules.exploit.TimerModule;
import dev.zio.client.modules.misc.AutoSignModule;
import dev.zio.client.modules.misc.BetterChatModule;
import dev.zio.client.modules.misc.MiddleClickModule;
import dev.zio.client.modules.movement.AutoWalkModule;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.modules.movement.FastClimbModule;
import dev.zio.client.modules.movement.FastFallModule;
import dev.zio.client.modules.movement.FastSwimModule;
import dev.zio.client.modules.movement.FastWebModule;
import dev.zio.client.modules.movement.FireworksModule;
import dev.zio.client.modules.movement.HighJumpModule;
import dev.zio.client.modules.movement.NoSlowModule;
import dev.zio.client.modules.movement.ObstaclePasserModule;
import dev.zio.client.modules.movement.SpeedModule;
import dev.zio.client.modules.movement.SprintModule;
import dev.zio.client.modules.movement.StepModule;
import dev.zio.client.modules.movement.VelocityModule;
import dev.zio.client.modules.movement.WarpModule;
import dev.zio.client.modules.player.AutoToolModule;
import dev.zio.client.modules.player.ChestStealerModule;
import dev.zio.client.modules.player.FastPlaceModule;
import dev.zio.client.modules.player.FreecamInput;
import dev.zio.client.modules.player.InventorySorterModule;
import dev.zio.client.modules.player.NukerModule;
import dev.zio.client.modules.player.ReplenishModule;
import dev.zio.client.modules.player.ScaffoldModule;
import dev.zio.client.modules.player.SourceFillModule;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.modules.render.ChamsModule;
import dev.zio.client.modules.render.ESPModule;
import dev.zio.client.modules.render.FreeLookModule;
import dev.zio.client.modules.render.HighlightModule;
import dev.zio.client.modules.render.HitmarkerModule;
import dev.zio.client.modules.render.LogoutSpotsModule;
import dev.zio.client.modules.render.NameTagsModule;
import dev.zio.client.modules.render.ParticlesModule;
import dev.zio.client.modules.render.ProjectileTrajectorySimulator;
import dev.zio.client.modules.render.SearchModule;
import dev.zio.client.modules.render.ShaderModule;
import dev.zio.client.modules.render.SkeletonModule;
import dev.zio.client.modules.render.TracersModule;
import dev.zio.client.settings.ColorSetting;
import dev.zio.mixin.BlockModuleBindings;
import dev.zio.mixin.BundleContentsModuleBindings;
import dev.zio.mixin.EntityModuleBindings;
import dev.zio.mixin.HoneyBlockModuleBindings;
import dev.zio.mixin.LevelModuleBindings;
import dev.zio.mixin.LiquidBlockModuleBindings;
import dev.zio.mixin.LivingEntityModuleBindings;
import dev.zio.mixin.MaterialInfoModuleBindings;
import dev.zio.mixin.MinecraftModuleBindings;
import dev.zio.mixin.MobModuleBindings;
import dev.zio.mixin.PlayerModuleBindings;
import dev.zio.mixin.RenderTypeColorModuleBindings;
import dev.zio.mixin.SlimeBlockModuleBindings;
import dev.zio.mixin.SweetBerryBushBlockModuleBindings;
import dev.zio.mixin.TextureTransformModuleBindings;
import dev.zio.mixin.TridentItemModuleBindings;
import dev.zio.mixin.WebBlockModuleBindings;
import java.util.Objects;

public final class ModuleDependencies {
   private ModuleDependencies() {}

   public static void bind(ModuleRegistry registry) {
      Objects.requireNonNull(registry, "Module manager is not initialized");
      bindMinecraftHooks(registry);
      ServerStateManager.bindModules(registry);
      BlockBreakTracker.bindModules(registry);
      RotationManager.bindReferencedModules(registry);
      SprintModule.bindReferencedModules(registry);
      ColorSetting.bindModuleRegistry(registry);
      CriticalsModule.bindModules(registry);
      HighlightModule.bindModuleDependencies(registry);
      AuraModule.bindModuleRegistry(registry);
      ScaffoldModule.bindReferencedModules(registry);
      NoSlowModule.bindReferencedModules(registry);
      VelocityModule.bindModules(registry);
      OffhandModule.bindModuleConnections(registry);
      SpeedModule.bindReferencedModules(registry);
      StepModule.bindReferencedModules(registry);
      ESPModule.bindModules(registry);
      MiddleClickModule.bindReferencedModules(registry);
      TimerModule.bindReferencedModules(registry);
      AutoLogModule.bindModules(registry);
      FastPlaceModule.bindModules(registry);
      ElytraFlyModule.bindModuleReferences(registry);
      AutoWalkModule.bindReferencedModules(registry);
      AnchorAuraModule.bindReferencedModules(registry);
      PlacementModule.bindModules(registry);
      FeetPlaceModule.bindReferencedModules(registry);
      FastFallModule.bindReferencedModules(registry);
      ReplenishModule.bindModules(registry);
      PlainHoleFillPolicy.connectSpeedMineModule(registry);
      AutoTrapModule.bindReferencedModules(registry);
      AutoCrystalModule.bindReferencedModules(registry);
      SpeedMineModule.bindModules(registry);
      AutoExpModule.bindReferencedModules(registry);
      BetterChatModule.bindReferencedModules(registry);
      HUDModule.bindReferencedModules(registry);
      ChamsModule.bindReferencedModules(registry);
      NameTagsModule.bindReferencedModules(registry);
      KeyPearlModule.bindReferencedModules(registry);
      ChestSearchController.bindReferencedModules(registry);
      ShaderModule.bindModules(registry);
      HitmarkerModule.bindCrosshair(registry);
      SearchModule.bindModules(registry);
      AutoArmorModule.bindModuleRegistry(registry);
      NukerModule.bindReferencedModules(registry);
      LogoutSpotsModule.bindFontsModule(registry);
      ChorusControlModule.bindModules(registry);
      FastSwimModule.bindReferencedModules(registry);
      SkeletonModule.bindModuleDependencies(registry);
      BlockerModule.bindReferencedModules(registry);
      WarpModule.bindReferencedModules(registry);
      AutoToolModule.bindModules(registry);
      ParticlesModule.bindColorsModule(registry);
      FreecamInput.bindModules(registry);
      TracersModule.bindReferencedModules(registry);
      FreeLookModule.bindReferencedModules(registry);
      ProjectileTrajectorySimulator.bindModules(registry);
      ReachModule.bindModuleDependencies(registry);
      WebAuraModule.bindModules(registry);
      ChestStealerModule.bindReferencedModules(registry);
      FastLatencyModule.bindReferencedModules(registry);
      AntiCheatModule.bindReferencedModules(registry);
      FastWebModule.bindModules(registry);
      RocketExtenderModule.bindReferencedModules(registry);
      ColorsModule.bindSearchModule(registry);
      AutoDropModule.bindModules(registry);
      PhaseModule.bindReferencedModules(registry);
      AutoMineModule.bindReferencedModules(registry);
      AutoSignModule.bindModules(registry);
      PusherModule.bindModules(registry);
      dev.zio.client.modules.combat.PistonCrystalModule.bindModules(registry);
      AutoClickerModule.bindModules(registry);
      FireworksModule.bindModules(registry);
      HighJumpModule.bindReferencedModules(registry);
      MainhandModule.bindModuleConnections(registry);
      AntiPhaseModule.bindMultiTaskModule(registry);
      FastClimbModule.bindModules(registry);
      SourceFillModule.bindModuleReferences(registry);
      InventorySorterModule.bindModules(registry);
      AutoMaceModule.bindReferencedModules(registry);
      AntiFeetPlaceModule.bindModules(registry);
      BaritoneModule.bindModuleDependencies(registry);
      ObstaclePasserModule.bindModules(registry);
   }

   private static void bindMinecraftHooks(ModuleRegistry registry) {
      BlockModuleBindings.bind(registry);
      LiquidBlockModuleBindings.bind(registry);
      WebBlockModuleBindings.bind(registry);
      SlimeBlockModuleBindings.bind(registry);
      SweetBerryBushBlockModuleBindings.bind(registry);
      HoneyBlockModuleBindings.bind(registry);
      MinecraftModuleBindings.bind(registry);
      BundleContentsModuleBindings.bind(registry);
      LevelModuleBindings.bind(registry);
      EntityModuleBindings.bind(registry);
      LivingEntityModuleBindings.bind(registry);
      TridentItemModuleBindings.bind(registry);
      TextureTransformModuleBindings.bind(registry);
      RenderTypeColorModuleBindings.bind(registry);
      // MC 26.1.2: el render type de material vive ahora en BakedQuad$MaterialInfo.of
      // (bake time); el binding se hace en MaterialInfoModuleBindings.
      MaterialInfoModuleBindings.bind(registry);
      MobModuleBindings.bind(registry);
      PlayerModuleBindings.bind(registry);
   }
}
