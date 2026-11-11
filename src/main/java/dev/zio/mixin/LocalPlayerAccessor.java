/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
   @Accessor("lastOnGround")
   boolean lastOnGround();

   @Accessor("yRotLast")
   float lastYaw();

   @Invoker("modifyInput")
   Vec2 zio$applyMovementSpeedFactors(Vec2 input);

   @Accessor("positionReminder")
   void zio$setTicksSinceLastPositionPacketSent(int positionReminder);

   @Accessor("positionReminder")
   int zio$getTicksSinceLastPositionPacketSent();

   @Accessor("lastSentInput")
   void zio$setLastPlayerInput(Input lastSentInput);

   @Accessor("wasSprinting")
   void zio$setLastSprinting(boolean wasSprinting);
}
