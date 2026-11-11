/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.audio;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class StreamSoundInstance extends AbstractSoundInstance {
   private final InputStream inputStream;

   public StreamSoundInstance(Vec3 position, InputStream inputStream) {
      super(
         Identifier.fromNamespaceAndPath(
            "zio", "sound"
         ),
         SoundSource.MASTER,
         SoundInstance.createUnseededRandom()
      );
      this.x = position.x;
      this.y = position.y;
      this.z = position.z;
      this.inputStream = inputStream;
   }

   public StreamSoundInstance(InputStream inputStream) {
      super(
         Identifier.fromNamespaceAndPath(
            "zio", "sound"
         ),
         SoundSource.MASTER,
         SoundInstance.createUnseededRandom()
      );
      this.inputStream = inputStream;
      this.relative = true;
      this.x = 0.0;
      this.y = 0.0;

      this.z = -1.0D;
   }

   public CompletableFuture<net.minecraft.client.sounds.AudioStream> getAudioStream(SoundBufferLibrary soundBuffers, Identifier identifier, boolean repeatInstantly) {
      try {
         JOrbisAudioStream audioStream = new JOrbisAudioStream(this.inputStream);
         return CompletableFuture.completedFuture(audioStream);
      } catch (Exception exception) {
         try { this.inputStream.close(); } catch (java.io.IOException cleanup) { exception.addSuppressed(cleanup); }
         return CompletableFuture.failedFuture(exception);
      }
   }

}
