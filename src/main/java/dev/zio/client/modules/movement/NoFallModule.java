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
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.combat.BlockRaycastContext;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.utils.combat.RaycastUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.mixin.ServerboundMovePlayerPacketAccessor;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class NoFallModule extends Module {
   final Setting<ModeChoice> modeSetting;
   private final Setting<Float> groundDistanceSetting;
   private double previousFallDistance;
   private boolean predictedTopFaceCollision;

   @Override
   public String getInfo() {
      return (this.modeSetting.getValue()).getName();
   }

   @EventHandler
   private void onConnectionSendPacketEvent(
      PacketEvent.Send event
   ) {
      NoFallModule.ModeChoice mode = this.modeSetting.getValue();
      mode.handleOutgoingPacket(event);
   }

   @EventHandler
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
      NoFallModule.ModeChoice mode = this.modeSetting.getValue();
      mode.beforePlayerTick();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      NoFallModule.ModeChoice mode = this.modeSetting.getValue();
      mode.afterPlayerTick();
   }

   private HashSet<BlockPos> collectBottomCorners(AABB box) {
      return new HashSet<>(List.of(
         BlockPos.containing(box.maxX, box.minY, box.maxZ),
         BlockPos.containing(box.maxX, box.minY, box.minZ),
         BlockPos.containing(box.minX, box.minY, box.maxZ),
         BlockPos.containing(box.minX, box.minY, box.minZ)
      ));
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (event.getStage() == EventStage.Pre) {
         this.updatePredictedTopFaceCollision();
      }

      NoFallModule.ModeChoice mode = this.modeSetting.getValue();
      mode.handleSendPosition(event);
      this.previousFallDistance = ClientContext.playerStateTracker.getFallDistance();
   }

   private void updatePredictedTopFaceCollision() {
      Vec3 currentPosition = ClientAccess.zioMinecraft.player.position();
      double previousX = ClientAccess.zioMinecraft.player.xo;
      double previousY = ClientAccess.zioMinecraft.player.yo;
      double previousZ = ClientAccess.zioMinecraft.player.zo;
      Vec3 prediction = currentPosition.subtract(previousX, previousY, previousZ).scale(1.100000023841858D);
      BlockRaycastContext.Builder builder = new BlockRaycastContext.Builder(
         ClientAccess.zioMinecraft.player.position(), ClientAccess.zioMinecraft.player.position().add(prediction)
      );
      BlockRaycastContext context = builder.build();
      BlockHitResult hit = RaycastUtils.raycast(context);
      if (hit.getType() == Type.MISS) {
         this.predictedTopFaceCollision = false;
      } else {
         this.predictedTopFaceCollision = hit.getDirection() == Direction.UP;
      }
   }

   public NoFallModule() {
      super("NoFall", "Prevents falling damage.", ModuleCategory.MOVEMENT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.PACKET));
      Predicate<Object> groundDistanceVisible = ignored -> this.modeSetting.getValue() == ModeChoice.MLG;
      this.groundDistanceSetting = this.add(new NumberSetting<>("GroundDistance", 2.0F, 1.0F, 4.0F, groundDistanceVisible).withUnit("m"));

      for (NoFallModule.ModeChoice choice : NoFallModule.ModeChoice.values()) {
         choice.module = this;
      }
   }

   public enum ModeChoice implements NamedEntry {
      PACKET("Packet") {
         @Override
         public void handleOutgoingPacket(
            PacketEvent.Send event
         ) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket
               && PlayerStateUtils.getTrackedFallDistance() > 2.1
               && !PlayerStateUtils.isFallFlying()) {
               ((ServerboundMovePlayerPacketAccessor)movementPacket).setOnGround(true);
            }
         }

         @Override
         public void handleSendPosition(LocalPlayerSendPositionEvent event) {
            if (ClientAccess.zioMinecraft.player.isFallFlying()) {
               NoFallModule.ModeChoice.GRIM.handleSendPosition(event);
            }
         }
      },
      ANTI("Anti") {
         @Override
         public void handleOutgoingPacket(
            PacketEvent.Send event
         ) {
            if (!(event.getPacket() instanceof ServerboundMovePlayerPacket)
               || !(PlayerStateUtils.getTrackedFallDistance() > 3.0F)
               || !this.canApplyFallProtection()) {
               return;
            }
            ClientLevel world = ClientAccess.zioMinecraft.level;
            AABB bounds = ClientAccess.zioMinecraft.player.getBoundingBox();
            double verticalExpansion = ClientAccess.zioMinecraft.player.getDeltaMovement().y * 1.1;
            AABB expandedBounds = bounds.expandTowards(0.0, verticalExpansion, 0.0);
            if (world.noCollision(expandedBounds)) {
               return;
            }
            LocalPlayer target = ClientAccess.zioMinecraft.player;
            double x = ClientAccess.zioMinecraft.player.getX();
            double y = ClientAccess.zioMinecraft.player.getY() + 2.0;
            double z = ClientAccess.zioMinecraft.player.getZ();
            target.setPosRaw(x, y, z);
         }
      },
      TELEPORT("Teleport") {
         private boolean teleportPending;

         @Override
         public void afterPlayerTick() {
            if (PlayerStateUtils.getTrackedFallDistance() > 3.0F) {
               this.teleportPending = true;
            }
         }

         @Override
         public void handleSendPosition(LocalPlayerSendPositionEvent event) {
            if (!this.teleportPending) {
               return;
            }
            if (!event.isOnGround()) {
               return;
            }
            if (event.getStage() != EventStage.Pre) {
               return;
            }
            if (!this.canApplyFallProtection()) {
               return;
            }
            event.setX(0.0);
            event.setY(0.0);
            event.setZ(0.0);
            event.setOnGround(false);
            if (WorldUtils.getDimension().isEnd()) {
               event.setY(64.0);
            }
            this.teleportPending = false;
         }
      },
      MLG("MLG") {
         private boolean skipNextMlgTick;

         @Override
         public void afterPlayerTick() {
            if (this.skipNextMlgTick) {
               this.skipNextMlgTick = false;
               return;
            }
            InventoryUtils.SwapSlot bucket = InventoryUtils.findSwapSlot(Items.WATER_BUCKET, true);
            int selectedSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
            if (PlayerStateUtils.getTrackedFallDistance() <= 3.0F) {
               return;
            }
            if (!bucket.isFound()) {
               return;
            }
            if (ClientAccess.zioMinecraft.player.isFallFlying()) {
               return;
            }
            NoFallModule owner = this.module;
            AABB bounds = ClientAccess.zioMinecraft.player.getBoundingBox();
            HashSet<BlockPos> positions = owner.collectBottomCorners(bounds);
            Vec3 playerPosition = ClientAccess.zioMinecraft.player.position();
            positions.add(BlockPos.containing(playerPosition));
            for (int depth = 0; (float)depth <= this.module.groundDistanceSetting.getValue(); depth++) {
               for (BlockPos position : positions) {
                  ClientLevel blockWorld = ClientAccess.zioMinecraft.level;
                  BlockPos below = position.relative(Direction.DOWN, depth);
                  BlockState state = blockWorld.getBlockState(below);
                  if (!(state.getBlock() instanceof AirBlock)) {
                     ClientLevel fluidWorld = ClientAccess.zioMinecraft.level;

                     if (fluidWorld.getFluidState(position).is(FluidTags.WATER)) {
                        return;
                     }
                     LocalPlayer pitchPlayer = ClientAccess.zioMinecraft.player;
                     float pitch = MathConstants.NINETY_DEGREES;
                     pitchPlayer.setXRot(pitch);
                     InventoryUtils.swapTo(bucket);
                     MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
                     LocalPlayer usePlayer = ClientAccess.zioMinecraft.player;
                     InteractionHand hand = bucket.hand();
                     gameMode.useItem(usePlayer, hand);
                     LocalPlayer swingPlayer = ClientAccess.zioMinecraft.player;
                     InteractionHand swingHand = bucket.hand();
                     swingPlayer.swing(swingHand);
                     InventoryUtils.swapBack(selectedSlot, bucket);
                     this.skipNextMlgTick = true;
                     return;
                  }
               }
            }
         }
      },
      GRIM("Grim") {
         @Override
         public void handleSendPosition(LocalPlayerSendPositionEvent event) {
            if (event.getStage() == EventStage.Post) {
               return;
            }
            if (!ClientAccess.zioMinecraft.player.onGround() || this.module.previousFallDistance < 3.0) {
               return;
            }
            Vec3 position = ClientContext.playerStateTracker.getPacketPosition();
            double x = position.x();
            double y = position.y() + 1.0E-9;
            double z = position.z();
            float yaw = ClientAccess.zioMinecraft.player.getYRot();
            float pitch = ClientAccess.zioMinecraft.player.getXRot();
            PacketSender.sendPositionRotation(
               x, y, z, yaw, pitch, false
            );
         }
      },
      GRIM_JUMP("GrimJump") {
         private boolean jumpPending;

         @Override
         public void handleOutgoingPacket(
            PacketEvent.Send event
         ) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacketAccessor movementPacket
               && (PlayerStateUtils.getTrackedFallDistance() > 3.0F || this.jumpPending)) {
               if (!ClientAccess.zioMinecraft.player.isFallFlying()) {
                  movementPacket.setOnGround(false);
                  this.jumpPending = true;
               }
            }
         }

         @Override
         public void beforePlayerTick() {
            if (!this.jumpPending) {
               return;
            }
            if (!ClientAccess.zioMinecraft.player.onGround()) {
               return;
            }
            ClientContext.inputOverrides.requestJump(true);
            this.jumpPending = false;
         }
      };

      private final String displayName;
      protected NoFallModule module;

      public void afterPlayerTick() {
      }

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }

      public void handleOutgoingPacket(
         PacketEvent.Send event
      ) {
      }

      public void handleSendPosition(LocalPlayerSendPositionEvent event) {
      }

      public void beforePlayerTick() {
      }

      protected boolean canApplyFallProtection() {
         if (!ClientAccess.zioMinecraft.player.isFallFlying()) {
            return true;
         }

         float pitch = ClientContext.playerStateTracker.getPacketRotations()[1];
         return Math.abs(pitch) <= 45.0F || this.module.predictedTopFaceCollision;
      }
   }
}
