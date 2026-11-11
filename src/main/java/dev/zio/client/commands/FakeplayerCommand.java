/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.MinecraftDisconnectEvent;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.utils.ServerAddressUtils;
import dev.zio.client.utils.network.InteractPacketUtils;
import dev.zio.client.utils.combat.DamageUtils;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.modules.misc.KillEffectsModule;
import dev.zio.client.modules.player.FreecamModule;
import dev.zio.client.modules.player.SpeedMineModule;
import dev.zio.client.modules.render.LogoutSpotsModule;
import dev.zio.client.settings.Setting;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FakeplayerCommand extends Command {
   private static final Identifier protectedSkinTexture;
   private static final FreecamModule freecam;
   private static final SpeedMineModule speedmine;
   private static final LogoutSpotsModule logoutSpots;
   private static final KillEffectsModule killEffects;
   private final List recordedFrames = new ArrayList();
   private final ElapsedTimer logoutSpotTimer = new ElapsedTimer();
   private FakeplayerCommand.SimulatedPlayer fakePlayer;
   private boolean recording;
   private boolean playing;
   private String name;
   private int playbackIndex;
   private boolean logoutSpotActive;

   private void toggleLogoutSpot() {
      if (this.logoutSpotActive) {
         this.logoutSpotActive = false;
         logoutSpots.removeSpot("Herobrine");
         ChatUtils.printChat(Component.literal("Herobrine logout spot has been removed."), -1);
      } else {
         SimulatedPlayer player = this.fakePlayer != null ? this.fakePlayer : new SimulatedPlayer(
            ClientAccess.zioMinecraft.player, ClientAccess.zioMinecraft.level, new GameProfile(UUID.randomUUID(), this.name));
         String address = ServerAddressUtils.getCurrentServerAddress();
         this.logoutSpotActive = true;
         logoutSpots.putSpot("Herobrine", LogoutSpotsModule.LogoutSpot.capturePlayer(player, address));
         this.logoutSpotTimer.reset();
         ChatUtils.printChat(Component.literal("Herobrine logout spot has been added. It will automatically disappear in 5 minutes."), -1);
      }
      if (this.fakePlayer != null) {
         ClientAccess.zioMinecraft.level.removeEntity(this.fakePlayer.getId(), RemovalReason.DISCARDED);
         this.fakePlayer = null;
      }
   }

   private void killFakePlayer() {
      if (this.fakePlayer != null) {
         KillEffectsModule capturedEffects = killEffects;
         FakeplayerCommand.SimulatedPlayer playerToKill = this.fakePlayer;
         capturedEffects.trackTarget(playerToKill);
         this.fakePlayer.killed = true;
      }
   }

   private BlockPos getMiningIgnorePosition() {
      SpeedMineModule capturedMiningModule = speedmine;

      float miningDamage = capturedMiningModule.getMiningProgress();
      Setting<Float> damageSetting = speedmine.damageSetting;
      Float damageThreshold = damageSetting.getValue();
      float normalizedMiningDamage = miningDamage / damageThreshold;
      if (speedmine.isToggled()) {

         if (!(normalizedMiningDamage < 1.0F)) {
            capturedMiningModule = speedmine;

            return capturedMiningModule.getMiningPos();
         }
      }

      return null;
   }

   public FakeplayerCommand() {
      super("fakeplayer");
      this.name = "Herobrine";
      this.setAliases(new String[]{"dummy"});
      ClientAccess.eventBus.subscribe(this);
   }

   static {

      protectedSkinTexture = Identifier.fromNamespaceAndPath(("zio"), ("textures/skin_protect.png"));
      freecam = ClientContext.getModule(FreecamModule.class);
      speedmine = ClientContext.getModule(SpeedMineModule.class);
      logoutSpots = ClientContext.getModule(LogoutSpotsModule.class);
      killEffects = ClientContext.getModule(KillEffectsModule.class);
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {

      ArgumentBuilder nameArgument = Command.argument("name", FancyStringArgument.fancyString()).executes((com.mojang.brigadier.Command<Object>)context -> {

            String requestedName = (String)context.getArgument("name", String.class);
            this.toggleFakePlayer(requestedName);
            return 1;
         });
      LiteralArgumentBuilder commandBranch = (LiteralArgumentBuilder)builder.then(nameArgument);

      ArgumentBuilder recordAction = Command.literal("record").executes((com.mojang.brigadier.Command<Object>)context -> {
            this.playing = false;
            this.recording = !this.recording;
            if (this.recording) {
               this.recordedFrames.clear();

               MutableComponent startedMessage = Component.literal("Started recording.");

               ChatUtils.printChat(
                  startedMessage, ChatUtils.createSignature(-1)
               );
            } else {

               MutableComponent stoppedMessage = Component.literal("Stopped recording.");

               ChatUtils.printChat(
                  stoppedMessage, ChatUtils.createSignature(-1)
               );
            }

            return 1;
         });
      commandBranch = (LiteralArgumentBuilder)commandBranch.then(recordAction);

      ArgumentBuilder playAction = Command.literal("play").executes((com.mojang.brigadier.Command<Object>)context -> {
            this.playbackIndex = 0;
            this.playing = !this.playing;
            this.recording = false;
            return 1;
         });
      commandBranch = (LiteralArgumentBuilder)commandBranch.then(playAction);

      ArgumentBuilder logoutAction = Command.literal("logout").executes((com.mojang.brigadier.Command<Object>)context -> {

         this.toggleLogoutSpot();
         return 1;
      });
      commandBranch = (LiteralArgumentBuilder)commandBranch.then(logoutAction);

      ArgumentBuilder killAction = Command.literal("kill").executes((com.mojang.brigadier.Command<Object>)context -> {

         this.killFakePlayer();
         return 1;
      });
      ((LiteralArgumentBuilder)commandBranch.then(killAction)).executes((com.mojang.brigadier.Command<Object>)context -> {

         this.toggleFakePlayer(("Herobrine"));
         return 1;
      });
   }

   @EventHandler(priority = 100)
   private void interceptFakePlayerAttack(
      PacketEvent.Send event
   ) {
      if (this.fakePlayer != null) {
         sessionReset: {
            if (!(event.getPacket() instanceof ClientboundDisconnectPacket)) {
               if (!(event.getPacket() instanceof ClientboundPlayerCombatKillPacket)) {
                  break sessionReset;
               }
            }

            this.fakePlayer = null;
         }

         if (event.getPacket() instanceof ServerboundAttackPacket attackPacket) {
            // el ATTACK 26.1.2 solo lleva entityId (la accion ya no viaja en el
            // record interact; el backend server-side la procesa como packet
            // dedicado al igual que el 1.21.11).
            FakeplayerCommand.SimulatedPlayer targetPlayer = this.fakePlayer;
            if (targetPlayer != null && targetPlayer.getId() == attackPacket.entityId()) {
               {
                  if (this.fakePlayer.hurtTime == 0) {
                     ClientAccess.zioMinecraft.execute((Runnable)() -> {
                           FakeplayerCommand.SimulatedPlayer hurtPlayer;
                           FakeplayerCommand.SimulatedPlayer capturedPlayer = hurtPlayer = this.fakePlayer;
                           float hurtYaw = hurtPlayer.getYRot();
                           capturedPlayer.animateHurt(hurtYaw);
                           FakeplayerCommand.SimulatedPlayer healthPlayer;
                           capturedPlayer = healthPlayer = this.fakePlayer;
                           float previousHealth = healthPlayer.getHealth();

                           float newHealth = previousHealth - 2.0F;
                           capturedPlayer.setHealth(newHealth);
                        });
                  }

                  event.cancel();
               }
            }
         }
      }
   }

   @EventHandler
   private void clearFakePlayerOnDisconnect(MinecraftDisconnectEvent event) {
      this.fakePlayer = null;
   }

   @EventHandler
   private void updateRecordingAndPlayback(LocalPlayerSendPositionEvent event) {
      if (this.fakePlayer != null) {
         int deathTicks = this.fakePlayer.deathTime;

         if (deathTicks > (6)) {
            this.fakePlayer = null;
         }
      }

      if (this.logoutSpotActive) {
         ElapsedTimer capturedExpirationTimer = this.logoutSpotTimer;

         TimeUnit expirationUnit = TimeUnit.MINUTES;
         if (capturedExpirationTimer.hasElapsedAndReset(5L, expirationUnit)) {

            this.toggleLogoutSpot();
         }
      }

      if (event.getStage() == EventStage.Post
         && this.recording) {
         List framesToRecord = this.recordedFrames;
         framesToRecord.add(new FakeplayerCommand.Frame(
            recordingPosition(),
            ClientAccess.zioMinecraft.player.getYRot(),
            ClientAccess.zioMinecraft.player.getXRot(),
            ClientAccess.zioMinecraft.player.getYHeadRot(),
            ClientAccess.zioMinecraft.player.getDeltaMovement(),
            ClientAccess.zioMinecraft.player.isShiftKeyDown(),
            ClientAccess.zioMinecraft.player.onGround()
         ));
      } else {
         if (event.getStage() == EventStage.Pre
            && this.fakePlayer != null
            && this.playing
            && !this.recordedFrames.isEmpty()) {
            this.playbackIndex = (
                  this.playbackIndex + 1
               )
               % this.recordedFrames.size();
            FakeplayerCommand.Frame frame = (FakeplayerCommand.Frame)this.recordedFrames.get(this.playbackIndex);
            float framePitch = frame.pitch();
            float frameYaw = frame.yaw();
            Vec3 framePosition = frame.position();
            this.fakePlayer.moveOrInterpolateTo(framePosition, frameYaw, framePitch);
            Vec3 frameVelocity = frame.velocity();
            this.fakePlayer.setDeltaMovement(frameVelocity);
            float playbackYaw = frame.yaw();
            this.fakePlayer.setYRot(playbackYaw);
            float playbackPitch = frame.pitch();
            this.fakePlayer.setXRot(playbackPitch);
            float playbackHeadYaw = frame.headYaw();
            this.fakePlayer.setYHeadRot(playbackHeadYaw);
            boolean playbackSneaking = frame.sneaking();
            this.fakePlayer.setShiftKeyDown(playbackSneaking);
            boolean playbackOnGround = frame.onGround();
            this.fakePlayer.setOnGround(playbackOnGround);
            FakeplayerCommand.SimulatedPlayer poseSource = this.fakePlayer;
            if (poseSource.isShiftKeyDown()) {
               Pose crouchingPose = Pose.CROUCHING;
               this.fakePlayer.setPose(crouchingPose);
            } else {
               Pose standingPose = Pose.STANDING;
               this.fakePlayer.setPose(standingPose);
            }
         }
      }
   }

   private static Vec3 recordingPosition() {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      return player.position();
   }

   @EventHandler
   private void simulateExplosionDamage(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundLoginPacket) {
         this.fakePlayer = null;
      }

      if (this.fakePlayer != null) {
         if (event.getPacket() instanceof ClientboundExplodePacket explosionPacket) {
            Vec3 xCenter = explosionPacket.center();
            double explosionX = xCenter.x();
            Vec3 yCenter = explosionPacket.center();
            double explosionY = yCenter.y();
            Vec3 zCenter = explosionPacket.center();
            double explosionZ = zCenter.z();
            Vec3 explosionPosition = this.fakePlayer.position();
            Vec3 distanceCenter = new Vec3(explosionX, explosionY, explosionZ);
            double explosionDistance = explosionPosition.distanceTo(distanceCenter);

            double normalizedDistance = explosionDistance / 12.0D;

            if (normalizedDistance > 1.0D) {
               return;
            }

            explosionPosition = explosionPacket.center();
            FakeplayerCommand.SimulatedPlayer boundsPlayer;
            FakeplayerCommand.SimulatedPlayer capturedBoundsPlayer = boundsPlayer = this.fakePlayer;
            AABB capturedDamageBounds = boundsPlayer.getBoundingBox();

            BlockPos capturedMiningIgnorePosition = this.getMiningIgnorePosition();
            Object additionalIgnorePosition = null;
            BlockPos miningIgnorePosition = capturedMiningIgnorePosition;
            boolean damageFlag = true;
            double explosionPower = 6.0D;
            AABB damageBounds = capturedDamageBounds;
            FakeplayerCommand.SimulatedPlayer damagePlayer = capturedBoundsPlayer;
            Vec3 damageCenter = explosionPosition;
            float damage = DamageUtils.getExplosionDamage(
               damageCenter, damagePlayer, damageBounds, explosionPower, damageFlag, miningIgnorePosition, (BlockPos)additionalIgnorePosition
            );
            if (this.fakePlayer.hurtTime == 0) {
               FakeplayerCommand.SimulatedPlayer healthPlayer = this.fakePlayer;
               float healthAfterDamage = EntityUtils.getHealthWithAbsorption(healthPlayer) - damage;
               float remainingTotalHealth = Math.max(healthAfterDamage, (0.0F));
               float maximumHealth = this.fakePlayer.getMaxHealth();
               AttributeInstance absorptionAttribute = this.fakePlayer.getAttribute(Attributes.MAX_ABSORPTION);
               absorptionAttribute.setBaseValue(16.0D);
               if (remainingTotalHealth > maximumHealth) {
                  this.fakePlayer.setHealth((maximumHealth));
                  float remainingAbsorption = remainingTotalHealth - maximumHealth;
                  this.fakePlayer.setAbsorptionAmount(remainingAbsorption);
               } else {
                  this.fakePlayer.setHealth((remainingTotalHealth));
                  this.fakePlayer.setAbsorptionAmount(0.0F);
               }

               FakeplayerCommand.SimulatedPlayer hurtPlayer;
               FakeplayerCommand.SimulatedPlayer capturedHurtPlayer = hurtPlayer = this.fakePlayer;
               float hurtYaw = hurtPlayer.getYRot();
               capturedHurtPlayer.animateHurt(hurtYaw);
            }
         }
      }
   }

   private void toggleFakePlayer(String requestedName) {
      this.recordedFrames.clear();
      this.recording = false;
      this.playing = false;
      if (this.fakePlayer == null) {
         if (this.logoutSpotActive) {

            this.toggleLogoutSpot();
         }

         this.fakePlayer = new FakeplayerCommand.SimulatedPlayer(
            ClientAccess.zioMinecraft.player,
            ClientAccess.zioMinecraft.level,
            new GameProfile(UUID.randomUUID(), requestedName)
         );
         if (freecam.isToggled()) {
            FakeplayerCommand.SimulatedPlayer capturedPositionTarget = this.fakePlayer;
            double cameraX = freecam.cameraPosition.x;
            double cameraY = freecam.cameraPosition.y;
            double cameraZ = freecam.cameraPosition.z;
            LocalPlayer yawPlayer = ClientAccess.zioMinecraft.player;
            float playerYaw = yawPlayer.getYRot();
            LocalPlayer pitchPlayer = ClientAccess.zioMinecraft.player;
            float spawnPitch = pitchPlayer.getXRot();
            capturedPositionTarget.snapTo((cameraX), (cameraY), (cameraZ), (playerYaw), spawnPitch);
         }

         FakeplayerCommand.SimulatedPlayer capturedPlayer = this.fakePlayer;

         capturedPlayer.setId(-9344);
         FakeplayerCommand.SimulatedPlayer playerToAdd = this.fakePlayer;
         ClientLevel levelToAdd = ClientAccess.zioMinecraft.level;
         levelToAdd.addEntity(playerToAdd);
         capturedPlayer = this.fakePlayer;
         LocalPlayer groundPlayer = ClientAccess.zioMinecraft.player;
         boolean onGround = groundPlayer.onGround();
         capturedPlayer.setOnGround(onGround);
         this.name = requestedName;
         String spawnedMessage = String.valueOf(requestedName) + " has been spawned.";
         MutableComponent spawnComponent = Component.literal(spawnedMessage);

         ChatUtils.printChat(
            spawnComponent, ChatUtils.createSignature(-1)
         );
      } else {
         ClientLevel capturedRemoveLevel = ClientAccess.zioMinecraft.level;

         RemovalReason removalReason = RemovalReason.DISCARDED;
         capturedRemoveLevel.removeEntity(-9344, removalReason);
         this.fakePlayer = null;
         String removedName = this.name == null ? requestedName : this.name;
         String removedMessage = String.valueOf(removedName) + " has been removed.";
         MutableComponent removeComponent = Component.literal(removedMessage);

         ChatUtils.printChat(
            removeComponent, ChatUtils.createSignature(-1)
         );
      }
   }

   private record Frame(
      Vec3 position,
      float yaw,
      float pitch,
      float headYaw,
      Vec3 velocity,
      boolean sneaking,
      boolean onGround
   ) {

   }

   public static class SimulatedPlayer extends RemotePlayer {
      private boolean useProtectedSkin;
      private boolean killed;

      private static final String protectedSkinName;

      @Override
      public boolean isAlive() {
         return !this.killed;
      }

      @Override
      public void tick() {
         if (this.killed) {
            int previousDeathTime = this.deathTime++;
            if (previousDeathTime > (6)) {
               RemovalReason removalReason = RemovalReason.KILLED;
               this.remove(removalReason);
            }

            FakeplayerCommand.SimulatedPlayer absorptionTarget = this;
            absorptionTarget.setAbsorptionAmount(0.0F);
            this.setHealth(0.0F);
         } else {
            AttributeInstance absorptionAttribute = this.getAttribute(Attributes.MAX_ABSORPTION);
            absorptionAttribute.setBaseValue(16.0D);
            this.tickCount++;
            int currentTick = this.tickCount;
            if (currentTick % (80) == 0) {
               Holder resistance = MobEffects.RESISTANCE;
               MobEffectInstance resistanceEffect = new MobEffectInstance(resistance, 900, 1);
               this.addEffect(resistanceEffect);
               this.setHealth(2e+01F);
               this.setAbsorptionAmount(16.0F);
            }

            ItemStack capturedOffhand = this.getOffhandItem();
            Item totem = Items.TOTEM_OF_UNDYING;
            if (!capturedOffhand.is(totem)) {
               Inventory capturedInventory = this.getInventory();
               ItemStack replacementTotem = new ItemStack(Items.TOTEM_OF_UNDYING, 1);
               capturedInventory.setItem(40, replacementTotem);
            }

            super.tick();
         }
      }

      @Override
      public boolean isPushable() {
         return false;
      }

      @Override
      public boolean isAttackable() {
         return true;
      }

      @Override
      public boolean ignoreExplosion(Explosion ignoredExplosion) {
         return false;
      }

      public SimulatedPlayer(Player source, ClientLevel level, GameProfile profile) {
         super(level, profile);
         this.useProtectedSkin = profile.name().equals(protectedSkinName);
         FakeplayerCommand.SimulatedPlayer positionTarget = this;
         positionTarget.copyPosition(source);
         this.yRotO = this.getYRot();
         this.xRotO = this.getXRot();
         this.yHeadRot = source.yHeadRot;
         this.yHeadRotO = this.yHeadRot;
         this.yBodyRot = source.yBodyRot;
         this.yBodyRotO = this.yBodyRot;
         Pose pose = source.getPose();
         this.setPose(pose);
         FakeplayerCommand.SimulatedPlayer healthTarget = this;
         healthTarget.setHealth(20.0F);
         Inventory capturedInventory = this.getInventory();
         Inventory sourceInventory = source.getInventory();
         capturedInventory.replaceWith(sourceInventory);
         this.setInvulnerable((false));
         if (source.isFallFlying()) {
            this.startFallFlying();
         }
      }

      static {
         protectedSkinName = "Herobrine";
      }

      @Override
      public boolean fireImmune() {
         return true;
      }

      @Override
      public void setHealth(float health) {
         super.setHealth(health);
         FakeplayerCommand.SimulatedPlayer healthPlayer = this;
         if (healthPlayer.getHealth() <= 0.0F && !this.killed) {
            super.setHealth(9.0F);
            this.removeAllEffects();
            Holder regeneration = MobEffects.REGENERATION;
            MobEffectInstance regenerationEffect = new MobEffectInstance(regeneration, 900, 1);
            this.addEffect(regenerationEffect);
            Holder resistance = MobEffects.RESISTANCE;
            MobEffectInstance resistanceEffect = new MobEffectInstance(resistance, 900, 1);
            this.addEffect(resistanceEffect);
            ParticleEngine particles = ClientAccess.zioMinecraft.particleEngine;
            SimpleParticleType totemParticles = ParticleTypes.TOTEM_OF_UNDYING;
            particles.createTrackingEmitter(this, totemParticles, 30);
            ClientLevel capturedSoundLevel = ClientAccess.zioMinecraft.level;
            LocalPlayer capturedSoundPlayer = ClientAccess.zioMinecraft.player;
            BlockPos capturedSoundPosition = this.blockPosition();
            SoundEvent totemSound = SoundEvents.TOTEM_USE;
            SoundSource playerSoundCategory = SoundSource.PLAYERS;
            capturedSoundLevel.playSound(capturedSoundPlayer, capturedSoundPosition, totemSound, playerSoundCategory, 1.0F, 1.0F);
            EventBus capturedEventBus = ClientAccess.eventBus;
            PacketEvent.Receive totemEvent = new PacketEvent.Receive(
               new ClientboundEntityEventPacket(this, (byte)35)
            );
            capturedEventBus.post(totemEvent);
         }
      }

      @Override
      protected void tickDeath() {
         this.deathTime = 0;
      }

      @Override
      protected void doPush(Entity ignoredEntity) {
      }

      @Override
      public PlayerSkin getSkin() {
         if (this.useProtectedSkin) {
            PlayerSkin originalSkin = super.getSkin();
            ResourceTexture protectedTexture = new ResourceTexture(
               FakeplayerCommand.protectedSkinTexture,
               FakeplayerCommand.protectedSkinTexture
            );
            return new PlayerSkin(
               protectedTexture,
               originalSkin.cape(),
               originalSkin.elytra(),
               PlayerModelType.WIDE,
               originalSkin.secure()
            );
         } else {
            return super.getSkin();
         }
      }
   }
}
