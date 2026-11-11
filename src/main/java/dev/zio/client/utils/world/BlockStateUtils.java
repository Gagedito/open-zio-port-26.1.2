/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.world;

import dev.zio.client.ClientAccess;

import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockStateUtils implements ClientAccess {

   private static final Set<Block> ignoredCollisionBlocks;

   private BlockStateUtils() {
   }

   static {
      Block fire = Blocks.FIRE;
      Block soulFire = Blocks.SOUL_FIRE;
      Block glowLichen = Blocks.GLOW_LICHEN;
      Block light = Blocks.LIGHT;
      Block tripwireHook = Blocks.TRIPWIRE_HOOK;
      Block tripwire = Blocks.TRIPWIRE;
      ignoredCollisionBlocks = Set.of(fire, soulFire, glowLichen, light, tripwireHook, tripwire);
   }

   public static boolean isBlastResistantUnbreakable(BlockState state) {
      Block resistanceBlock = state.getBlock();
      float resistance = resistanceBlock.getExplosionResistance();
      return resistance >= 600.0F && state.getBlock().defaultDestroyTime() < 0.0F;
   }

   public static boolean isBlocking(BlockState state) {
      Block block = state.getBlock();
      if (state.isAir() || ignoredCollisionBlocks.contains(block)) {
         return false;
      }
      Block snowBlock = Blocks.SNOW;
      if (state.is(snowBlock)) {
         if (hasLowSnowLayers(state)) {
            return false;
         }
      }
      BlockGetter collisionLevel = ClientAccess.zioMinecraft.level;
      BlockPos collisionPosition = BlockPos.ZERO;
      VoxelShape shape = state.getCollisionShape(collisionLevel, collisionPosition);
      if (shape.isEmpty()) {
         Block cobwebBlock = Blocks.COBWEB;
         return state.is(cobwebBlock);
      }
      return true;
   }

   static boolean hasLowSnowLayers(BlockState state) {
      return state.getValueOrElse(SnowLayerBlock.LAYERS, 0) < 5;
   }

   public static boolean isBlocking(BlockPos position) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      return isBlocking(level.getBlockState(position));
   }

   public static boolean isBlockingExceptCarpet(BlockPos position) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      BlockState state = level.getBlockState(position);
      return isBlocking(state) && !(state.getBlock() instanceof CarpetBlock);
   }

   public static boolean isBlastResistantBreakable(BlockState state) {
      Block resistanceBlock = state.getBlock();
      float resistance = resistanceBlock.getExplosionResistance();
      return resistance >= 600.0F && state.getBlock().defaultDestroyTime() >= 0.0F;
   }

   public static boolean isWeakOrInstantlyBreakable(BlockState state) {
      Block resistanceBlock = state.getBlock();
      float resistance = resistanceBlock.getExplosionResistance();
      if (resistance < 600.0F && state.getBlock().getExplosionResistance() >= 0.0F) {
         return true;
      }
      Block instantBlock = state.getBlock();
      return instantBlock.defaultDestroyTime() == 0.0F;
   }

}
