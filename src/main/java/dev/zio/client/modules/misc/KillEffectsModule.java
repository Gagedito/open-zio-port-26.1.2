/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import java.util.Set;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.MinecraftDisconnectEvent;
import dev.zio.client.events.PlayerAttackEvent;
import dev.zio.client.render.ClientLightningState;
import dev.zio.client.audio.BuiltinSound;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.audio.SoundClip;
import dev.zio.client.managers.SoundManager;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.utils.collections.NamedIdentifier;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.IdentifierSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.StringSetting;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.awt.Color;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class KillEffectsModule extends Module {
   final Setting<Boolean> killSoundSetting;
   private final Setting<NamedIdentifier> soundSetting;
   private final Setting<NamedIdentifier> selfSoundSetting;
   private final Setting<Float> killSoundVolumeSetting;
   final Setting<Boolean> killStreakSetting;
   private final Setting<Float> killStreakVolumeSetting;
   protected final Setting<Boolean> killSaySetting;
   protected final Setting<Float> delaySetting;
   protected final Setting<String> pathSetting;
   protected final Setting<Boolean> onlyEnemiesSetting;
   protected final Setting<Boolean> randomSetting;
   protected final Setting<Boolean> refreshSetting;
   public final Setting<Boolean> thunderSetting;
   private final Setting<Integer> lightningsSetting;
   public final Setting<Color> colorSetting;
   private final Setting<KillEffectsParticlesChoice> particlesSetting;
   protected final Setting<Set<ParticleType<?>>> particleTypesSetting;
   private final Setting<Boolean> ignoreNakedsSetting;
   private final Setting<Boolean> selfSetting;
   private final KillSayMessages killSayMessages;
   private final ConcurrentHashMap<UUID, Integer> trackedTargets;
   private final ObjectList<UUID> expiredTargets;
   private int killStreak;

   private void spawnKillParticles(Vec3 position) {
      Setting<KillEffectsParticlesChoice> particleOption = this.particlesSetting;
      KillEffectsParticlesChoice particleChoiceSnapshot = particleOption.getValue();
      ParticleOptions particle = particleChoiceSnapshot.chooseParticle(this);
      if (particle == null) {
         return;
      }
      Setting<KillEffectsParticlesChoice> emissionOption = this.particlesSetting;
      KillEffectsParticlesChoice emissionChoice = emissionOption.getValue();
      emissionChoice.emitParticles(particle, position);
   }

   @Override
   public void onEnable() {
      this.killStreak = 0;
      this.killSayMessages.reloadMessages((true));
   }

   private void spawnKillLightning(Vec3 position) {
      Setting<Boolean> thunderOption = this.thunderSetting;
      Boolean spawnThunder = thunderOption.getValue();
      if (!spawnThunder) {
         return;
      }
      for (int index = 0; ; index++) {
         Setting<Integer> lightningsOption = this.lightningsSetting;
         Integer lightningCount = lightningsOption.getValue();
         if (index >= lightningCount) {
            return;
         }

         LightningBolt lightning = new LightningBolt(
            EntityType.LIGHTNING_BOLT, ClientAccess.zioMinecraft.level
         );
         lightning.setPos(position);
         lightning.snapTo(position);
         ((ClientLightningState)lightning).zio$setClient(true);
         ClientLevel level = ClientAccess.zioMinecraft.level;
         level.addEntity(lightning);
      }
   }

   @EventHandler
   private void onPlayerAttackEvent(PlayerAttackEvent event) {
      if (event.getStage() == EventStage.Post) {
         return;
      }
      if (this.isWorldUnavailable()) {
         return;
      }
      if (!(event.getTarget() instanceof Player player)) {
         return;
      }
      if (ClientAccess.zioMinecraft.player == player) {
         return;
      }

      SocialRegistry friendManager = ClientContext.socialRegistry;
      if (friendManager.isFriendPlayer(player)) {
         return;
      }

      if (!PlayerStateUtils.hasArmor((LivingEntity)player)) {
         Setting<Boolean> ignoreNakedsOption = this.ignoreNakedsSetting;
         Boolean ignoreNakeds = ignoreNakedsOption.getValue();
         if (ignoreNakeds) {
            return;
         }
      }

      if (EntityUtils.getHealthWithAbsorption(player) > 0.0F) {
         try {
            ConcurrentHashMap<UUID, Integer> targetsSnapshot = this.trackedTargets;
            UUID uuidSnapshot = player.getUUID();
            Integer trackedTick = (int)(ClientAccess.zioMinecraft.player.tickCount);
            targetsSnapshot.put(uuidSnapshot, trackedTick);
         } catch (Exception trackingFailure) {
         }
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (this.killStreak == -1) {
         this.killStreak = 0;
         Setting<Boolean> selfOption = this.selfSetting;
         Boolean selfEnabled = selfOption.getValue();
         if (!selfEnabled) {
            return;
         }

         Setting<Boolean> selfKillSoundOption = this.killSoundSetting;
         Boolean selfKillSoundEnabled = selfKillSoundOption.getValue();
         if (selfKillSoundEnabled) {
            Setting<Float> selfVolumeCheckOption = this.killSoundVolumeSetting;
            Float selfVolumeCheck = selfVolumeCheckOption.getValue();
            if (selfVolumeCheck > 0.0F) {
               SoundManager selfSoundManagerSnapshot = ClientContext.soundManager;
               Setting<NamedIdentifier> selfSoundOption = this.selfSoundSetting;
               NamedIdentifier selfSoundId = selfSoundOption.getValue();
               SoundClip selfSound = selfSoundManagerSnapshot.getSound(selfSoundId);
               if (selfSound != null) {
                  Setting<Float> selfVolumeReadOption = this.killSoundVolumeSetting;
                  float selfPlaybackVolume = selfVolumeReadOption.getValue();
                  selfSound.play(selfPlaybackVolume);
               }
            }
         }

         LocalPlayer lightningLocalPlayer = ClientAccess.zioMinecraft.player;
         Vec3 selfPositionSnapshot = lightningLocalPlayer.position();

         this.spawnKillLightning(selfPositionSnapshot);
         LocalPlayer particlesLocalPlayer = ClientAccess.zioMinecraft.player;
         selfPositionSnapshot = particlesLocalPlayer.position();
         this.spawnKillParticles(selfPositionSnapshot);
      }

      this.expiredTargets.clear();
      synchronized (this.trackedTargets) {
         KeySetView<UUID, Integer> targetKeySet = this.trackedTargets.keySet();
         Iterator<UUID> targetIds = targetKeySet.iterator();

         while (targetIds.hasNext()) {
            UUID uuid = targetIds.next();

            try {
               ClientLevel targetLevel = ClientAccess.zioMinecraft.level;
               Player player = targetLevel.getPlayerByUUID(uuid);
               if (player == null) {
                  this.expiredTargets.add(uuid);
                  continue;
               }
               int trackedAtTick = ClientAccess.zioMinecraft.player.tickCount;
               Integer trackedTick = this.trackedTargets.get(uuid);
               int ageTicks = trackedAtTick - trackedTick;

               if (ageTicks > (200)) {
                  this.expiredTargets.add(uuid);
                  continue;
               }
               if (!(player.getHealth() <= 0.0F)) {
                  continue;
               }
               this.expiredTargets.add(uuid);
               this.killStreak++;
               KillSayMessages messagesSnapshot = this.killSayMessages;
               String playerName = player.getName().getString();
               messagesSnapshot.sendKillMessage(player, playerName);
               Vec3 lightningPositionSnapshot = player.position();

               this.spawnKillLightning(lightningPositionSnapshot);
               Vec3 particlesPositionSnapshot = player.position();
               this.spawnKillParticles(particlesPositionSnapshot);
               Setting<Boolean> killSoundOption = this.killSoundSetting;
               Boolean killSoundEnabled = killSoundOption.getValue();
               if (killSoundEnabled) {
                  Setting<Float> killVolumeCheckOption = this.killSoundVolumeSetting;
                  Float killVolumeCheck = killVolumeCheckOption.getValue();
                  if (killVolumeCheck > 0.0F) {
                     SoundManager killSoundManagerSnapshot = ClientContext.soundManager;
                     Setting<NamedIdentifier> killSoundIdOption = this.soundSetting;
                     NamedIdentifier killSoundId = killSoundIdOption.getValue();
                     SoundClip killSound = killSoundManagerSnapshot.getSound(
                        killSoundId
                     );
                     if (killSound != null) {
                        Setting<Float> killVolumeReadOption = this.killSoundVolumeSetting;
                        float killPlaybackVolume = killVolumeReadOption.getValue();
                        killSound.play(killPlaybackVolume);
                     }
                  }
               }

               Setting<Boolean> streakOption = this.killStreakSetting;
               Boolean streakEnabled = streakOption.getValue();
               if (streakEnabled) {
                  int currentStreak = this.killStreak;

                  if (currentStreak >= (2)) {
                     Setting<Float> streakVolumeCheckOption = this.killStreakVolumeSetting;
                     Float streakVolumeCheck = streakVolumeCheckOption.getValue();
                     if (streakVolumeCheck > 0.0F) {
                        SoundManager streakSoundManagerSnapshot = ClientContext.soundManager;
                        int streakCount = this.killStreak;
                        SoundClip streakSound = streakSoundManagerSnapshot.getKillstreakSound(streakCount);
                        if (streakSound != null) {
                           Setting<Float> streakVolumeReadOption = this.killStreakVolumeSetting;
                           float streakPlaybackVolume = streakVolumeReadOption.getValue();
                           streakSound.play(streakPlaybackVolume);
                        }
                     }
                  }
               }
            } catch (Exception trackingFailure) {
            }
         }
      }

      synchronized (this.expiredTargets) {
         try {
            ObjectListIterator<UUID> expiredIterator = this.expiredTargets.iterator();

            while (expiredIterator.hasNext()) {
               UUID expiredUuid = expiredIterator.next();
               this.trackedTargets.remove(expiredUuid);
            }
         } catch (Exception removalFailure) {
         }
      }
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      this.killStreak = 0;
   }

   public void trackTarget(Player player) {
      if (player == ClientAccess.zioMinecraft.player) {
         return;
      }
      SocialRegistry friendManagerSnapshot = ClientContext.socialRegistry;
      String nameSnapshot = player.getName().getString();

      if (friendManagerSnapshot.isFriendName(nameSnapshot)) {
         return;
      }
      try {
         ConcurrentHashMap<UUID, Integer> targetsSnapshot = this.trackedTargets;
         UUID uuidSnapshot = player.getUUID();
         Integer trackedTick = (int)(ClientAccess.zioMinecraft.player.tickCount);
         targetsSnapshot.put(uuidSnapshot, trackedTick);
      } catch (Exception trackingFailure) {
      }
   }

   @EventHandler
   private void onPacketReceive(
      PacketEvent.Receive event
   ) {
      if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket) {
         this.killStreak = -1;
      }
   }

   public KillEffectsModule() {
      super("KillEffects", "Does various things when someone dies.", ModuleCategory.MISC, new String[0]);
      Boolean killSoundDefault = false;
      BooleanSetting killSoundDefinition = new BooleanSetting(("KillSound"), killSoundDefault);
      killSoundDefinition.markVisibilityDependency();
      this.killSoundSetting = this.add(killSoundDefinition);
      Predicate<Object> soundPredicate = ignored -> this.killSoundSetting.isExpanded();
      BuiltinSound cssSound = BuiltinSound.CSS;
      NamedIdentifier defaultSound = cssSound.getIdentifier();
      this.soundSetting = this.add(new IdentifierSetting(("Sound"), defaultSound, soundPredicate));
      Predicate<Object> selfSoundPredicate = ignored -> this.killSoundSetting.isExpanded();
      BuiltinSound bodySplatSound = BuiltinSound.BODYSPLAT;
      NamedIdentifier defaultSelfSound = bodySplatSound.getIdentifier();
      this.selfSoundSetting = this.add(new IdentifierSetting(("SelfSound"), defaultSelfSound, selfSoundPredicate));
      Predicate<Object> killVolumePredicate = ignored -> this.killSoundSetting.isExpanded();
      this.killSoundVolumeSetting = this.add(new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F, killVolumePredicate));
      Boolean killStreakDefault = false;
      BooleanSetting killStreakDefinition = new BooleanSetting(("KillStreak"), killStreakDefault);
      killStreakDefinition.markVisibilityDependency();
      this.killStreakSetting = this.add(killStreakDefinition);
      Predicate<Object> streakVolumePredicate = ignored -> this.killStreakSetting.isExpanded();
      this.killStreakVolumeSetting = this.add(new NumberSetting<>("Volume", 0.4F, 0.0F, 1.0F, streakVolumePredicate));
      Boolean killSayDefault = false;
      BooleanSetting killSayDefinition = new BooleanSetting(("KillSay"), killSayDefault);
      killSayDefinition.markVisibilityDependency();
      Setting<Boolean> registeredKillSay = this.add(killSayDefinition);
      this.killSaySetting = registeredKillSay;
      NumberSetting<Float> delayDefinition = new NumberSetting<>("Delay", 0.0F, 0.0F, 5.0F);
      delayDefinition.withUnit(("s"));
      this.delaySetting = this.add(delayDefinition.dependsOn(registeredKillSay));
      StringSetting pathDefinition = new StringSetting(("Path"), ("killsay.txt"));
      this.pathSetting = this.add(pathDefinition.dependsOn(registeredKillSay));
      Boolean onlyEnemiesDefault = false;
      BooleanSetting onlyEnemiesDefinition = new BooleanSetting(("OnlyEnemies"), onlyEnemiesDefault);
      this.onlyEnemiesSetting = this.add(onlyEnemiesDefinition.dependsOn(registeredKillSay));
      Boolean randomDefault = true;
      BooleanSetting randomDefinition = new BooleanSetting(("Random"), randomDefault);
      this.randomSetting = this.add(randomDefinition.dependsOn(registeredKillSay));
      Boolean refreshDefault = false;
      BooleanSetting refreshDefinition = new BooleanSetting(("Refresh"), refreshDefault);
      this.refreshSetting = this.add(refreshDefinition.dependsOn(registeredKillSay));
      Boolean thunderDefault = true;
      BooleanSetting thunderDefinition = new BooleanSetting(("Thunder"), thunderDefault);
      thunderDefinition.markVisibilityDependency();
      Setting<Boolean> registeredThunder = this.add(thunderDefinition);
      this.thunderSetting = registeredThunder;
      NumberSetting<Integer> lightningsDefinition = new NumberSetting<>("Lightnings", 1, 1, 4);
      this.lightningsSetting = this.add(lightningsDefinition.dependsOn(registeredThunder));
      Color defaultColor = new Color(-8355712, true);
      ColorSetting colorDefinition = new ColorSetting(("Color"), defaultColor);
      this.colorSetting = this.add(colorDefinition.dependsOn(registeredThunder));
      KillEffectsParticlesChoice defaultParticles = KillEffectsParticlesChoice.OFF;
      this.particlesSetting = this.add(new EnumSetting<>(("Particles"), defaultParticles));
      Registry particleRegistry = BuiltInRegistries.PARTICLE_TYPE;
      ParticleType[] defaultParticleTypes = new ParticleType[0];
      this.particleTypesSetting = this.add(new RegistrySetSetting<>(("ParticleTypes"), particleRegistry, (Object[]) defaultParticleTypes));
      Boolean targetsDefault = false;
      BooleanSetting targetsDefinition = new BooleanSetting(("Targets"), targetsDefault);
      targetsDefinition.markAsGroup();
      targetsDefinition.markVisibilityDependency();
      Setting<Boolean> registeredTargets = this.add(targetsDefinition);
      Boolean ignoreNakedsDefault = true;
      BooleanSetting ignoreNakedsDefinition = new BooleanSetting(("IgnoreNakeds"), ignoreNakedsDefault);
      this.ignoreNakedsSetting = this.add(ignoreNakedsDefinition.dependsOn(registeredTargets));
      Boolean selfDefault = false;
      BooleanSetting selfDefinition = new BooleanSetting(("Self"), selfDefault);
      this.selfSetting = this.add(selfDefinition.dependsOn(registeredTargets));
      this.killSayMessages = new KillSayMessages(this);
      this.trackedTargets = new ConcurrentHashMap<>();
      this.expiredTargets = ObjectLists.synchronize(new ObjectArrayList<>());
      this.killStreak = 0;
      Setting<?> callbackOption = this.killStreakVolumeSetting;

      callbackOption.setConfigName(("KillStreakVolume"));
      this.killSaySetting.setChangeCallback(() -> {
         KillSayMessages messagesSnapshot = this.killSayMessages;

         boolean force = false;
         messagesSnapshot.reloadMessages(force);
      });
      this.pathSetting.setChangeCallback(() -> {
         KillSayMessages messagesSnapshot = this.killSayMessages;

         boolean force = true;
         messagesSnapshot.reloadMessages(force);
      });
      this.refreshSetting.setChangeCallback(() -> {
         Setting<Boolean> refreshOption = this.refreshSetting;
         Boolean refresh = refreshOption.getValue();
         if (refresh) {
            KillSayMessages messagesSnapshot = this.killSayMessages;

            boolean force = true;
            messagesSnapshot.reloadMessages(force);
            this.refreshSetting.setValue(false);
         }
      });
      this.randomSetting.setChangeCallback(this.killSayMessages::resetSequence);
      callbackOption = this.particleTypesSetting;
      Predicate customParticlesPredicate = ignoredParticleOption -> {
         return this.particlesSetting.getValue() == KillEffectsParticlesChoice.CUSTOM;
      };

      callbackOption.addVisibilityPredicate(customParticlesPredicate);

      this.setDrawn(false);
   }

}
