/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.inventory;

import dev.zio.client.utils.combat.BoundingBoxUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.world.BlockInteractionUtils;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.client.AntiCheatModule;
import dev.zio.client.modules.combat.AutoMineModule;
import dev.zio.client.modules.combat.AutoMineTargetSelector;
import dev.zio.client.modules.combat.AutoTrapModule;
import dev.zio.client.settings.Setting;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class AutoMineHeadProvider extends AutoMineProviderBase {
   private static AutoTrapModule autoTrap;
   private static AntiCheatModule antiCheat;

   private boolean isAlternateHeadModeEnabled() {
      return antiCheat.is2b2tEnabled();
   }

   public AutoMineHeadProvider(AutoMineModule module) {
      super(module);
   }

   static {
      autoTrap = (AutoTrapModule)ClientContext.getModule(AutoTrapModule.class);
      antiCheat = (AntiCheatModule)ClientContext.getModule(AntiCheatModule.class);
   }

   @Override
   public void collectCandidates(AutoMineCandidates candidates) {
      AutoMineTargetSelector targetSelector = this.autoMine.targetSelector;
      Player target = targetSelector.selectTarget();
      if (target == null || this.hasNoCollisionBelow(target)) {
         return;
      }

      Setting<Boolean> inCrawlSetting = this.autoMine.inCrawlSetting;
      Boolean inCrawl = inCrawlSetting.getValue();
      if (!inCrawl && target.isVisuallyCrawling()) {
         return;
      }

      Set<BlockPos> occupiedPositions = PlayerStateUtils.getOccupiedBlockPositions(target);
      AABB targetBounds = target.getBoundingBox();
      boolean extraBreak = this.autoMine.isExtraBreakEnabled();
      AABB floorBounds = targetBounds.setMaxY(targetBounds.minY);
      boolean intersectsBedrock = BlockPos.betweenClosedStream(floorBounds).anyMatch(position -> {
         return BlockInteractionUtils.getBlock(position) == Blocks.BEDROCK;
      });
      AABB insetBounds = targetBounds.deflate(0.05D, 0.05D, 0.05D);
      if (!BlockPos.betweenClosedStream(insetBounds).filter(position -> {
         return BlockInteractionUtils.getBlock(position).getExplosionResistance() >= 6e+02F
            && !this.isAlternateHeadModeEnabled() && BlockInteractionUtils.getBlock(position) != Blocks.AIR;
      }).toList().isEmpty()) {
         return;
      }

      AABB upperBounds = targetBounds.setMaxY(targetBounds.maxY + 0.5D);
      Optional<BlockPos> upperResult = BlockPos.betweenClosedStream(upperBounds)
         .map(BlockPos::immutable).filter(this::isHeadCandidateValid)
         .max(Comparator.comparing(position -> {
            return this.isAlreadyMining(position);
         }));
      BlockPos primaryPosition = upperResult.orElse(null);
      Optional<BlockPos> headResult = PlayerStateUtils.getSurroundPositions(target).stream()
         .map(BlockPos::immutable).map(BlockPos::above).filter(this::isHeadCandidateValid)
         .max(Comparator.comparing(position -> {
            return this.getCandidatePriority(position, candidates);
         }));
      BlockPos secondaryPosition = headResult.orElse(null);
      if (this.isAlternateHeadModeEnabled() && intersectsBedrock) {
         secondaryPosition = null;
      }
      if (primaryPosition == null && secondaryPosition != null && !this.isAlternateHeadModeEnabled()) {
         primaryPosition = secondaryPosition;
         secondaryPosition = null;
      }
      if (primaryPosition == null) {
         return;
      }

      BlockPos selectedPrimary = primaryPosition;
      BlockPos selectedSecondary = secondaryPosition;
      if (this.isAlternateHeadModeEnabled()) {
         int alternatePriority;
         if (intersectsBedrock) {
            if (extraBreak) {
               alternatePriority = 900;
            } else {
               alternatePriority = 1000;
            }
         } else {
            alternatePriority = extraBreak ? 850 : 950;
         }
         Consumer<AutoMineCandidates> alternateUpdate = output -> {
            if (!extraBreak) {
               output.setPrimaryPosition(selectedPrimary);
            } else if (selectedSecondary != null) {
               output.setSecondaryPosition(selectedPrimary);
               output.setPrimaryPosition(selectedSecondary);
            } else {
               output.setPrimaryPosition(selectedPrimary);
            }
         };
         candidates.offer(alternatePriority, alternateUpdate);
         return;
      }

      int normalPriority = 599;
      BlockPos priorityPosition = primaryPosition;
      if (this.isAlreadyMining(priorityPosition)) {
         normalPriority = 650;
      }
      Setting<Boolean> trapOverrideSetting = this.autoMine.trapOverrideSetting;
      if (trapOverrideSetting.getValue()) {
         if (autoTrap.isToggled()) {
            normalPriority = 650;
         }
      }
      if (target.isVisuallyCrawling() && occupiedPositions.size() == 1) {
         ClientLevel level = ClientAccess.zioMinecraft.level;
         BlockPos selfPosition = ClientAccess.zioMinecraft.player.blockPosition();
         if (level.getBlockState(selfPosition).isAir()) {
            Consumer<AutoMineCandidates> crawlUpdate = output -> output.setSecondaryPosition(selectedPrimary);
            candidates.offer(650, crawlUpdate);
            return;
         }
      }
      Consumer<AutoMineCandidates> normalUpdate = output -> output.setPrimaryPosition(selectedPrimary);
      candidates.offer(normalPriority, normalUpdate);
   }

   private boolean isHeadCandidateValid(BlockPos position) {
      if (this.intersectsPlayer(position)) {
         return false;
      }
      boolean baseAllowed = BlockInteractionUtils.getBlock(position) == Blocks.OBSIDIAN || this.isAlternateHeadModeEnabled();
      ClientLevel level = ClientAccess.zioMinecraft.level;
      BlockPos abovePosition = position.above();
      boolean aboveAllowed = level.isEmptyBlock(abovePosition) || this.isAlternateHeadModeEnabled();
      if ((!this.canStartMiningPosition(position) || !baseAllowed) && !this.isAlreadyMining(position)) {
         return false;
      }
      return aboveAllowed;
   }

   private boolean intersectsPlayer(BlockPos position) {
      AABB blockBounds = new AABB(position);
      ClientLevel level = ClientAccess.zioMinecraft.level;
      Iterator<AbstractClientPlayer> players = level.players().iterator();
      while (players.hasNext()) {
         AABB playerBounds = players.next().getBoundingBox();
         if (BoundingBoxUtils.hasVolumeOverlap(playerBounds, blockBounds)) {
            return true;
         }
      }
      return false;
   }

   private int getCandidatePriority(BlockPos position, AutoMineCandidates candidates) {
      if (this.isAlreadyMining(position)) {
         return 2;
      }
      if (!position.equals(candidates.getSecondaryPosition()) && !position.equals(candidates.getPrimaryPosition())) {
         return 0;
      }
      return 1;
   }

   @Override
   public boolean isEnabled() {
      return this.autoMine.headSetting.getValue();
   }

   public static void bindModules(ModuleRegistry registry) {
      autoTrap = (AutoTrapModule)registry.getModule(AutoTrapModule.class);
      antiCheat = (AntiCheatModule)registry.getModule(AntiCheatModule.class);
   }
}
