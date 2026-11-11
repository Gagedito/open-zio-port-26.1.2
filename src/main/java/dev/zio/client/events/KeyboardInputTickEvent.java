/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.events;

import dev.zio.client.utils.InputUtils;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

public class KeyboardInputTickEvent extends CancellableEvent {
    private final ClientInput input;

    public KeyboardInputTickEvent(ClientInput input) {
        this.input = input;
    }

    public ClientInput getInput() {
        return input;
    }

    public void reset() {
        InputUtils.clearKeyPresses(input);
        input.moveVector = Vec2.ZERO;
    }

    public static class Pre extends KeyboardInputTickEvent {
        public Pre(ClientInput input) {
            super(input);
        }
    }
}
