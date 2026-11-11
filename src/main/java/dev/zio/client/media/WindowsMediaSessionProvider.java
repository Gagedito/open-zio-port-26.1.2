/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.media;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WindowsMediaSessionProvider implements PortableMediaSessionProvider.WindowsBackend {
    public static final int PLAYING = 4;
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(3);

    private static final long POLL_MILLIS = 500L;
    private static final long RETRY_MILLIS = 5_000;

    public record MediaState(int playbackStatus, String artist, String title,
                             long positionMillis, long durationMillis) {
        public MediaState {
            Objects.requireNonNull(artist, "Artist");
            Objects.requireNonNull(title, "Title");
        }

        public static MediaState empty() {
            return new MediaState(0, "", "", 0, 0);
        }
    }

    private final Consumer<MediaState> update;
    private final Supplier<RuntimeApi> runtime;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile MediaState latest = MediaState.empty();
    private volatile String lastFailure;
    private volatile Thread worker;

    public WindowsMediaSessionProvider(Consumer<MediaState> update) {
        this(update, () -> Native.load("combase", RuntimeApi.class));
    }

    WindowsMediaSessionProvider(Consumer<MediaState> update, Supplier<RuntimeApi> runtime) {
        this.update = Objects.requireNonNull(update, "Media update consumer");
        this.runtime = Objects.requireNonNull(runtime, "Windows runtime API supplier");
    }

    public MediaState latest() {
        return latest;
    }

    @Override
    public String lastFailure() {
        return lastFailure;
    }

    @Override
    public synchronized void start() {
        if (closed.get() || !started.compareAndSet(false, true)) return;
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            publish(MediaState.empty());
            return;
        }
        Thread thread = new Thread(this::poll, "zio-media-session");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    private void publish(MediaState state) {
        if (closed.get()) return;
        latest = state;
        update.accept(state);
    }

    private void poll() {
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            try {
                RuntimeApi api = runtime.get();
                try (SessionReader reader = new SessionReader(api, ASYNC_TIMEOUT)) {
                    while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                        publish(reader.read());
                        lastFailure = null;
                        Thread.sleep(POLL_MILLIS);
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException | LinkageError unavailable) {
                lastFailure = unavailable.getClass().getSimpleName() + ": " + unavailable.getMessage();
                publish(MediaState.empty());
                try {
                    Thread.sleep(RETRY_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        Thread thread = worker;
        if (thread != null) thread.interrupt();

    }

    public interface RuntimeApi extends StdCallLibrary {
        int RoInitialize(int apartmentType);
        void RoUninitialize();
        int RoGetActivationFactory(Pointer className, Pointer iid, PointerByReference factory);
        int WindowsCreateString(Pointer text, int utf16Units, PointerByReference result);
        int WindowsDeleteString(Pointer value);
        Pointer WindowsGetStringRawBuffer(Pointer value, IntByReference utf16Units);
    }

    static final class SessionReader implements AutoCloseable {
        private static final String RUNTIME_CLASS =
                "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
        private static final String MANAGER_STATICS = "2050c4ee-11a0-57de-aed7-c97c70338245";
        private static final String ASYNC_INFO = "00000036-0000-0000-c000-000000000046";
        private final RuntimeApi api;
        private final Duration timeout;
        private final Thread owner = Thread.currentThread();
        private ComReference manager;
        private boolean initialized;
        private boolean stopped;

        SessionReader(RuntimeApi api, Duration timeout) throws InterruptedException {
            this.api = Objects.requireNonNull(api, "Windows runtime API");
            this.timeout = Objects.requireNonNull(timeout, "Async timeout");
            if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Async timeout must be positive");

            check(api.RoInitialize(1), "RoInitialize(MTA)");
            initialized = true;
            try (StringHandle name = StringHandle.create(api, RUNTIME_CLASS);
                 Memory iid = guid(MANAGER_STATICS)) {
                PointerByReference result = new PointerByReference();
                check(api.RoGetActivationFactory(name.pointer, iid, result), "RoGetActivationFactory");
                try (ComReference factory = ComReference.required(result.getValue());
                     ComReference operation = factory.object(6)) {
                    manager = awaitObject(operation);
                }
            } catch (RuntimeException | Error | InterruptedException failure) {
                close();
                throw failure;
            }
        }

        MediaState read() throws InterruptedException {
            requireOwner();
            if (stopped) throw new IllegalStateException("Media reader is closed");
            try (ComReference session = manager.optionalObject(6)) {
                if (session == null) return MediaState.empty();
                int status;
                try (ComReference info = session.object(9)) {
                    status = info.integer(7);
                }
                String artist;
                String title;
                try (ComReference operation = session.object(7);
                     ComReference properties = awaitObject(operation)) {
                    title = properties.string(api, 6);
                    artist = properties.string(api, 9);
                }
                long position = 0L;
                long duration = 0L;
                try (ComReference timeline = session.optionalObject(8)) {
                    if (timeline != null) {
                        long start = timeline.longInteger(6);
                        long end = timeline.longInteger(7);
                        long current = timeline.longInteger(10);

                        position = (current - start) / 10_000;
                        duration = (end - start) / 10_000;
                    }
                }
                return new MediaState(status, artist, title, position, duration);
            }
        }

        private ComReference awaitObject(ComReference operation) throws InterruptedException {
            requireOwner();
            try (ComReference info = operation.query(ASYNC_INFO)) {
                boolean terminal = false;
                try {
                    long startedAt = System.nanoTime();
                    long limit = timeout.toNanos();
                    for (;;) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Media query interrupted");
                        int status = info.integer(7);
                        if (status == 1) {
                            terminal = true;
                            return operation.object(8);
                        }
                        if (status == 2) {
                            terminal = true;
                            throw new WinRtException("Media query canceled", 0x800704c7);
                        }
                        if (status == 3) {
                            terminal = true;
                            throw new WinRtException("Media query failed", info.integer(8));
                        }
                        if (status != 0) throw new IllegalStateException("Unknown async media status " + status);
                        if (System.nanoTime() - startedAt >= limit) throw new WinRtException("Media query timed out", 0x800705b4);
                        Thread.sleep(10);
                    }
                } finally {
                    if (!terminal) info.bestEffort(9);
                    info.bestEffort(10);
                }
            }
        }

        private void requireOwner() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("COM media reader used from another thread");
        }

        @Override
        public void close() {
            requireOwner();
            if (stopped) return;
            stopped = true;
            try {
                if (manager != null) manager.close();
            } finally {
                if (initialized) {
                    initialized = false;
                    api.RoUninitialize();
                }
            }
        }
    }

    static final class ComReference implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private Pointer pointer;

        private ComReference(Pointer pointer) {
            this.pointer = pointer;
        }

        static ComReference required(Pointer pointer) {
            if (pointer == null || Pointer.nativeValue(pointer) == 0) throw new IllegalStateException("Windows media API returned a null interface");
            return new ComReference(pointer);
        }

        private int invoke(int slot, Object... arguments) {
            if (Thread.currentThread() != owner) throw new IllegalStateException("COM reference used from another thread");
            if (pointer == null) throw new IllegalStateException("COM reference is released");
            Pointer table = pointer.getPointer(0);
            Pointer address = table.getPointer((long)slot * Native.POINTER_SIZE);
            if (address == null) throw new IllegalStateException("Null Windows media vtable slot " + slot);
            Object[] call = new Object[arguments.length + 1];
            call[0] = pointer;
            System.arraycopy(arguments, 0, call, 1, arguments.length);
            return Function.getFunction(address, Function.ALT_CONVENTION).invokeInt(call);
        }

        ComReference query(String interfaceId) {
            try (Memory iid = guid(interfaceId)) {
                PointerByReference result = new PointerByReference();
                check(invoke(0, iid, result), "QueryInterface");
                return required(result.getValue());
            }
        }

        ComReference optionalObject(int slot) {
            PointerByReference result = new PointerByReference();
            check(invoke(slot, result), "Media interface method " + slot);
            Pointer value = result.getValue();
            return value == null || Pointer.nativeValue(value) == 0 ? null : required(value);
        }

        ComReference object(int slot) {
            ComReference result = optionalObject(slot);
            if (result == null) throw new IllegalStateException("Windows media method " + slot + " returned no required object");
            return result;
        }

        int integer(int slot) {
            IntByReference value = new IntByReference();
            check(invoke(slot, value), "Media integer method " + slot);
            return value.getValue();
        }

        long longInteger(int slot) {
            LongByReference value = new LongByReference();
            check(invoke(slot, value), "Media TimeSpan method " + slot);
            return value.getValue();
        }

        String string(RuntimeApi api, int slot) {
            PointerByReference value = new PointerByReference();
            check(invoke(slot, value), "Media string method " + slot);
            try (StringHandle string = new StringHandle(api, value.getValue())) {
                return string.text();
            }
        }

        void bestEffort(int slot) {
            try {
                invoke(slot);
            } catch (RuntimeException cleanupFailure) {

            }
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("COM reference released from another thread");
            if (pointer == null) return;
            try {
                invoke(2);
            } finally {
                pointer = null;
            }
        }
    }

    static final class StringHandle implements AutoCloseable {
        private final RuntimeApi api;
        private Pointer pointer;
        private boolean released;

        private StringHandle(RuntimeApi api, Pointer pointer) {
            this.api = api;
            this.pointer = pointer;
        }

        static StringHandle create(RuntimeApi api, String text) {
            try (Memory utf16 = new Memory(Math.max(2L, (long)text.length() * 2))) {
                for (int i = 0; i < text.length(); i++) utf16.setShort((long)i * 2, (short)text.charAt(i));
                PointerByReference result = new PointerByReference();
                check(api.WindowsCreateString(utf16, text.length(), result), "WindowsCreateString");
                return new StringHandle(api, result.getValue());
            }
        }

        String text() {
            if (released) throw new IllegalStateException("Windows string is released");
            IntByReference length = new IntByReference();
            Pointer raw = api.WindowsGetStringRawBuffer(pointer, length);
            int count = length.getValue();
            if (count < 0 || count > 1_048_576) throw new IllegalStateException("Invalid media string length " + count);
            if (count == 0) return "";
            if (raw == null) throw new IllegalStateException("Windows string has no data");
            short[] units = raw.getShortArray(0, count);
            char[] text = new char[count];
            for (int i = 0; i < count; i++) text[i] = (char)(units[i] & 0xffff);
            return new String(text);
        }

        @Override
        public void close() {
            if (released) return;
            released = true;
            api.WindowsDeleteString(pointer);
            pointer = null;
        }
    }

    static Memory guid(String text) {
        UUID id = UUID.fromString(text);
        Memory bytes = new Memory(16);
        long high = id.getMostSignificantBits();
        long low = id.getLeastSignificantBits();
        bytes.setInt(0, (int)(high >>> 32));
        bytes.setShort(4, (short)(high >>> 16));
        bytes.setShort(6, (short)high);
        for (int i = 0; i < 8; i++) bytes.setByte(8 + i, (byte)(low >>> (56 - 8 * i)));
        return bytes;
    }

    private static void check(int result, String operation) {
        if (result < 0) throw new WinRtException(operation, result);
    }

    public static final class WinRtException extends RuntimeException {
        private final int result;

        WinRtException(String operation, int result) {
            super(operation + " failed (HRESULT 0x" + Integer.toHexString(result) + ")");
            this.result = result;
        }

        public int result() {
            return result;
        }
    }
}
