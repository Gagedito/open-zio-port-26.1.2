/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

public final class CommandExecuteEvent extends CancellableEvent {
   private final String command;

   public String getCommand() {
      return this.command;
   }

   public CommandExecuteEvent(String command) {
      this.command = command;
   }
}
