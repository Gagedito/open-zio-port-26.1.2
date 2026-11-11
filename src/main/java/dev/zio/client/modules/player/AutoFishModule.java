/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.utils.combat.BlockRaycastClassifier;
import dev.zio.client.utils.combat.BlockRaycastOverride;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.combat.BlockRaycastContext;
import dev.zio.client.utils.combat.RaycastUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class AutoFishModule extends Module {
   private final Setting<Boolean> waterSetting;
   private final Setting<Float> recastDelaySetting;
   private final ElapsedTimer castTimer;
   private boolean nearbySplashDetected;

   @Override
   public void onEnable() {
      this.castTimer.reset();
   }

   private void castIfNoHook(InteractionHand hand) {
      if (ClientAccess.zioMinecraft.player.fishing == null) {
         this.useRod(hand);
      }

      this.nearbySplashDetected = false;
   }

   private void useRod(InteractionHand hand) {
      MultiPlayerGameMode gameMode = ClientAccess.zioMinecraft.gameMode;
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      gameMode.useItem(player, hand);
      ClientAccess.zioMinecraft.player.swing(hand);
      this.castTimer.reset();
   }

   @EventHandler
   private void updateFishing(LocalPlayerPreTickEvent event) {
      ItemStack capturedOffhand = ClientAccess.zioMinecraft.player.getOffhandItem();
      Item offhandRod = Items.FISHING_ROD;

      InteractionHand rodHand = capturedOffhand.is(offhandRod) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      Item rod = Items.FISHING_ROD;
      LocalPlayer holdingPlayer = ClientAccess.zioMinecraft.player;
      if (!holdingPlayer.isHolding(rod)) {
         return;
      }
      if (this.nearbySplashDetected) {
         this.castIfNoHook(rodHand);
      }

      if ((this.waterSetting.getValue()) && this.isWaterTargetMissing()) {
         return;
      }

      ElapsedTimer capturedFallbackTimer = this.castTimer;

      TimeUnit fallbackUnit = TimeUnit.MINUTES;

      if (!capturedFallbackTimer.hasElapsed(1.0D, fallbackUnit)) {
         if (ClientAccess.zioMinecraft.player.fishing != null) {
            return;
         }

         ElapsedTimer capturedRecastTimer = this.castTimer;
         double recastSeconds = (this.recastDelaySetting.getValue()).floatValue();
         TimeUnit recastUnit = TimeUnit.SECONDS;

         if (!capturedRecastTimer.hasElapsed(recastSeconds, recastUnit)) {
            return;
         }
      }

      this.useRod(rodHand);
   }

   @EventHandler
   private void detectNearbySplash(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundSoundPacket soundPacket) {
         var sound = soundPacket.getSound();
         Predicate<ResourceKey<SoundEvent>> splashSound = key -> key.identifier().toString()
            .equalsIgnoreCase("minecraft:entity.fishing_bobber.splash");
         if (sound.is(splashSound) && ClientAccess.zioMinecraft.player.fishing != null) {
            Vec3 capturedSoundPosition = new Vec3(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ());
            Vec3 hookPosition = ClientAccess.zioMinecraft.player.fishing.position();

            if (capturedSoundPosition.distanceTo(hookPosition) <= 1.0D) {
               this.nearbySplashDetected = true;
            }
         }
      }
   }

   private boolean isWaterTargetMissing() {
      Vec3 lookDirection = ClientAccess.zioMinecraft.player.getLookAngle();
      Vec3 eyePosition = ClientAccess.zioMinecraft.player.getEyePosition();

      BlockRaycastContext.Builder builder = new BlockRaycastContext.Builder(
         eyePosition, eyePosition.add(lookDirection.scale(8.0D))
      );
      BlockRaycastClassifier waterClassifier = (position, blockState) -> {
         Block waterBlock = Blocks.WATER;
         return blockState.is(waterBlock) ? BlockRaycastOverride.SOLID : BlockRaycastOverride.NONE;
      };
      builder.withClassifier(waterClassifier);
      BlockRaycastContext context = builder.build();

      if (!(RaycastUtils.raycast(context) instanceof BlockHitResult hit) || hit.getType() == Type.MISS) {
         return true;
      }

      ClientLevel capturedLevel = ClientAccess.zioMinecraft.level;
      BlockPos hitPosition = hit.getBlockPos();

      BlockState state = capturedLevel.getBlockState(hitPosition);
      Block water = Blocks.WATER;

      return !state.is(water);
   }

   public AutoFishModule() {
      super("AutoFish", "Fishes.", ModuleCategory.PLAYER, new String[0]);
      Boolean defaultWater = false;
      this.waterSetting = this.add(new BooleanSetting("Water", defaultWater));
      this.recastDelaySetting = this.add(new NumberSetting<>("RecastDelay", 2.0F, 1.0F, 5.0F).withUnit("s"));
      this.castTimer = new ElapsedTimer();
   }

}
