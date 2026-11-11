/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface InputAccessor {
   @Mutable
   @Accessor("shift")
   void zio$setSneak(boolean shift);

   @Mutable
   @Accessor("sprint")
   void zio$setSprint(boolean sprint);

   @Mutable
   @Accessor("jump")
   void zio$setJump(boolean jump);

   @Mutable
   @Accessor("forward")
   void zio$setForward(boolean forward);

   @Mutable
   @Accessor("backward")
   void zio$setBack(boolean backward);

   @Mutable
   @Accessor("left")
   void zio$setLeft(boolean left);

   @Mutable
   @Accessor("right")
   void zio$setRight(boolean right);
}
