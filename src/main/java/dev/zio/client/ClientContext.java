/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client;

import dev.zio.client.gui.screens.ClickGuiScreen;
import dev.zio.client.gui.screens.HudEditorScreen;
import dev.zio.client.integrations.baritone.BaritoneBridge;
import dev.zio.client.integrations.xaero.WaypointAdapter;
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
import dev.zio.client.managers.ShaderRegistry;
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
import dev.zio.client.modules.Module;
import dev.zio.client.render.FeatureBoxRenderer;
import dev.zio.client.render.text.LanguageTable;
import dev.zio.client.utils.FrameTiming;
import dev.zio.client.utils.combat.TotemPopTracker;
import dev.zio.client.utils.entity.PlayerStateTracker;
import dev.zio.client.utils.movement.MovementSpeedTracker;
import dev.zio.client.utils.world.BlockBreakTracker;
import dev.zio.client.utils.world.MapDataCache;
import java.util.Objects;

public final class ClientContext {
   public static TickRateController tickRateController;
   public static ConfigurationManager configurationManager;
   public static RotationManager rotationManager;
   public static volatile ModuleRegistry moduleRegistry;
   public static CommandRegistry commandRegistry;
   public static FrameTiming frameTiming;
   public static ServerStateManager serverStateManager;
   public static SocialRegistry socialRegistry;
   public static WaypointRegistry waypointRegistry;
   public static HoleManager holeManager;
   public static SoundManager soundManager;
   public static MacroRegistry macroRegistry;
   public static InventoryStateManager inventoryStateManager;
   public static MovementPredictionManager movementPredictionManager;
   public static TotemPopTracker totemPopTracker;
   public static MovementSpeedTracker movementSpeedTracker;
   public static PlayerStateTracker playerStateTracker;
   public static FeatureBoxRenderer featureBoxRenderer;
   public static TickRateController.PlayerTicks playerTickController;
   public static StashRegistry stashRegistry;
   public static TaskScheduler taskScheduler;
   public static HudModuleRegistry hudModuleRegistry;
   public static WebhookManager webhookManager;
   public static WorldStateManager worldStateManager;
   public static CrystalInteractionManager crystalInteractionManager;
   public static MapDataCache mapDataCache;
   public static KitRegistry kitRegistry;
   public static AliasRegistry aliasRegistry;
   public static ChatFilterRegistry chatFilterRegistry;
   public static GlintRegistry glintRegistry;
   public static BlockBreakTracker blockBreakTracker;
   public static ShaderRegistry shaderRegistry;
   public static TradeRegistry tradeRegistry;
   public static BlockColorRegistry blockColorRegistry;
   public static AutoPresetRegistry autoPresetRegistry;
   public static InputOverrideManager inputOverrides;
   public static LanguageTable languageTable;
   public static SwingInteractionManager swingManager;
   public static BaritoneBridge baritoneBridge;
   public static WaypointAdapter waypointAdapter;
   public static ClickGuiScreen clickGui;
   private static HudEditorScreen hudEditor;

   private ClientContext() {
   }

   public static <T extends Module> T getModule(Class<T> moduleType) {
      Objects.requireNonNull(moduleType, "Module type");
      ModuleRegistry registry = moduleRegistry;
      return registry == null ? null : moduleType.cast(registry.getModule(moduleType));
   }

   public static ClickGuiScreen getClickGui() {
      if (clickGui == null) {
         clickGui = new ClickGuiScreen();
      }

      return clickGui;
   }

   public static HudEditorScreen getHudEditor() {
      if (hudEditor == null) {
         hudEditor = new HudEditorScreen();
      }

      return hudEditor;
   }
}
