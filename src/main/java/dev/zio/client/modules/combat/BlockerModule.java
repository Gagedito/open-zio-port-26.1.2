/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.utils.world.BlockBreakTracker;
import dev.zio.client.utils.world.BlockStateUtils;
import dev.zio.client.modules.PlacementModule;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.BlockerModeChoice;
import dev.zio.client.settings.options.BlockerTargetOffsets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.Packet;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BlockerModule extends PlacementModule {
   private static SpeedMineModule speedMineModule;
   private final Setting<BlockerModeChoice> modeSetting;
   private final Setting<Float> blockDamageSetting;
   private final Setting<Boolean> targetsSetting;
   private final Setting<Boolean> fishSetting;
   private final Setting<Boolean> surroundSetting;
   private final Setting<Boolean> cornersSetting;
   private final Setting<Boolean> faceSetting;
   private final Setting<Boolean> cevSetting;
   private final Setting<Boolean> extendedSetting;
   private final Map<BlockPos, Long> destroyedPositionExpiries;

   static {
      speedMineModule = (SpeedMineModule)ClientContext.getModule(SpeedMineModule.class);
   }

   @Override
   public String getInfo() {
      if (this.modeSetting.getValue() == BlockerModeChoice.DAMAGE) {
         return "%.1f".formatted(this.blockDamageSetting.getValue());
      } else {
         return (this.modeSetting.getValue()).getName();
      }
   }

   private boolean shouldProtectBelow() {
      if (!this.fishSetting.getValue()) {
         return false;
      }

      AABB playerBounds = ClientAccess.zioMinecraft.player.getBoundingBox();

      double playerY = ClientAccess.zioMinecraft.player.getY();
      AABB feetBounds = playerBounds.setMaxY(playerY);
      double minX = feetBounds.minX;
      int minBlockX = Mth.floor(minX);
      double maxX = feetBounds.maxX;
      int maxBlockX = Mth.floor(maxX);
      double minZ = feetBounds.minZ;
      int minBlockZ = Mth.floor(minZ);
      double maxZ = feetBounds.maxZ;
      int maxBlockZ = Mth.floor(maxZ);
      int occupiedColumns = (maxBlockX - minBlockX + 1) * (maxBlockZ - minBlockZ + 1);
      return occupiedColumns == 1;
   }

   private List<BlockPos> getTargetOffsets() {
      ArrayList<BlockPos> offsets = new ArrayList<>();
      Collection<BlockPos> surroundPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.zioMinecraft.player);

      if (this.surroundSetting.getValue()) {
         offsets.addAll(surroundPositions);
      }

      if (this.cevSetting.getValue()) {
         for (BlockPos position : PlayerStateUtils.getOccupiedBlockPositions(ClientAccess.zioMinecraft.player)) {
            offsets.add(position.above(2));
            offsets.add(position.above(3));
         }

         if (this.extendedSetting.getValue()) {
            for (BlockPos position : surroundPositions) {
               offsets.add(position.above());
            }
         }
      }

      return offsets;
   }

   @EventHandler
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      synchronized (this.destroyedPositionExpiries) {
         Collection<Long> expiries = this.destroyedPositionExpiries.values();
         Predicate<Long> hasExpired = expiry -> System.currentTimeMillis() > expiry;
         expiries.removeIf(hasExpired);
      }
   }

   @Override
   public List<BlockPos> getPlacementPositions() {
      BlockPos playerBlockPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      ArrayList<BlockPos> triggeredTargets = new ArrayList<>();

      Iterator<BlockPos> offsetIterator = this.getTargetOffsets().iterator();

      while (offsetIterator.hasNext()) {
         BlockPos targetPosition = offsetIterator.next();

         if (this.isTargetTriggered(targetPosition)) {
            triggeredTargets.add(targetPosition);
         }
      }

      ArrayList<BlockPos> placements = new ArrayList<>();

      if (this.shouldProtectBelow()) {
         BlockPos belowPlayerPosition = playerBlockPosition.below();

         if (this.isTargetTriggered(belowPlayerPosition)) {
            Iterator<BlockPos> belowSurroundIterator = PlayerStateUtils.getSurroundPositions(ClientAccess.zioMinecraft.player).iterator();

            while (belowSurroundIterator.hasNext()) {
               BlockPos belowSurroundPosition = belowSurroundIterator.next();
               BlockPos belowSidePosition = belowSurroundPosition.below();
               placements.add(belowSidePosition);
            }
         }
      }

      Iterator<BlockPos> targetIterator = triggeredTargets.iterator();

      while (targetIterator.hasNext()) {
         BlockPos triggeredTarget = targetIterator.next();
         BlockerTargetOffsets surroundPattern = BlockerTargetOffsets.SURROUND;
         Iterator<Vec3i> surroundIterator = surroundPattern.getOffsets().iterator();

         while (surroundIterator.hasNext()) {
            Vec3i surroundOffset = surroundIterator.next();
            int positionWord = triggeredTarget.getY();

            Vec3 playerPositionForY = ClientAccess.zioMinecraft.player.position();
            if (positionWord != (int)playerPositionForY.y()) {
               break;
            }

            BlockPos localBlockPosition = BlockPos.containing((Position)ClientAccess.zioMinecraft.player.position());

            if (this.cornersSetting.getValue()) {
               if (!triggeredTarget.offset(surroundOffset).equals(localBlockPosition)) {
                  placements.add(triggeredTarget.offset(surroundOffset));
               }
            } else {
               if (triggeredTarget.subtract(localBlockPosition).equals(surroundOffset)) {
                  placements.add(triggeredTarget.offset(surroundOffset));
                  break;
               }
            }
         }

         double targetY = triggeredTarget.getY();

         double playerY = ClientAccess.zioMinecraft.player.getY();

         if (targetY == playerY + 3.0D) {
            placements.add(triggeredTarget.below());
         } else {
            if (this.faceSetting.getValue()) {
               placements.add(triggeredTarget.above());
            }
         }
      }

      return placements;
   }

   private boolean isTargetTriggered(BlockPos position) {
      switch (this.modeSetting.getValue()) {
         case DAMAGE:
            BlockBreakTracker breakManagerSnapshot = ClientContext.blockBreakTracker;

            float blockDamage = breakManagerSnapshot.getBreakProgress(position);
            Float minimumDamage = this.blockDamageSetting.getValue();
            return blockDamage >= minimumDamage;
         case INSTANT:
            return !BlockStateUtils.isBlastResistantUnbreakable(ClientAccess.zioMinecraft.level.getBlockState(position));
         case DESTROY:
            synchronized (this.destroyedPositionExpiries) {
               return this.destroyedPositionExpiries.containsKey(position);
            }
         default:
            throw new MatchException(null, null);
      }
   }

   @EventHandler
   private void onInboundBlockUpdates(
      PacketEvent.Receive event
   ) {
      Packet packet = event.getPacket();

      if (BlockUpdateBatch.isSupportedPacket(packet)) {
         BlockUpdateBatch blockUpdates = new BlockUpdateBatch(event.getPacket());
         Iterator<BlockUpdateBatch.Update> updatesIterator = blockUpdates.iterator();

         while (updatesIterator.hasNext()) {
            BlockUpdateBatch.Update update = updatesIterator.next();

            BlockPos miningPosition = speedMineModule.getMiningPos();

            BlockState updatedStateSnapshot = update.state();
            TagKey airTag = BlockTags.AIR;
            if (!updatedStateSnapshot.is(airTag)) {
               return;
            }

            if (miningPosition != null) {
               if (miningPosition.equals(update.position())) {
                  return;
               }
            }

            synchronized (this.destroyedPositionExpiries) {
               Map<BlockPos, Long> expiryMap = this.destroyedPositionExpiries;
               BlockPos updatedPosition = update.position();
               long now = System.currentTimeMillis();

               long expiry = now + 150L;
               expiryMap.put(updatedPosition, expiry);
            }
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      speedMineModule = (SpeedMineModule)registry.getModule(
         SpeedMineModule.class
      );
   }

   public BlockerModule() {
      super("Blocker", "Blocks certain offsets to prevent crystal damage.", ModuleCategory.COMBAT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", BlockerModeChoice.DAMAGE));
      this.blockDamageSetting = this.add(new NumberSetting<>("BlockDamage", 0.8F, 0.1F, 1.0F));
      this.targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup());
      this.fishSetting = this.add(new BooleanSetting("Fish", false).dependsOn(this.targetsSetting));
      this.surroundSetting = this.add(new BooleanSetting("Surround", true)
         .markVisibilityDependency().dependsOn(this.targetsSetting));
      this.cornersSetting = this.add(new BooleanSetting("Corners", false).dependsOn(this.targetsSetting, this.surroundSetting));
      this.faceSetting = this.add(new BooleanSetting("Face", true).dependsOn(this.targetsSetting, this.surroundSetting));
      this.cevSetting = this.add(new BooleanSetting("Cev", true)
         .markVisibilityDependency().dependsOn(this.targetsSetting));
      this.extendedSetting = this.add(new BooleanSetting("Extended", false).dependsOn(this.targetsSetting, this.cevSetting));
      this.destroyedPositionExpiries = new HashMap<>();
      this.placementJumpSetting.setDefaultValue(false);
      this.blockDamageSetting.addVisibilityPredicate( unused -> {
         return this.modeSetting.getValue() == BlockerModeChoice.DAMAGE;
      });
      this.ignoreMissingPlacementBlocks();
   }
}
