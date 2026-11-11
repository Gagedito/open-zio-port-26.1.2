/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;


import dev.zio.client.ClientAccess;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.BlockCollisionShapeEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.mixin.ServerboundMovePlayerPacketAccessor;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class JesusModule extends Module {
   final Setting<ModeChoice> modeSetting;
   private final Setting<Boolean> strictSetting;
   private final Setting<Float> ascendingSetting;
   private final double[] strictYOffsetSequence;
   private int strictOffsetIndex;

   @Override
   public String getInfo() {
      return (this.modeSetting.getValue()).getName();
   }

   @Override
   public void onToggle() {
      this.strictOffsetIndex = 0;
   }

   private boolean isStableAboveWater() {
      if (Math.abs(ClientAccess.zioMinecraft.player.yo - ClientAccess.zioMinecraft.player.getY()) > 0.01D) {
         return false;
      }
      for (BlockPos position : PlayerStateUtils.getOccupiedBlockPositions(ClientAccess.zioMinecraft.player)) {
         if (WorldUtils.getBlock(position.below()) != Blocks.WATER) {
            return false;
         }
      }
      return true;
   }

   public boolean canWalkOnWater() {
      if (this.isWorldUnavailable() || ClientAccess.zioMinecraft.player.input == null) {
         return false;
      }
      if (ClientAccess.zioMinecraft.player.isPassenger()) {
         return false;
      }
      return !ClientAccess.zioMinecraft.player.input.keyPresses.shift();
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      if (!this.canWalkOnWater() || !ClientAccess.zioMinecraft.player.isInWater()) {
         return;
      }
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      double upwardImpulse = this.modeSetting.getValue() == ModeChoice.DOLPHIN
         ? 0.0399D : 0.08D * this.ascendingSetting.getValue();
      player.push(0.0, upwardImpulse, 0.0);
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (this.modeSetting.getValue() != JesusModule.ModeChoice.SOLID || !this.canWalkOnWater()) {
         return;
      }
      if (!(event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket)) {
         return;
      }
      if (!this.strictSetting.getValue() || !this.isStableAboveWater()) {
         return;
      }
      ServerboundMovePlayerPacketAccessor packetAccessor = (ServerboundMovePlayerPacketAccessor)movementPacket;
      packetAccessor.setOnGround(false);
      packetAccessor.setY(movementPacket.getY(0.0) + this.strictYOffsetSequence[this.strictOffsetIndex++]);
      this.strictOffsetIndex %= this.strictYOffsetSequence.length;
   }

   @EventHandler
   private void onCollisionShape(BlockCollisionShapeEvent event) {
      if (this.isWorldUnavailable()) {
         return;
      }
      if (ClientAccess.zioMinecraft.player.isInWater()) {
         return;
      }
      if (this.modeSetting.getValue() != JesusModule.ModeChoice.SOLID || !this.canWalkOnWater()) {
         return;
      }
      float blockYAsFloat = event.getPos().getY();
      double liquidSurfaceY = blockYAsFloat + 0.2F;
      if (liquidSurfaceY > ClientAccess.zioMinecraft.player.getY()) {
         return;
      }
      if (event.getState().is(Blocks.WATER)) {
         AABB waterBounds = new AABB(0.0, 0.0, 0.0, 1.0D, 0.98D, 1.0D);
         VoxelShape waterShape = Shapes.create(waterBounds);
         event.setShape(waterShape);
      }
   }

   public JesusModule() {
      super("Jesus", "Allows you to walk on water.", ModuleCategory.MOVEMENT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", JesusModule.ModeChoice.SOLID));
      Predicate<Object> showStrict = ignored -> this.modeSetting.getValue() == ModeChoice.SOLID;
      this.strictSetting = this.add(new BooleanSetting("Strict", false, showStrict));
      Predicate<Object> showAscending = ignored -> this.modeSetting.getValue() == ModeChoice.SOLID;
      this.ascendingSetting = this.add(new NumberSetting<>("Ascending", 1.0F, 0.1F, 1.0F, showAscending));
      this.strictYOffsetSequence = new double[]{
         0.08232659236482401D, 0.13927999979019162D, 0.1654239999427795D,
         0.11427136035293574D, 0.04194693730418576D, 0.01236341326161235D
      };
   }
   public enum ModeChoice implements NamedEntry {
      SOLID("Solid"),
      DOLPHIN("Dolphin");
      private final String displayName;
      @Override
      public String getName() {
         return this.displayName;
      }
      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
