/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.zio.client.ClientAccess;
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.ClientContext;
import dev.zio.client.events.ItemUseEvent;
import dev.zio.client.events.LocalPlayerTickPhaseEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.StatusEffectQueryEvent;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.InventoryActionEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerAiStepEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import dev.zio.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.zio.client.gui.panels.Panel;
import dev.zio.client.gui.panels.GamePanel;
import dev.zio.client.gui.screens.ClickGuiScreen;
import dev.zio.client.gui.screens.HudEditorScreen;
import dev.zio.client.utils.InterpolatedFloat;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.inventory.ChestSearchController;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.inventory.NoSlowInventoryNcpMode;
import dev.zio.client.utils.inventory.NoSlowInventoryMode;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.exploit.MultiTaskModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.NoSlowModeChoice;
import dev.zio.client.settings.options.NoSlowStrictInventoryChoice;
import java.util.Iterator;
import java.util.function.Predicate;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public class NoSlowModule extends Module {
   private static MultiTaskModule multiTask;
   public final Setting<Boolean> guiMoveSetting;
   public final Setting<Boolean> containersSetting;
   public final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> strictSetting;
   public final Setting<NoSlowModeChoice> modeSetting;
   private final Setting<NoSlowStrictInventoryChoice> strictInventorySetting;
   private final Setting<Boolean> playerSetting;
   public final Setting<Boolean> itemsSetting;
   public final Setting<Boolean> sneakSetting;
   public final Setting<Boolean> crawlSetting;
   public final Setting<Boolean> slownessSetting;
   final Setting<Boolean> blocksSetting;
   public final Setting<Boolean> iceSetting;
   public final Setting<Boolean> soulSandSetting;
   public final Setting<Boolean> honeySetting;
   public final Setting<Boolean> berryBushSetting;
   public final Setting<Boolean> slimeSetting;
   public final Setting<Boolean> noSlimeBounceSetting;
   public boolean releasedUsingItemThisTick;
   private final InterpolatedFloat pitchInterpolation;
   private final InterpolatedFloat yawInterpolation;
   private final EnumSelectionMap<NoSlowInventoryMode> strictInventoryModes;

   @EventHandler
   private void onInventoryClickAfterStackHash(
      MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash event
   ) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onInventoryClickIntercept(event);
   }

   @EventHandler
   private void onTickBeforePassengerCheck(
      LocalPlayerTickPhaseEvent.BeforePassengerCheck event
   ) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onTickBeforePassengerCheck();
   }

   @EventHandler
   private void onBeforeContainerClick(
      MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick event
   ) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.beforeMenuClick(event);
   }

   @EventHandler(priority = -100)
   private void onLocalPlayerAiStepEvent(LocalPlayerAiStepEvent event) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onPlayerAiStep();
   }

   @EventHandler
   private void onInventoryAction(InventoryActionEvent event) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onInventoryAction(event);
   }

   @EventHandler
   private void onStatusEffectQuery(StatusEffectQueryEvent event) {
      if (this.slownessSetting.getValue()) {
         if (event.getEffect() == MobEffects.SLOWNESS) {
            event.cancel();
         }
      }
   }

   @EventHandler(priority = 200)
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
      this.releasedUsingItemThisTick = false;
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onTickStart(event);
   }

   @EventHandler
   private void onItemSlowdown(ItemUseEvent.Slowdown event) {
      if (!this.itemsSetting.getValue()) return;
      if (!ClientAccess.zioMinecraft.player.isUsingItem()) return;
      if (ClientAccess.zioMinecraft.player.isPassenger()) return;

      boolean cancelSlowdown = true;
      if (this.modeSetting.getValue() == NoSlowModeChoice.GRIMV3) {
         int useTicks = ClientAccess.zioMinecraft.player.getTicksUsingItem();
         cancelSlowdown = useTicks % 2 == 0 && useTicks > 1;
         if (ClientAccess.zioMinecraft.player.isFallFlying()) cancelSlowdown = false;
      }

      if (this.modeSetting.getValue() == NoSlowModeChoice.GRIMOLD) {
         LocalPlayer handPlayer = ClientAccess.zioMinecraft.player;
         InteractionHand usedHand = handPlayer.getUsedItemHand();
         this.handleGrimItemUse(usedHand);
      }

      if (this.modeSetting.getValue() == NoSlowModeChoice.CONSTANTIAM) cancelSlowdown = false;
      if (this.modeSetting.getValue() == NoSlowModeChoice._2B2T) {
         cancelSlowdown = this.handle2b2tItemUse();
      }
      if (cancelSlowdown) {
         event.cancel();
      }
   }

   @EventHandler
   private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
      if (this.modeSetting.getValue() != NoSlowModeChoice.CONSTANTIAM) return;
      if (!this.itemsSetting.getValue()) return;
      if (ClientAccess.zioMinecraft.player.isFallFlying()) return;
      LocalPlayer handPlayer = ClientAccess.zioMinecraft.player;
      InteractionHand usedHand = event.getHand();
      if (!handPlayer.getItemInHand(usedHand).has(DataComponents.FOOD)) return;
      BlockPos hitPos = event.getHitResult().getBlockPos();
      if (BlockInteractionUtils.requiresSneakForInteraction(hitPos)) return;
      ClientAccess.zioMinecraft.gameMode.useItem(ClientAccess.zioMinecraft.player, InteractionHand.MAIN_HAND);
      event.cancel();
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onPacketSend(event);
      if (!(event.getPacket() instanceof ServerboundMovePlayerPacket)) return;
      InteractionHand usedHand = ClientAccess.zioMinecraft.player.getUsedItemHand();
      ItemStack usedStack = ClientAccess.zioMinecraft.player.getItemInHand(usedHand);
      if (!ClientAccess.zioMinecraft.player.isUsingItem()) return;
      if (!this.itemsSetting.getValue()) return;
      if (ClientAccess.zioMinecraft.player.isHandsBusy()) return;
      if (ClientAccess.zioMinecraft.player.isFallFlying()) return;
      if (!usedStack.has(DataComponents.FOOD)) return;
      if (this.modeSetting.getValue() == NoSlowModeChoice.NCP) {
         ServerboundSetCarriedItemPacket heldSlotPacket = new ServerboundSetCarriedItemPacket(
            ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot()
         );
         PacketSender.send(heldSlotPacket);
      }
   }

   private boolean isFoodBowShieldOrPearl(ItemStack stack) {
      if (stack.has(DataComponents.FOOD)) return true;
      if (stack.is(Items.BOW)) return true;
      if (stack.is(Items.SHIELD)) return true;
      return stack.is(Items.ENDER_PEARL);
   }

    public boolean isGuiMoveAllowed() {
        if (ClientAccess.zioMinecraft.screen instanceof ClickGuiScreen) {
            Iterator<Panel> iterator = ClientContext.getClickGui().getPanels().iterator();
            while (iterator.hasNext()) {
                Panel panel = iterator.next();
                if (panel instanceof GamePanel) {
                    if (panel.isFullyExpanded()) {
                        return false;
                    }
                }
            }
            if (ClientContext.getClickGui().searchBox.isFocused()) {
                return false;
            }
            if (!ClientContext.getClickGui().searchBox.getValue().isBlank()) {
                return false;
            }
        }
        if (ClientAccess.zioMinecraft.screen instanceof AbstractContainerScreen containerScreen) {
            if (!this.containersSetting.getValue()) {
                return containerScreen.getMenu()
                    == ClientAccess.zioMinecraft.player.inventoryMenu;
            }
        }
        if (ClientAccess.zioMinecraft.screen != null
            && !(ClientAccess.zioMinecraft.screen instanceof ChatScreen)
            && !(ClientAccess.zioMinecraft.screen instanceof SignEditScreen)
            && !(ClientAccess.zioMinecraft.screen instanceof AnvilScreen)
            && !(ClientAccess.zioMinecraft.screen instanceof AbstractCommandBlockEditScreen)
            && !(ClientAccess.zioMinecraft.screen instanceof StructureBlockEditScreen)) {
            if (ChestSearchController.searchBox == null) {
                return true;
            }
            if (!ChestSearchController.searchBox.canConsumeInput()) {
                return true;
            }
        }
        return false;
    }

   public void prepareForInventoryAction() {
      if (this.releasedUsingItemThisTick) return;
      if (!multiTask.isItemUseRestricted()) return;
      if (multiTask.isInventoryAllowed()) return;
      ClientAccess.zioMinecraft.gameMode.releaseUsingItem(ClientAccess.zioMinecraft.player);
      this.releasedUsingItemThisTick = true;
   }

   public NoSlowStrictInventoryChoice getStrictInventoryMode() {
      return this.strictInventorySetting.getValue();
   }

   public boolean isUsingItemInConstantiamMode() {
      if (!ClientAccess.zioMinecraft.player.isUsingItem()) return false;
      return this.modeSetting.getValue() == NoSlowModeChoice.CONSTANTIAM;
   }

   public boolean isSlimeSlowdownDisabled() {
      if (!this.isToggled()) return false;
      return this.slimeSetting.getValue();
   }

   private boolean handle2b2tItemUse() {
      int swapSlot = this.find2b2tSwapSlot();
      if (swapSlot == -1 || ClientAccess.zioMinecraft.isLocalServer()) return true;

      float latencyTicks = ClientContext.serverStateManager.getPeakLatencyMillis() / 50.0F;
      int tickThreshold = (int)(Math.ceil(latencyTicks / ClientContext.serverStateManager.getTickRateScale()) + 2.0D);
      int useTicks = ClientAccess.zioMinecraft.player.getTicksUsingItem();
      if (useTicks == tickThreshold) {
         InventoryUtils.clickSlot(InventoryUtils.toMenuSlot(InventoryUtils.getSelectedSlot()), swapSlot, ContainerInput.SWAP);
         PacketSender.sendPlayerAction(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN);
         InventoryUtils.clickSlot(InventoryUtils.toMenuSlot(InventoryUtils.getSelectedSlot()), swapSlot, ContainerInput.SWAP);
      }
      return useTicks > tickThreshold;
   }

   private int find2b2tSwapSlot() {
      if (!ClientAccess.zioMinecraft.player.getOffhandItem().isEmpty()) return 40;
      for (int slot = 0; slot < 9; slot++) {
         if (slot != InventoryUtils.getSelectedSlot() && !ClientAccess.zioMinecraft.player.getInventory().getItem(slot).isEmpty()) {
            return slot;
         }
      }
      return -1;
   }

   private boolean isAnyRotationKeyPressed() {
      return this.isKeyPressed(GLFW.GLFW_KEY_DOWN) || this.isKeyPressed(GLFW.GLFW_KEY_UP)
         || this.isKeyPressed(GLFW.GLFW_KEY_RIGHT) || this.isKeyPressed(GLFW.GLFW_KEY_LEFT);
   }

   private void handleGrimItemUse(InteractionHand usedHand) {
      InteractionHand otherHand = PlayerStateUtils.oppositeHand(usedHand);
      ItemStack otherHandStack = ClientAccess.zioMinecraft.player.getItemInHand(otherHand);
      if (this.isFoodBowShieldOrPearl(otherHandStack)) {
         if (otherHand == InteractionHand.MAIN_HAND) {
            int selectedSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
            int nextSlot = selectedSlot + 1;
            nextSlot %= 9;
            InventoryUtils.selectHotbarSlot(nextSlot);
            PacketSender.sendUseItem(otherHand);
            InventoryUtils.selectHotbarSlot(selectedSlot);
            return;
         }
         Predicate<ItemStack> inventoryPredicate = stack -> !this.isFoodBowShieldOrPearl(stack);
         int inventorySlot = InventoryUtils.findInventorySlot(inventoryPredicate, true);
         int containerSlot = InventoryUtils.toMenuSlot(inventorySlot);
         ClientAccess.zioMinecraft.gameMode.handleContainerInput(0, containerSlot, 40, ContainerInput.SWAP, ClientAccess.zioMinecraft.player);
         PacketSender.sendUseItem(otherHand);
         ClientAccess.zioMinecraft.gameMode.handleContainerInput(0, containerSlot, 40, ContainerInput.SWAP, ClientAccess.zioMinecraft.player);
      } else {
         PacketSender.sendUseItem(otherHand);
      }
   }

   private boolean isKeyPressed(int key) {
      long handleCapture = ClientAccess.zioMinecraft.getWindow().handle();
      return GLFW.glfwGetKey(handleCapture, key) == GLFW.GLFW_PRESS;
   }

   public boolean isGrimStrictInventoryEnabled() {
      if (!this.isToggled()) return false;
      return this.strictInventorySetting.getValue() == NoSlowStrictInventoryChoice.GRIM_STRICT;
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      if (!this.guiMoveSetting.getValue() || !this.rotateSetting.getValue()) {
         return;
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      if (this.isGuiMoveAllowed()
         && !(ClientAccess.zioMinecraft.screen instanceof ClickGuiScreen)
         && !(ClientAccess.zioMinecraft.screen instanceof HudEditorScreen)) {
         if (this.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
            float pitch = Mth.lerp(event.getPartialTick(), player.xRotO, player.getXRot());
            this.pitchInterpolation.setTarget(Math.min(pitch + 8.0F, MathConstants.NINETY_DEGREES));
         } else if (this.isKeyPressed(GLFW.GLFW_KEY_UP)) {
            float pitch = Mth.lerp(event.getPartialTick(), player.xRotO, player.getXRot());
            this.pitchInterpolation.setTarget(Math.max(pitch - 8.0F, -MathConstants.NINETY_DEGREES));
         }
         if (this.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) {
            float yaw = Mth.lerp(event.getPartialTick(), player.yRotO, player.getYRot());
            this.yawInterpolation.setTarget(yaw + 8.0F);
         } else if (this.isKeyPressed(GLFW.GLFW_KEY_LEFT)) {
            float yaw = Mth.lerp(event.getPartialTick(), player.yRotO, player.getYRot());
            this.yawInterpolation.setTarget(yaw - 8.0F);
         }
         if (this.isAnyRotationKeyPressed()) {
            player.setXRot(this.pitchInterpolation.updateAndGet());
            player.setYRot(this.yawInterpolation.updateAndGet());
         }
      } else {
         this.yawInterpolation.setValue(player.getYRot());
         this.pitchInterpolation.setValue(player.getXRot());
      }
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json instanceof JsonObject moduleJson && moduleJson.get("settings") instanceof JsonObject settings) {
         String itemModeKey = this.modeSetting.getConfigName();
         String inventoryModeKey = this.strictInventorySetting.getConfigName();
         boolean retiredItemMode = isLegacyMode(settings.get(itemModeKey), "GrimStrict");
         boolean retiredInventoryMode = isLegacyMode(settings.get(inventoryModeKey), "Constantiam");
         if (retiredItemMode || retiredInventoryMode) {
            JsonObject migrated = moduleJson.deepCopy();
            JsonObject migratedSettings = migrated.getAsJsonObject("settings");

            if (retiredItemMode) {
               migratedSettings.addProperty(itemModeKey, NoSlowModeChoice.GRIMV3.getName());
            }
            if (retiredInventoryMode) {
               migratedSettings.addProperty(inventoryModeKey, NoSlowStrictInventoryChoice.NCP.getName());
            }
            json = migrated;
         }
      }
      super.fromJson(json);
   }

   private static boolean isLegacyMode(JsonElement value, String retiredName) {
      return value instanceof JsonPrimitive primitive && primitive.isString()
         && retiredName.equalsIgnoreCase(primitive.getAsString());
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      multiTask = (MultiTaskModule)registry.getModule(MultiTaskModule.class);
      NoSlowInventoryNcpMode.bindReferencedModules(registry);
   }

   public NoSlowModule() {
      super("NoSlow", "Cancels several things that may slow you down.", ModuleCategory.MOVEMENT, new String[0]);
      this.guiMoveSetting = this.add(new BooleanSetting("GuiMove", true).markVisibilityDependency());
      this.containersSetting = this.add(new BooleanSetting("Containers", true).dependsOn(this.guiMoveSetting));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", true).dependsOn(this.guiMoveSetting));
      this.strictSetting = this.add(new BooleanSetting("Strict", true).markAsGroup().markVisibilityDependency());
      this.modeSetting = this.add(new EnumSetting<>("Mode", NoSlowModeChoice.NONE).dependsOn(this.strictSetting));
      this.strictInventorySetting = this.add(new EnumSetting<>("StrictInventory", NoSlowStrictInventoryChoice.NONE).dependsOn(this.strictSetting));
      this.playerSetting = this.add(new BooleanSetting("Player", false).markAsGroup().markVisibilityDependency());
      this.itemsSetting = this.add(new BooleanSetting("Items", true).dependsOn(this.playerSetting));
      this.sneakSetting = this.add(new BooleanSetting("Sneak", false).dependsOn(this.playerSetting));
      this.crawlSetting = this.add(new BooleanSetting("Crawl", false).dependsOn(this.playerSetting));
      this.slownessSetting = this.add(new BooleanSetting("Slowness", false).dependsOn(this.playerSetting));
      this.blocksSetting = this.add(new BooleanSetting("Blocks", false).markAsGroup().markVisibilityDependency());
      Predicate<Object> iceVisibility = ignored -> this.blocksSetting.isExpanded();
      this.iceSetting = this.add(new BooleanSetting("Ice", false, iceVisibility));
      Predicate<Object> soulSandVisibility = ignored -> this.blocksSetting.isExpanded();
      this.soulSandSetting = this.add(new BooleanSetting("SoulSand", false, soulSandVisibility));
      Predicate<Object> honeyVisibility = ignored -> this.blocksSetting.isExpanded();
      this.honeySetting = this.add(new BooleanSetting("Honey", false, honeyVisibility));
      Predicate<Object> berryBushVisibility = ignored -> this.blocksSetting.isExpanded();
      this.berryBushSetting = this.add(new BooleanSetting("BerryBush", false, berryBushVisibility));
      Predicate<Object> slimeVisibility = ignored -> this.blocksSetting.isExpanded();
      this.slimeSetting = this.add(new BooleanSetting("Slime", false, slimeVisibility).markVisibilityDependency());
      this.noSlimeBounceSetting = this.add(new BooleanSetting("NoSlimeBounce", true).dependsOn(this.blocksSetting, this.slimeSetting));

      this.pitchInterpolation = new InterpolatedFloat(2.0F, true);
      this.yawInterpolation = new InterpolatedFloat(2.0F, true);
      this.strictInventoryModes = new EnumSelectionMap<>(
         this.strictInventorySetting
      );
      this.strictInventoryModes.register(NoSlowStrictInventoryChoice.NONE, new NoSlowInventoryMode(this));
      this.strictInventoryModes.register(NoSlowStrictInventoryChoice.NCP, new NoSlowInventoryNcpMode(this));
      this.strictInventoryModes.register(NoSlowStrictInventoryChoice.GRIM_STRICT, new NoSlowInventoryGrimStrictMode(this));

      this.setDrawn(false);
   }
}
