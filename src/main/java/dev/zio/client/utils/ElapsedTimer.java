/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils;

import java.util.concurrent.TimeUnit;

public final class ElapsedTimer {
    long lastResetMillis;

    public ElapsedTimer() {
        lastResetMillis = -1L;
    }

    public long elapsedMillis() {
        return System.currentTimeMillis() - lastResetMillis;
    }

    public void reset() {
        lastResetMillis = System.currentTimeMillis();
    }

    public void setTime(long timestampMillis) {
        lastResetMillis = timestampMillis;
    }

    public boolean hasElapsedAndReset(long amount, TimeUnit unit) {
        long millis = toMillis(amount, unit);
        return hasElapsedAndReset(millis);
    }

    public boolean hasElapsed(double amount, TimeUnit unit) {
        long millis = toMillis(amount, unit);
        return hasElapsed(millis);
    }

    public boolean hasElapsed(long millis) {
        return System.currentTimeMillis() - lastResetMillis >= millis;
    }

    public void setTime(double timestamp, TimeUnit unit) {
        lastResetMillis = toMillis(timestamp, unit);
    }

    public void clear() {
        lastResetMillis = -1L;
    }

    public boolean hasElapsedAndReset(long millis) {
        boolean elapsed = hasElapsed(millis);
        if (elapsed) {
            reset();
        }
        return elapsed;
    }

    private static long toMillis(double value, TimeUnit unit) {
        return switch (unit) {
            case NANOSECONDS -> {
                double scaled = value * 0.001;
                yield (long)(scaled * 0.001);
            }
            case MICROSECONDS -> (long)(value * 0.001);
            case MILLISECONDS -> (long)value;
            case SECONDS -> (long)(value * 1000.0);
            case MINUTES -> {
                double scaled = value * 1000.0;
                yield (long)(scaled * 60.0);
            }
            case HOURS -> {
                double scaled = value * 1000.0;
                scaled *= 60.0;
                yield (long)(scaled * 60.0);
            }
            case DAYS -> {
                double scaled = value * 1000.0;
                scaled *= 60.0;
                scaled *= 60.0;
                yield (long)(scaled * 24.0);
            }
        };
    }
}
