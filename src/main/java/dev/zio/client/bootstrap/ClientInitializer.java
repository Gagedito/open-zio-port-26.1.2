/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.bootstrap;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.ZioClient;
import dev.zio.client.events.ClientInitializationEvent;
import dev.zio.client.integrations.baritone.BaritoneAdapter;
import dev.zio.client.integrations.baritone.DisabledBaritoneBridge;
import dev.zio.client.integrations.xaero.DisabledWaypointAdapter;
import dev.zio.client.managers.AliasRegistry;
import dev.zio.client.managers.AutoPresetRegistry;
import dev.zio.client.managers.BlockColorRegistry;
import dev.zio.client.managers.ChatFilterRegistry;
import dev.zio.client.managers.CommandRegistry;
import dev.zio.client.managers.ConfigurationManager;
import dev.zio.client.managers.CrystalInteractionManager;
import dev.zio.client.managers.GlintRegistry;
import dev.zio.client.managers.HoleManager;
import dev.zio.client.managers.HudModuleRegistry;
import dev.zio.client.managers.InputOverrideManager;
import dev.zio.client.managers.InventoryStateManager;
import dev.zio.client.managers.KitRegistry;
import dev.zio.client.managers.MacroRegistry;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.managers.MovementPredictionManager;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.ServerStateManager;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.managers.SoundManager;
import dev.zio.client.managers.StashRegistry;
import dev.zio.client.managers.SwingInteractionManager;
import dev.zio.client.managers.TaskScheduler;
import dev.zio.client.managers.TickRateController;
import dev.zio.client.managers.TradeRegistry;
import dev.zio.client.managers.WaypointRegistry;
import dev.zio.client.managers.WebhookManager;
import dev.zio.client.managers.WorldStateManager;
import dev.zio.client.render.FeatureBoxRenderer;
import dev.zio.client.render.FontSupport;
import dev.zio.client.render.text.LanguageTable;
import dev.zio.client.utils.FrameTiming;
import dev.zio.client.utils.combat.TotemPopTracker;
import dev.zio.client.utils.entity.PlayerStateTracker;
import dev.zio.client.utils.inventory.CustomItemRegistries;
import dev.zio.client.utils.movement.MovementSpeedTracker;
import dev.zio.client.utils.world.BlockBreakTracker;
import dev.zio.client.utils.world.MapDataCache;
import java.util.Locale;
import java.util.Locale.Category;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class ClientInitializer {
   private boolean managersInitialized;
   private boolean clientInitialized;
   private boolean deferredReloadDone;

   public void initialize() {
      requireEnvironment();
      if (this.managersInitialized) {
         return;
      }
      // Constructors publish services in dependency order before modules bind to them.
      ClientContext.baritoneBridge = FabricLoader.getInstance().isModLoaded("baritone-meteor")
         ? new BaritoneAdapter() : new DisabledBaritoneBridge();
      ClientContext.waypointAdapter = new DisabledWaypointAdapter();
      FontSupport.initialize();
      ClientContext.languageTable = new LanguageTable();
      ClientContext.inputOverrides = new InputOverrideManager();
      ClientContext.blockColorRegistry = new BlockColorRegistry();
      ClientContext.playerStateTracker = new PlayerStateTracker();
      ClientContext.serverStateManager = new ServerStateManager();
      ClientContext.blockBreakTracker = new BlockBreakTracker();
      ClientContext.tickRateController = new TickRateController();
      ClientContext.playerTickController = new TickRateController.PlayerTicks();
      ClientContext.movementPredictionManager = new MovementPredictionManager();
      ClientContext.holeManager = new HoleManager();
      ClientContext.totemPopTracker = new TotemPopTracker();
      ClientContext.movementSpeedTracker = new MovementSpeedTracker();
      ClientContext.rotationManager = new RotationManager();
      ClientContext.waypointRegistry = new WaypointRegistry();
      ClientContext.featureBoxRenderer = new FeatureBoxRenderer();
      ClientContext.taskScheduler = new TaskScheduler();
      ClientContext.moduleRegistry = new ModuleRegistry();
      ClientContext.hudModuleRegistry = new HudModuleRegistry();
      ClientContext.tradeRegistry = new TradeRegistry();
      ClientContext.commandRegistry = new CommandRegistry();
      ClientContext.frameTiming = new FrameTiming();
      ClientContext.macroRegistry = new MacroRegistry();
      ClientContext.socialRegistry = new SocialRegistry();
      ClientContext.soundManager = new SoundManager();
      ClientContext.inventoryStateManager = new InventoryStateManager();
      ClientContext.stashRegistry = new StashRegistry();
      ClientContext.webhookManager = new WebhookManager();
      ClientContext.worldStateManager = new WorldStateManager();
      ClientContext.crystalInteractionManager = new CrystalInteractionManager();
      ClientContext.mapDataCache = new MapDataCache();
      ClientContext.kitRegistry = new KitRegistry();
      ClientContext.aliasRegistry = new AliasRegistry();
      ClientContext.chatFilterRegistry = new ChatFilterRegistry();
      ClientContext.glintRegistry = new GlintRegistry();
      ClientContext.autoPresetRegistry = new AutoPresetRegistry();
      ClientContext.swingManager = new SwingInteractionManager();
      Locale.setDefault(Category.FORMAT, Locale.US);
      this.managersInitialized = true;
      ZioClient.LOGGER.info("Zio main initialization completed.");
   }

   public void initializeClient() {
      requireEnvironment();
      if (!this.managersInitialized) {
         throw new IllegalStateException("Zio client initialization requires the main phase.");
      }
      if (this.clientInitialized) {
         return;
      }
      ModuleDependencies.bind(ClientContext.moduleRegistry);
      ClientAccess.eventBus.post(new ClientInitializationEvent());
      dev.zio.client.parity.ParityDump.dumpIfRequested();
      ItemComponentsBootstrapper.bindItemComponents();
      ClientContext.soundManager.reload();
      ClientContext.configurationManager = new ConfigurationManager();
      ConfigurationManager configuration = ClientContext.configurationManager;
      CustomItemRegistries.initialize();
      configuration.load();
      this.clientInitialized = true;
      ZioClient.LOGGER.info("Zio client initialization completed.");
   }

   /**
    * MC 26.1.2: llamado por RegistryDataCollectorMixin cuando vanilla completa
    * el bind de data components (primer join al mundo). Completa la instalacion
    * de registries custom que quedo pendiente y resuelve los valores de
    * configuracion que la carga del boot no pudo mapear a contenido vacio.
    */
   public static void onGameRegistriesComponentsBound() {
      if (!CustomItemRegistries.hasPendingInstall()) {
         return;
      }

      ClientInitializer initializer = ZioClient.getInitializer();
      CustomItemRegistries.retryPendingInstall();
      initializer.reloadDeferredConfiguration();
   }

   private synchronized void reloadDeferredConfiguration() {
      if (!this.clientInitialized || this.deferredReloadDone) {
         return;
      }

      this.deferredReloadDone = true;
      ZioClient.LOGGER.info("Game registries bound; reloading configuration for deferred custom registries.");
      ClientContext.configurationManager.load();
   }

   private static void requireEnvironment() {
      FabricLoader fabric = FabricLoader.getInstance();
      if (fabric.getEnvironmentType() != EnvType.CLIENT || Runtime.version().feature() < 21) {
         throw new IllegalStateException("Zio requires the Fabric client environment and Java 21 or newer.");
      }
      String minecraft = fabric.getModContainer("minecraft")
         .orElseThrow(() -> new IllegalStateException("Minecraft mod container is missing."))
         .getMetadata().getVersion().getFriendlyString();
      if (!"1.21.11".equals(minecraft) && !"26.1.2".equals(minecraft)) {
         throw new IllegalStateException("Zio requires Minecraft 1.21.11 or 26.1.2; found " + minecraft);
      }
   }
}
