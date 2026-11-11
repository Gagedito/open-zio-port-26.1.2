/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockCollisionShapeEvent extends CancellableEvent {
   public static final BlockCollisionShapeEvent INSTANCE = new BlockCollisionShapeEvent();
   private VoxelShape shape;
   private BlockPos pos;
   private BlockState state;

   public BlockPos getPos() {
      return this.pos;
   }

   public VoxelShape getShape() {
      return this.shape;
   }

   public BlockState getState() {
      return this.state;
   }

   private BlockCollisionShapeEvent() {
   }

   public static BlockCollisionShapeEvent prepare(
      VoxelShape shape, BlockPos pos, BlockState state
   ) {
      INSTANCE.shape = shape;
      INSTANCE.pos = pos;
      INSTANCE.state = state;
      return INSTANCE;
   }

   public void setShape(VoxelShape shape) {
      this.shape = shape;
   }

}
