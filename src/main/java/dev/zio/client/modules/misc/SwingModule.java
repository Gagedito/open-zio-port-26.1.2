/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;


import java.util.function.Predicate;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;

public class SwingModule extends Module {
   public Setting<HandChoice> handSetting;
   public final Setting<TypeChoice> typeSetting;
   public final Setting<Boolean> shakeSetting;
   public final Setting<Double> speedSetting;
   private long nextShakeTimeMillis;
   private int shakeTicksRemaining;

   public boolean consumeShakeTick() {
      if (this.shakeTicksRemaining > 0) {
         this.shakeTicksRemaining--;
         return true;
      } else {
         return false;
      }
   }

   @EventHandler
   private void onConnectionSendPacketEvent(
      PacketEvent.Send event
   ) {
      if (!(event.getPacket() instanceof ServerboundSwingPacket)) {
         return;
      }
      if (System.currentTimeMillis() <= this.nextShakeTimeMillis) {
         return;
      }
      long nextShakeTime = System.currentTimeMillis();
      ThreadLocalRandom random = ThreadLocalRandom.current();

      nextShakeTime += random.nextInt(350);

      this.nextShakeTimeMillis = nextShakeTime + 150L;

      this.shakeTicksRemaining = 3;
   }

   public SwingModule() {
      super("Swing", "Changes your swinging hand.", ModuleCategory.MISC, new String[0]);
      SwingModule.HandChoice defaultHand = SwingModule.HandChoice.MAINHAND;
      this.handSetting = this.add(new EnumSetting<>(("Hand"), defaultHand));
      SwingModule.TypeChoice defaultType = SwingModule.TypeChoice.VANILLA;
      this.typeSetting = this.add(new EnumSetting<>(("Type"), defaultType));
      Predicate<Object> shakeVisibility = ignored -> this.typeSetting.getValue() == SwingModule.TypeChoice.ONE_TWELVE;
      Boolean defaultShake = false;
      this.shakeSetting = this.add(new BooleanSetting(("Shake"), defaultShake, shakeVisibility));
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0, 0.1, 3.0));
   }

   public enum HandChoice implements NamedEntry {
      VANILLA("Vanilla"),
      MAINHAND("Mainhand", InteractionHand.MAIN_HAND),
      OFFHAND("Offhand", InteractionHand.OFF_HAND),
      PACKET("Packet");

      private final InteractionHand hand;
      private final String displayName;

      public InteractionHand getHand() {
         return this.hand;
      }

      @Override
      public String getName() {
         return this.displayName;
      }

      HandChoice(String displayName, InteractionHand hand) {
         this.displayName = displayName;
         this.hand = hand;
      }

      HandChoice(String displayName) {
         this(displayName, null);
      }
   }

   public enum TypeChoice implements NamedEntry {
      VANILLA("Vanilla"),
      ONE_TWELVE("1.12"),
      ONE_EIGHT("1.8");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      TypeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
