/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.media;

import java.io.IOException;
import java.util.List;

record MacGetDataRequest(int eventClass, int eventId, List<Integer> propertyPath,
                         int sendMode, double timeoutSeconds) {
    static MacGetDataRequest create(int pid, List<Integer> path, int mode, long timeoutTicks) throws IOException {
        if (pid <= 0 || path == null || path.isEmpty() || path.size() > 2 || path.stream().anyMatch(v -> v == null)) {
            throw new IOException("Unsupported PID property request");
        }
        if (mode != MacApplicationMediaSource.READ_SEND_MODE || timeoutTicks != MacApplicationMediaSource.REPLY_TIMEOUT_TICKS) {
            throw new IOException("Unreviewed Apple event options");
        }
        return new MacGetDataRequest(MacScriptingDictionary.code("core"), MacScriptingDictionary.code("getd"),
            List.copyOf(path), mode, timeoutTicks / 60.0);
    }
}
