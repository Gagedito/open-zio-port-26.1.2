/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.misc.SocialEntry;
import dev.zio.client.managers.PlayerTickExecutor;
import dev.zio.client.utils.FloatAnimation;
import dev.zio.client.utils.entity.PlayerState;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.settings.options.SocialRole;
import dev.zio.client.events.EventStage;
import dev.zio.client.commands.FakeplayerCommand;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.movement.NoSlowModule;
import dev.zio.client.events.PlayerAttackEvent;
import dev.zio.client.events.PlayerIsStayingOnGroundSurfaceEvent;
import dev.zio.client.modules.exploit.ReachModule;
import dev.zio.client.modules.player.ScaffoldModule;
import dev.zio.client.modules.movement.SprintModule;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements PlayerState {
   private static ScaffoldModule scaffoldModule = ClientContext.getModule(ScaffoldModule.class);
   private static SprintModule sprintModule = ClientContext.getModule(SprintModule.class);
   private static ReachModule reachModule = ClientContext.getModule(ReachModule.class);
   private static NoSlowModule noSlowModule = ClientContext.getModule(NoSlowModule.class);
   @Unique
   private long lastCrawlingTime;
   @Unique
   private long lastEatingTime;
   @Unique
   private SocialRole socialRole;
   @Unique
   private boolean nextToWall;
   @Unique
   private int lastAttackedTicksServer;
   @Unique
   private FloatAnimation healthAnimation;

   @Shadow
   public abstract Abilities getAbilities();

   protected PlayerMixin(EntityType entityType, Level level) {
      super(entityType, level);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void initializePlayerState(
      Level level, GameProfile profile, CallbackInfo callback
   ) {
      this.healthAnimation = new FloatAnimation();
      this.healthAnimation
         .setValue(
            EntityUtils.getHealthFraction(this)
         );

      for (Object entry : (List)ClientContext.socialRegistry.getRegistry()) {
         SocialEntry socialEntry = (SocialEntry)entry;
         if (profile.name().equalsIgnoreCase(socialEntry.getName())) {
            this.zio$setRole(socialEntry.role());
            break;
         }
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void updatePlayerState(CallbackInfo callback) {
      this.healthAnimation
         .setTarget(
            EntityUtils.getHealthFraction(this), 100L
         );
      if (this.getBoundingBox().getYsize() < 1.0) {
         this.lastCrawlingTime = System.currentTimeMillis();
      }

      this.nextToWall = !this.level()
         .noCollision(this.getBoundingBox().inflate(1.0E-4, 0.0, 1.0E-4));
      if (!this.isUsingItem()
         || this.getItemUseProgress(this.getUseItem(), this) > 1.0F) {
         this.lastEatingTime = System.currentTimeMillis();
      }
   }

   @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
   private void onIsStayingOnGroundSurface(CallbackInfoReturnable<Boolean> callback) {
      if (ClientAccess.zioMinecraft.player != null) {
         PlayerIsStayingOnGroundSurfaceEvent event = new PlayerIsStayingOnGroundSurfaceEvent();
         ClientAccess.eventBus
            .post(event);
         if (event.isCancelled()) {
            callback.setReturnValue(true);
         }
      }
   }

   @ModifyExpressionValue(method = "maybeBackOffFromEdge", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;maxUpStep()F"))
   private float modifySafeWalkStepHeight(float originalHeight) {
      if (ClientAccess.zioMinecraft.player == null) {
         return originalHeight;
      }

      if (!scaffoldModule.isToggled() || !scaffoldModule.safeWalkSetting.getValue()) {
         return originalHeight;
      }
      Item heldItem = ClientAccess.zioMinecraft.player.getItemInHand(InteractionHand.MAIN_HAND).getItem();
      if (!(heldItem instanceof BlockItem blockItem)) {
         return originalHeight;
      }
      VoxelShape collisionShape = blockItem.getBlock().defaultBlockState().getCollisionShape(
         ClientAccess.zioMinecraft.level, BlockPos.ZERO
      );
      return collisionShape != null && !collisionShape.isEmpty() ? (float)collisionShape.bounds().getYsize() : originalHeight;
   }

   @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
   public void onAttackPre(Entity target, CallbackInfo callback) {
      PlayerAttackEvent event = new PlayerAttackEvent(EventStage.Pre, target);
      ClientAccess.eventBus
         .post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "attack", at = @At("TAIL"))
   private void onAttackPost(Entity attackedEntity, CallbackInfo callback) {
      PlayerAttackEvent event = new PlayerAttackEvent(EventStage.Post, attackedEntity);
      ClientAccess.eventBus
         .post(event);
   }

   @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
   private boolean allowAttackMovementSlowdown(Player player, Vec3 movement) {
      return !sprintModule.isToggled()
         || !sprintModule.keepSetting.getValue();
   }

   @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
   private boolean allowAttackSprintReset(Player player, boolean sprinting) {
      return !sprintModule.isToggled()
         || !sprintModule.keepSetting.getValue();
   }

   @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
   private boolean allowSimulatedPlayerKnockback(
      LivingEntity entity, double strength, double directionX, double directionZ
   ) {
      return !((Object)this instanceof FakeplayerCommand.SimulatedPlayer)
         || entity != ClientAccess.zioMinecraft.player;
   }

   @Inject(method = "entityInteractionRange", at = @At("HEAD"), cancellable = true)
   private void overrideEntityInteractionRange(CallbackInfoReturnable<Double> callback) {
      if (reachModule.isToggled()) {
         callback.setReturnValue(
            (reachModule.modifierSetting.getValue())
               .doubleValue()
         );
      }
   }

   @Inject(method = "blockInteractionRange", at = @At("HEAD"), cancellable = true)
   private void overrideBlockInteractionRange(CallbackInfoReturnable<Double> callback) {
      if (reachModule.isToggled()) {
         callback.setReturnValue(
            (reachModule.modifierSetting.getValue())
               .doubleValue()
         );
      }
   }

   @Inject(method = "isSecondaryUseActive", at = @At("HEAD"), cancellable = true)
   private void forceSecondaryUseWhilePlacing(CallbackInfoReturnable<Boolean> callback) {
      if (BlockInteractionUtils.isPlacingBlock) {
         callback.setReturnValue(true);
      }
   }

   @Inject(method = "resetOnlyAttackStrengthTicker", at = @At("HEAD"))
   private void resetServerAttackTicks(CallbackInfo callback) {
      this.zio$resetLastAttackedServer();
   }

   @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;attackStrengthTicker:I", ordinal = 0, opcode = 180))
   private void incrementServerAttackTicks(CallbackInfo callback) {
      if (!PlayerTickExecutor.isRunningExtraTick()) {
         this.lastAttackedTicksServer++;
      }
   }

   @ModifyReturnValue(method = "getSpeed", at = @At("RETURN"))
   private float applyNoSlowWalkingSpeed(float originalSpeed) {
      if (!this.level().isClientSide()) {
         return originalSpeed;
      }

      if (!noSlowModule.isToggled() || !noSlowModule.slownessSetting.getValue()) {
         return originalSpeed;
      }
      float walkingSpeed = this.getAbilities().getWalkingSpeed();
      if (originalSpeed >= walkingSpeed) {
         return originalSpeed;
      }
      return this.isSprinting() ? walkingSpeed * 1.3000001F : walkingSpeed;
   }

   @Override
   public long zio$getLastEatingTime() {
      return this.lastEatingTime;
   }

   @Override
   public SocialRole zio$getRole() {
      return this.socialRole;
   }

   @Override
   public void zio$setRole(SocialRole role) {
      this.socialRole = role;
   }

   @Override
   public float zio$getHealthFactor() {
      return this.healthAnimation
         .getValue();
   }

   @Override
   public boolean zio$isNextToWall() {
      return this.nextToWall;
   }

   @Override
   public boolean zio$wasCrawling() {
      return System.currentTimeMillis() - this.lastCrawlingTime <= 150L;
   }

   @Override
   public int zio$getLastAttackedTicksServer() {
      return this.lastAttackedTicksServer;
   }

   @Override
   public void zio$resetLastAttackedServer() {
      this.lastAttackedTicksServer = 0;
   }

   @Unique
   private static void zio$bindPlayerModules(ModuleRegistry moduleRegistry) {
      scaffoldModule = (ScaffoldModule)moduleRegistry.getModule(
         ScaffoldModule.class
      );
      sprintModule = (SprintModule)moduleRegistry.getModule(
         SprintModule.class
      );
      reachModule = (ReachModule)moduleRegistry.getModule(
         ReachModule.class
      );
      noSlowModule = (NoSlowModule)moduleRegistry.getModule(
         NoSlowModule.class
      );
   }
}
