/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.config;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

public final class PresetUndoState {
   private static final int HISTORY_LIMIT = 5;
   private final Deque<PresetUndoAction> actions = new ArrayDeque<>();

   public void push(PresetUndoAction action) {
      if (action == null) {
         return;
      }
      this.actions.addLast(action);
      while (this.actions.size() > HISTORY_LIMIT) {
         this.actions.removeFirst();
      }
   }

   public PresetUndoAction undo() throws IOException {
      PresetUndoAction action = this.actions.peekLast();
      if (action != null) {
         action.undo();
         this.actions.removeLast();
      }
      return action;
   }
}
