/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.integrations.baritone;

import dev.zio.client.utils.chat.ChatUtils;

import dev.zio.client.ClientAccess;
import dev.zio.client.NamedFeature;
import dev.zio.client.events.bus.EventBus;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.calc.IPathingControlManager;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.IElytraProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.selection.ISelection;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.interfaces.IGoalRenderPos;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BaritoneAdapter implements ClientAccess, BaritoneBridge {
   private final ObjectSet<NamedFeature> pauseRequests = ObjectSets.synchronize(new ObjectOpenHashSet<>());

   @Override
   public void pathElytraTo(BlockPos position) {
      IElytraProcess process = BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess();
      process.pathTo(position);
   }

   @Override
   public void setGoal(BlockPos position) {
      GoalBlock goal = new GoalBlock(position);
      BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(goal);
   }

   @Override
   public boolean isPathingOrCalculating() {
      IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
      return pathing.isPathing() || pathing.getInProgress().isPresent();
   }

   @Override
   public boolean isInSelection(BlockPos position) {
      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      if (baritone.getSelectionManager().getLastSelection() == null) {
         return false;
      }

      ISelection[] selections = baritone.getSelectionManager().getSelections();
      for (ISelection selection : selections) {
         BetterBlockPos minimum = selection.min();
         BetterBlockPos maximum = selection.max();
         AABB bounds = AABB.encapsulatingFullBlocks(minimum, maximum);
         Vec3 center = position.getCenter();
         if (bounds.contains(center)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean isPauseRequested() {
      return !this.pauseRequests.isEmpty();
   }

   @Override
   public BlockPos getGoalPosition() {
      Goal goal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal();
      if (goal instanceof IGoalRenderPos renderGoal) {
         return renderGoal.getGoalPos();
      } else if (goal instanceof GoalXZ horizontalGoal) {
         double x = horizontalGoal.getX();
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         double y = player.getY();
         double z = horizontalGoal.getZ();
         return BlockPos.containing(x, y, z);
      } else {
         return null;
      }
   }

   @Override
   public void pathTo(BlockPos position) {
      GoalBlock goal = new GoalBlock(position);
      BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
   }

   @Override
   public void cancelPathing() {
      IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
      pathing.cancelEverything();
      pathing.forceCancel();
   }

   @Override
   public boolean isAvailable() {
      return true;
   }

   public BaritoneAdapter() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
      IPathingControlManager controlManager = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager();
      controlManager.registerProcess(new BaritoneAdapter.PauseProcess());
      baritone.api.Settings.Setting messageTagSetting = BaritoneAPI.getSettings().useMessageTag;
      messageTagSetting.value = false;
      BaritoneAPI.getSettings().logger.value = message -> {
         MutableComponent copiedMessage = message.copy();
         List<Component> siblings = copiedMessage.getSiblings();
         siblings.addFirst(Component.literal(" "));
         siblings = copiedMessage.getSiblings();
         siblings.addFirst(ChatUtils.createPrefix());
         Gui gui = ClientAccess.zioMinecraft.gui;
         ChatComponent chat = gui.getChat();
         GuiMessageTag tag = ChatUtils.MESSAGE_TAG;
         chat.addPlayerMessage(copiedMessage, null, tag);
      };
   }

   @Override
   public void requestPause(NamedFeature feature) {
      if (!this.pauseRequests.contains(feature)) {
         this.pauseRequests.add(feature);
      }
   }

   @Override
   public void releasePause(NamedFeature feature) {
      if (this.pauseRequests.contains(feature)) {
         this.pauseRequests.remove(feature);
      }
   }

   @Override
   public void pathForward() {
      Vec3 playerPosition = ClientAccess.zioMinecraft.player.position();
      float playerYaw = ClientAccess.zioMinecraft.player.getYRot();
      GoalXZ goal = GoalXZ.fromDirection(playerPosition, playerYaw, 5.0D);
      BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
   }

   private class PauseProcess implements IBaritoneProcess {
      private static final String displayName;

      static {
         displayName = "Zio pause service";
      }

      @Override
      public double priority() {
         return 999.0D;
      }

      @Override
      public boolean isActive() {
         return !BaritoneAdapter.this.pauseRequests.isEmpty();
      }

      @Override
      public boolean isTemporary() {
         return true;
      }

      @Override
      public void onLostControl() {
      }

      @Override
      public String displayName0() {
         return displayName;
      }

      @Override
      public PathingCommand onTick(boolean calculationFailed, boolean safeToCancel) {
         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      }
   }
}
