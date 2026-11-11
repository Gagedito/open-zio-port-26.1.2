/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils;

public final class UncheckedExceptions {
    private UncheckedExceptions() {}

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException rethrow(Throwable failure) throws T {
        throw (T) failure;
    }
}
