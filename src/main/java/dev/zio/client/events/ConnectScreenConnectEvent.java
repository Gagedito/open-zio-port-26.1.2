/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public final class ConnectScreenConnectEvent extends CancellableEvent {
   private final String serverAddress;

   public String getServerAddress() {
      return this.serverAddress;
   }

   public ConnectScreenConnectEvent(String serverAddress) {
      this.serverAddress = serverAddress;
   }
}
