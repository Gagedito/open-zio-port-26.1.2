/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.managers;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.NamedFeature;
import dev.zio.client.events.InputPressEvent;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.utils.ExperimentalFeatures;
import dev.zio.client.integrations.baritone.BaritoneBridge;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.modules.PlacementModule;
import dev.zio.client.modules.HudWidgetModule;
import dev.zio.client.modules.Keybind;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.client.BaritoneModule;
import dev.zio.client.modules.movement.ObstaclePasserModule;
import dev.zio.client.settings.JsonConfigurable;
import dev.zio.client.settings.NamedEntry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ModuleRegistry extends Registry<Module> implements ClientAccess, JsonConfigurable {
   private final Object2ObjectOpenHashMap<Class<?>, Module> modulesByClass = new Object2ObjectOpenHashMap<>();

   public List<PlacementModule> getPlacementModules() {
      ArrayList<PlacementModule> placementModules = new ArrayList<>();
      List<Module> registeredModules = (List<Module>)this.entries;
      registeredModules.forEach((Consumer<Module>)module -> {
         if (module instanceof PlacementModule placementModule) {
            placementModules.add(placementModule);
         }
      });
      return placementModules;
   }

   private void registerPathfindingModules() {
      BaritoneBridge bridge = ClientContext.baritoneBridge;
      BaritoneModule baritoneModule = new BaritoneModule();
      this.registerModule(baritoneModule);
      if (bridge.isAvailable()) {
         ObstaclePasserModule obstaclePasserModule = new ObstaclePasserModule();
         this.registerModule(obstaclePasserModule);
      }
   }

   public ModuleRegistry() {
      ClientAccess.eventBus.subscribe(this);
      if (!this.registerModule(new dev.zio.client.modules.render.AmbienceModule())) throw new IllegalStateException("Registration rejected AmbienceModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AnchorAuraModule())) throw new IllegalStateException("Registration rejected AnchorAuraModule");
      if (!this.registerModule(new dev.zio.client.modules.render.AnimationsModule())) throw new IllegalStateException("Registration rejected AnimationsModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.AnnouncerModule())) throw new IllegalStateException("Registration rejected AnnouncerModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.AntiAFKModule())) throw new IllegalStateException("Registration rejected AntiAFKModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.AntiAimModule())) throw new IllegalStateException("Registration rejected AntiAimModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AntiBotModule())) throw new IllegalStateException("Registration rejected AntiBotModule");
      if (!this.registerModule(new dev.zio.client.modules.client.AntiCheatModule())) throw new IllegalStateException("Registration rejected AntiCheatModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.AntiCyrillicModule())) throw new IllegalStateException("Registration rejected AntiCyrillicModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AntiFeetPlaceModule())) throw new IllegalStateException("Registration rejected AntiFeetPlaceModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.AntiHungerModule())) throw new IllegalStateException("Registration rejected AntiHungerModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AntiPearlModule())) throw new IllegalStateException("Registration rejected AntiPearlModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AntiPhaseModule())) throw new IllegalStateException("Registration rejected AntiPhaseModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.AntiQuitModule())) throw new IllegalStateException("Registration rejected AntiQuitModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.AntiSpamModule())) throw new IllegalStateException("Registration rejected AntiSpamModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.AntiVanishModule())) throw new IllegalStateException("Registration rejected AntiVanishModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.AvoidModule())) throw new IllegalStateException("Registration rejected AvoidModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.ArrowsModule())) throw new IllegalStateException("Registration rejected ArrowsModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AuraModule())) throw new IllegalStateException("Registration rejected AuraModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AutoArmorModule())) throw new IllegalStateException("Registration rejected AutoArmorModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AutoBowReleaseModule())) throw new IllegalStateException("Registration rejected AutoBowReleaseModule");
      if (!this.registerModule(new dev.zio.client.modules.player.AutoBreedModule())) throw new IllegalStateException("Registration rejected AutoBreedModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AutoClickerModule())) throw new IllegalStateException("Registration rejected AutoClickerModule");
      if (!this.registerModule(new dev.zio.client.modules.player.AutoCraftModule())) throw new IllegalStateException("Registration rejected AutoCraftModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AutoCrystalModule())) throw new IllegalStateException("Registration rejected AutoCrystalModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AutoDropModule())) throw new IllegalStateException("Registration rejected AutoDropModule");
      if (!this.registerModule(new dev.zio.client.modules.player.AutoEatModule())) throw new IllegalStateException("Registration rejected AutoEatModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AutoExpModule())) throw new IllegalStateException("Registration rejected AutoExpModule");
      if (!this.registerModule(new dev.zio.client.modules.player.AutoFarmModule())) throw new IllegalStateException("Registration rejected AutoFarmModule");
      if (!this.registerModule(new dev.zio.client.modules.player.AutoFishModule())) throw new IllegalStateException("Registration rejected AutoFishModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AutoLogModule())) throw new IllegalStateException("Registration rejected AutoLogModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AutoMaceModule())) throw new IllegalStateException("Registration rejected AutoMaceModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AutoMineModule())) throw new IllegalStateException("Registration rejected AutoMineModule");
      if (!this.registerModule(new dev.zio.client.modules.player.AutoMountModule())) throw new IllegalStateException("Registration rejected AutoMountModule");
      if (!this.registerModule(new dev.zio.client.modules.player.AutoNameTagModule())) throw new IllegalStateException("Registration rejected AutoNameTagModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AutoPotModule())) throw new IllegalStateException("Registration rejected AutoPotModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.AutoReconnectModule())) throw new IllegalStateException("Registration rejected AutoReconnectModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.AutoRespawnModule())) throw new IllegalStateException("Registration rejected AutoRespawnModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.AutoSignModule())) throw new IllegalStateException("Registration rejected AutoSignModule");
      if (!this.registerModule(new dev.zio.client.modules.player.AutoTameModule())) throw new IllegalStateException("Registration rejected AutoTameModule");
      if (!this.registerModule(new dev.zio.client.modules.player.AutoToolModule())) throw new IllegalStateException("Registration rejected AutoToolModule");
      if (!this.registerModule(new dev.zio.client.modules.player.AutoTradeModule())) throw new IllegalStateException("Registration rejected AutoTradeModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.AutoTrapModule())) throw new IllegalStateException("Registration rejected AutoTrapModule");
      if (!this.registerModule(new dev.zio.client.modules.player.AutoTemplateModule())) throw new IllegalStateException("Registration rejected AutoTemplateModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.AutoWalkModule())) throw new IllegalStateException("Registration rejected AutoWalkModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.BedAuraModule())) throw new IllegalStateException("Registration rejected BedAuraModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.BetterChatModule())) throw new IllegalStateException("Registration rejected BetterChatModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.BlockerModule())) throw new IllegalStateException("Registration rejected BlockerModule");
      if (!this.registerModule(new dev.zio.client.modules.render.BlurModule())) throw new IllegalStateException("Registration rejected BlurModule");
      if (!this.registerModule(new dev.zio.client.modules.render.BordersModule())) throw new IllegalStateException("Registration rejected BordersModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.BowAimModule())) throw new IllegalStateException("Registration rejected BowAimModule");
      if (!this.registerModule(new dev.zio.client.modules.render.BreakHighlightModule())) throw new IllegalStateException("Registration rejected BreakHighlightModule");
      if (!this.registerModule(new dev.zio.client.modules.client.CapesModule())) throw new IllegalStateException("Registration rejected CapesModule");
      if (!this.registerModule(new dev.zio.client.modules.render.ChamsModule())) throw new IllegalStateException("Registration rejected ChamsModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.ChatFilterModule())) throw new IllegalStateException("Registration rejected ChatFilterModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.ChestSearchBarModule())) throw new IllegalStateException("Registration rejected ChestSearchBarModule");
      if (!this.registerModule(new dev.zio.client.modules.player.ChestStealerModule())) throw new IllegalStateException("Registration rejected ChestStealerModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.ChorusControlModule())) throw new IllegalStateException("Registration rejected ChorusControlModule");
      if (!this.registerModule(new dev.zio.client.modules.client.ColorsModule())) throw new IllegalStateException("Registration rejected ColorsModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.CoordLoggerModule())) throw new IllegalStateException("Registration rejected CoordLoggerModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.CriticalsModule())) throw new IllegalStateException("Registration rejected CriticalsModule");
      if (!this.registerModule(new dev.zio.client.modules.render.CrosshairModule())) throw new IllegalStateException("Registration rejected CrosshairModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.CustomDeathTextModule())) throw new IllegalStateException("Registration rejected CustomDeathTextModule");
      if (!this.registerModule(new dev.zio.client.modules.client.DiscordModule())) throw new IllegalStateException("Registration rejected DiscordModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.WebhookNotifsModule())) throw new IllegalStateException("Registration rejected WebhookNotifsModule");
      if (!this.registerModule(new dev.zio.client.modules.render.ESPModule())) throw new IllegalStateException("Registration rejected ESPModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.ElytraFlyModule())) throw new IllegalStateException("Registration rejected ElytraFlyModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.EntityControlModule())) throw new IllegalStateException("Registration rejected EntityControlModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.ExtraScreenshotModule())) throw new IllegalStateException("Registration rejected ExtraScreenshotModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.ExtraTabModule())) throw new IllegalStateException("Registration rejected ExtraTabModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.FakeLagModule())) throw new IllegalStateException("Registration rejected FakeLagModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.FakeVanillaModule())) throw new IllegalStateException("Registration rejected FakeVanillaModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.FastClimbModule())) throw new IllegalStateException("Registration rejected FastClimbModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.FastFallModule())) throw new IllegalStateException("Registration rejected FastFallModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.FastLatencyModule())) throw new IllegalStateException("Registration rejected FastLatencyModule");
      if (!this.registerModule(new dev.zio.client.modules.player.FastPlaceModule())) throw new IllegalStateException("Registration rejected FastPlaceModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.FastStairsModule())) throw new IllegalStateException("Registration rejected FastStairsModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.FastSwimModule())) throw new IllegalStateException("Registration rejected FastSwimModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.FastWebModule())) throw new IllegalStateException("Registration rejected FastWebModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.FeetPlaceModule())) throw new IllegalStateException("Registration rejected FeetPlaceModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.FireworksModule())) throw new IllegalStateException("Registration rejected FireworksModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.FishTrapModule())) throw new IllegalStateException("Registration rejected FishTrapModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.FlightModule())) throw new IllegalStateException("Registration rejected FlightModule");
      if (!this.registerModule(new dev.zio.client.modules.client.FontsModule())) throw new IllegalStateException("Registration rejected FontsModule");
      if (!this.registerModule(new dev.zio.client.modules.render.FreeLookModule())) throw new IllegalStateException("Registration rejected FreeLookModule");
      if (!this.registerModule(new dev.zio.client.modules.player.FreecamModule())) throw new IllegalStateException("Registration rejected FreecamModule");
      if (!this.registerModule(new dev.zio.client.modules.render.GlintModule())) throw new IllegalStateException("Registration rejected GlintModule");
      if (!this.registerModule(new dev.zio.client.modules.client.HUDModule())) throw new IllegalStateException("Registration rejected HUDModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.HeavenModule())) throw new IllegalStateException("Registration rejected HeavenModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.HighJumpModule())) throw new IllegalStateException("Registration rejected HighJumpModule");
      if (!this.registerModule(new dev.zio.client.modules.render.HighlightModule())) throw new IllegalStateException("Registration rejected HighlightModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.HitboxDesyncModule())) throw new IllegalStateException("Registration rejected HitboxDesyncModule");
      if (!this.registerModule(new dev.zio.client.modules.render.HitmarkerModule())) throw new IllegalStateException("Registration rejected HitmarkerModule");
      if (!this.registerModule(new dev.zio.client.modules.render.HoleESPModule())) throw new IllegalStateException("Registration rejected HoleESPModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.HoleFillModule())) throw new IllegalStateException("Registration rejected HoleFillModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.HoleSnapModule())) throw new IllegalStateException("Registration rejected HoleSnapModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.IllegalDisconnectModule())) throw new IllegalStateException("Registration rejected IllegalDisconnectModule");
      if (!this.registerModule(new dev.zio.client.modules.player.InventoryCleanerModule())) throw new IllegalStateException("Registration rejected InventoryCleanerModule");
      if (!this.registerModule(new dev.zio.client.modules.player.InventorySorterModule())) throw new IllegalStateException("Registration rejected InventorySorterModule");
      if (!this.registerModule(new dev.zio.client.modules.player.InventoryTweaksModule())) throw new IllegalStateException("Registration rejected InventoryTweaksModule");
      if (!this.registerModule(new dev.zio.client.modules.player.ItemSaverModule())) throw new IllegalStateException("Registration rejected ItemSaverModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.JesusModule())) throw new IllegalStateException("Registration rejected JesusModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.KeyPearlModule())) throw new IllegalStateException("Registration rejected KeyPearlModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.KillEffectsModule())) throw new IllegalStateException("Registration rejected KillEffectsModule");
      if (!this.registerModule(new dev.zio.client.modules.render.LogoutSpotsModule())) throw new IllegalStateException("Registration rejected LogoutSpotsModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.LongJumpModule())) throw new IllegalStateException("Registration rejected LongJumpModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.MainhandModule())) throw new IllegalStateException("Registration rejected MainhandModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.MiddleClickModule())) throw new IllegalStateException("Registration rejected MiddleClickModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.MultiTaskModule())) throw new IllegalStateException("Registration rejected MultiTaskModule");
      if (!this.registerModule(new dev.zio.client.modules.player.NameProtectModule())) throw new IllegalStateException("Registration rejected NameProtectModule");
      if (!this.registerModule(new dev.zio.client.modules.render.NameTagsModule())) throw new IllegalStateException("Registration rejected NameTagsModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.NewChunksModule())) throw new IllegalStateException("Registration rejected NewChunksModule");
      if (!this.registerModule(new dev.zio.client.modules.render.NoBobModule())) throw new IllegalStateException("Registration rejected NoBobModule");
      if (!this.registerModule(new dev.zio.client.modules.player.NoEffectsModule())) throw new IllegalStateException("Registration rejected NoEffectsModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.NoFallModule())) throw new IllegalStateException("Registration rejected NoFallModule");
      if (!this.registerModule(new dev.zio.client.modules.player.NoGlitchBlocksModule())) throw new IllegalStateException("Registration rejected NoGlitchBlocksModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.NoHitDelayModule())) throw new IllegalStateException("Registration rejected NoHitDelayModule");
      if (!this.registerModule(new dev.zio.client.modules.player.NoInteractModule())) throw new IllegalStateException("Registration rejected NoInteractModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.NoJumpDelayModule())) throw new IllegalStateException("Registration rejected NoJumpDelayModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.NoMineAnimationModule())) throw new IllegalStateException("Registration rejected NoMineAnimationModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.NoNarratorModule())) throw new IllegalStateException("Registration rejected NoNarratorModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.NoPacketKickModule())) throw new IllegalStateException("Registration rejected NoPacketKickModule");
      if (!this.registerModule(new dev.zio.client.modules.render.NoRenderModule())) throw new IllegalStateException("Registration rejected NoRenderModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.NoRotateModule())) throw new IllegalStateException("Registration rejected NoRotateModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.NoSlowModule())) throw new IllegalStateException("Registration rejected NoSlowModule");
      if (!this.registerModule(new dev.zio.client.modules.client.NotificationsModule())) throw new IllegalStateException("Registration rejected NotificationsModule");
      if (!this.registerModule(new dev.zio.client.modules.player.NukerModule())) throw new IllegalStateException("Registration rejected NukerModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.OffhandModule())) throw new IllegalStateException("Registration rejected OffhandModule");
      if (!this.registerModule(new dev.zio.client.modules.render.ParticlesModule())) throw new IllegalStateException("Registration rejected ParticlesModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.PhaseModule())) throw new IllegalStateException("Registration rejected PhaseModule");
      if (!this.registerModule(new dev.zio.client.modules.render.PhaseESPModule())) throw new IllegalStateException("Registration rejected PhaseESPModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.PingSpoofModule())) throw new IllegalStateException("Registration rejected PingSpoofModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.PortalGodModeModule())) throw new IllegalStateException("Registration rejected PortalGodModeModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.PistonCrystalModule())) throw new IllegalStateException("Registration rejected PistonCrystalModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.PusherModule())) throw new IllegalStateException("Registration rejected PusherModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.RaytraceBypassModule())) throw new IllegalStateException("Registration rejected RaytraceBypassModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.ReachModule())) throw new IllegalStateException("Registration rejected ReachModule");
      if (!this.registerModule(new dev.zio.client.modules.player.ReplenishModule())) throw new IllegalStateException("Registration rejected ReplenishModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.RocketExtenderModule())) throw new IllegalStateException("Registration rejected RocketExtenderModule");
      if (!this.registerModule(new dev.zio.client.modules.player.RotationLockModule())) throw new IllegalStateException("Registration rejected RotationLockModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.SafeWalkModule())) throw new IllegalStateException("Registration rejected SafeWalkModule");
      if (!this.registerModule(new dev.zio.client.modules.player.ScaffoldModule())) throw new IllegalStateException("Registration rejected ScaffoldModule");
      if (!this.registerModule(new dev.zio.client.modules.render.SearchModule())) throw new IllegalStateException("Registration rejected SearchModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.SelfFillModule())) throw new IllegalStateException("Registration rejected SelfFillModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.SelfTrapModule())) throw new IllegalStateException("Registration rejected SelfTrapModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.SelfWebModule())) throw new IllegalStateException("Registration rejected SelfWebModule");
      if (!this.registerModule(new dev.zio.client.modules.client.SettingsModule())) throw new IllegalStateException("Registration rejected SettingsModule");
      if (!this.registerModule(new dev.zio.client.modules.render.ShaderModule())) throw new IllegalStateException("Registration rejected ShaderModule");
      if (!this.registerModule(new dev.zio.client.modules.render.SkeletonModule())) throw new IllegalStateException("Registration rejected SkeletonModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.SkinFlickerModule())) throw new IllegalStateException("Registration rejected SkinFlickerModule");
      if (!this.registerModule(new dev.zio.client.modules.render.SkyboxModule())) throw new IllegalStateException("Registration rejected SkyboxModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.SoundBlockerModule())) throw new IllegalStateException("Registration rejected SoundBlockerModule");
      if (!this.registerModule(new dev.zio.client.modules.player.SourceFillModule())) throw new IllegalStateException("Registration rejected SourceFillModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.SpammerModule())) throw new IllegalStateException("Registration rejected SpammerModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.SpeedModule())) throw new IllegalStateException("Registration rejected SpeedModule");
      if (!this.registerModule(new dev.zio.client.modules.player.SpeedMineModule())) throw new IllegalStateException("Registration rejected SpeedMineModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.SprintModule())) throw new IllegalStateException("Registration rejected SprintModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.StashFinderModule())) throw new IllegalStateException("Registration rejected StashFinderModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.StepModule())) throw new IllegalStateException("Registration rejected StepModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.SwingModule())) throw new IllegalStateException("Registration rejected SwingModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.TimerModule())) throw new IllegalStateException("Registration rejected TimerModule");
      if (!this.registerModule(new dev.zio.client.modules.render.TooltipsModule())) throw new IllegalStateException("Registration rejected TooltipsModule");
      if (!this.registerModule(new dev.zio.client.modules.render.TracersModule())) throw new IllegalStateException("Registration rejected TracersModule");
      if (!this.registerModule(new dev.zio.client.modules.render.TrailsModule())) throw new IllegalStateException("Registration rejected TrailsModule");
      if (!this.registerModule(new dev.zio.client.modules.render.TrajectoriesModule())) throw new IllegalStateException("Registration rejected TrajectoriesModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.TridentModule())) throw new IllegalStateException("Registration rejected TridentModule");
      if (!this.registerModule(new dev.zio.client.modules.render.TunnelsModule())) throw new IllegalStateException("Registration rejected TunnelsModule");
      if (!this.registerModule(new dev.zio.client.modules.client.UIModule())) throw new IllegalStateException("Registration rejected UIModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.VelocityModule())) throw new IllegalStateException("Registration rejected VelocityModule");
      if (!this.registerModule(new dev.zio.client.modules.render.ViewClipModule())) throw new IllegalStateException("Registration rejected ViewClipModule");
      if (!this.registerModule(new dev.zio.client.modules.render.ViewModelModule())) throw new IllegalStateException("Registration rejected ViewModelModule");
      if (!this.registerModule(new dev.zio.client.modules.misc.VisualRangeModule())) throw new IllegalStateException("Registration rejected VisualRangeModule");
      if (!this.registerModule(new dev.zio.client.modules.render.VoidESPModule())) throw new IllegalStateException("Registration rejected VoidESPModule");
      if (!this.registerModule(new dev.zio.client.modules.movement.WarpModule())) throw new IllegalStateException("Registration rejected WarpModule");
      if (!this.registerModule(new dev.zio.client.modules.render.WaypointsModule())) throw new IllegalStateException("Registration rejected WaypointsModule");
      if (!this.registerModule(new dev.zio.client.modules.combat.WebAuraModule())) throw new IllegalStateException("Registration rejected WebAuraModule");
      if (!this.registerModule(new dev.zio.client.modules.exploit.XCarryModule())) throw new IllegalStateException("Registration rejected XCarryModule");
      if (!this.registerModule(new dev.zio.client.modules.render.XrayModule())) throw new IllegalStateException("Registration rejected XrayModule");
      if (!this.registerModule(new dev.zio.client.modules.render.ZoomModule())) throw new IllegalStateException("Registration rejected ZoomModule");

      this.registerPathfindingModules();
      List<Module> registeredModules = (List<Module>)this.entries;
      registeredModules.sort(Comparator.comparing((Function<Module, String>)NamedFeature::getName));
   }

   @Override
   public boolean register(Object entry) {
      Module module = (Module)entry;
      return this.registerModule(module);
   }

   @Override
   public boolean unregister(Object entry) {
      Module module = (Module)entry;

      return this.unregisterModule(module);
   }

   public boolean unregisterModule(Module module) {
      Object2ObjectOpenHashMap<Class<?>, Module> cache = this.modulesByClass;
      Class moduleClass = module.getClass();
      cache.remove(moduleClass, module);
      return super.unregisterEntry(module);
   }

   @Override
   public void fromJson(JsonElement config) {
      List<Module> registeredModules = (List<Module>)this.getRegistry();
      Iterator<Module> modulesIterator = registeredModules.iterator();

      while (modulesIterator.hasNext()) {
         Module module = modulesIterator.next();
         if (!(module instanceof HudWidgetModule)) {
            try {
               JsonObject configObject = config.getAsJsonObject();
               String moduleConfigName = module.getConfigName();
               JsonElement moduleConfig = dev.zio.client.config.LegacyBranding.findModuleConfig(configObject, moduleConfigName);
               module.fromJson(moduleConfig);
            } catch (Exception ignoredFailure) {
            }
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject config = new JsonObject();

      config.addProperty("client", "zio");

      config.addProperty("category", "all");
      List<Module> registeredModules = (List<Module>)this.getRegistry();
      Iterator<Module> modulesIterator = registeredModules.iterator();

      while (modulesIterator.hasNext()) {
         Module module = modulesIterator.next();
         if (!(module instanceof HudWidgetModule)) {
            try {
               String moduleConfigName = module.getConfigName();
               JsonElement moduleConfig = module.toJson();
               config.add(moduleConfigName, moduleConfig);
            } catch (Exception ignoredFailure) {
            }
         }
      }

      return config;
   }

   @Override
   public String getConfigName() {

      return "modules.json";
   }

   @Override
   public boolean registerEntry(NamedEntry entry) {
      Module module = (Module)entry;
      return this.registerModule(module);
   }

   @Deprecated
   @Override
   public NamedEntry getByClass(Class moduleType) {
      return this.getModule(moduleType);
   }

   public List getModulesByCategory(ModuleCategory category) {
      List<Module> registeredModules = (List<Module>)this.entries;
      Stream<Module> modules = registeredModules.stream();
      return modules.filter(module -> module.getCategory() == category).collect(Collectors.toList());
   }

   @EventHandler
   private void onInputPress(InputPressEvent inputEvent) {

      if (KeyUtils.isPhysicalKeyDown(292)) {
         return;
      }
      List<Module> registeredModules = (List<Module>)this.entries;
      Iterator<Module> modulesIterator = registeredModules.iterator();

      while (modulesIterator.hasNext()) {
         Module module = modulesIterator.next();
         Keybind keybindForDeviceCheck = module.getKeybind();

         boolean mouseBinding = keybindForDeviceCheck.mouse();
         if (mouseBinding != inputEvent.isMouse()) {
            continue;
         }
         Keybind keybindForCodeCheck = module.getKeybind();

         int bindingCode = keybindForCodeCheck.code();
         if (bindingCode != inputEvent.getCode()) {
            continue;
         }
         Keybind keybindForModeCheck = module.getKeybind();

         if (keybindForModeCheck.mode()
            == Keybind.Mode.TOGGLE) {
            module.toggle();
         }
      }
   }

   @EventHandler(priority = -200)
   private void onWorldRender(WorldRenderEvent.Main renderEvent) {
      List<Module> registeredModules = (List<Module>)this.entries;
      Iterator<Module> modulesIterator = registeredModules.iterator();

      while (modulesIterator.hasNext()) {
         Module module = modulesIterator.next();
         Keybind keybind = module.getKeybind();

         boolean holdBinding = keybind.mode()
            == Keybind.Mode.HOLD;

         if (keybind.isUnbound()) {
            continue;
         }

         if (keybind.mode() == Keybind.Mode.TOGGLE) {
            continue;
         }
         if (ClientAccess.zioMinecraft.screen != null && module.isToggled() != holdBinding) {
            continue;
         }
         boolean shouldToggle = holdBinding == KeyUtils.isPhysicallyDown(keybind);
         module.setToggled(shouldToggle);
      }
   }

   @Override
   public boolean unregisterEntry(NamedEntry entry) {
      Module module = (Module)entry;

      return this.unregisterModule(module);
   }

   public boolean registerModule(Module module) {
      Class moduleType = module.getClass();
      if (ExperimentalFeatures.isClassHidden(moduleType)) {
         return false;
      }
      ExperimentalFeatures.unregisterHiddenSettings(module);
      Object2ObjectOpenHashMap<Class<?>, Module> cache = this.modulesByClass;
      Class moduleClass = module.getClass();
      cache.put(moduleClass, module);
      return super.registerEntry(module);
   }

   @Deprecated
   public Module getModule(Class moduleType) {
      Object2ObjectOpenHashMap<Class<?>, Module> cache = this.modulesByClass;
      return cache.get(moduleType);
   }

}
