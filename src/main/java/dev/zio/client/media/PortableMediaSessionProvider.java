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
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class PortableMediaSessionProvider implements AutoCloseable {
    public enum Backend { WINDOWS_GSMTC, LINUX_MPRIS, MACOS_APPLE_EVENTS, UNSUPPORTED_OS }
    public enum Availability { NOT_STARTED, AVAILABLE, UNAVAILABLE, UNSUPPORTED, CLOSED }
    @FunctionalInterface interface SourceFactory { MediaSessionSource open() throws IOException; }
    interface WindowsBackend extends AutoCloseable {
        void start();
        String lastFailure();
        @Override void close();
    }
    @FunctionalInterface interface WindowsFactory { WindowsBackend create(Consumer<MediaSnapshot> update); }
    private static final long POLL_MILLIS = 500L;
    private static final long RETRY_MILLIS = 5_000;
    private final Consumer<MediaSnapshot> update;
    private final SourceFactory sourceFactory;
    private final SourceFactory macSourceFactory;
    private final WindowsFactory windowsFactory;
    private final Backend backend;
    private volatile MediaSnapshot latest = MediaSnapshot.empty();
    private volatile Availability availability = Availability.NOT_STARTED;
    private volatile String lastFailure;
    private volatile MediaSessionSource source;
    private volatile Thread worker;
    private volatile WindowsBackend windows;
    private volatile boolean closed;
    private boolean started;

    public PortableMediaSessionProvider(Consumer<MediaSnapshot> update) {
        this(update, System.getProperty("os.name", ""), MprisSessionSource::connect, MacApplicationMediaSource::connect,
                sink -> new WindowsMediaSessionProvider(state -> sink.accept(new MediaSnapshot(
                        state.playbackStatus(), state.artist(), state.title(), state.positionMillis(), state.durationMillis()))));
    }
    PortableMediaSessionProvider(Consumer<MediaSnapshot> update, String platform, SourceFactory sourceFactory, WindowsFactory windowsFactory) {
        this(update,platform,sourceFactory,MacApplicationMediaSource::connect,windowsFactory);
    }
    PortableMediaSessionProvider(Consumer<MediaSnapshot> update, String platform, SourceFactory sourceFactory, SourceFactory macSourceFactory, WindowsFactory windowsFactory) {
        this.update = Objects.requireNonNull(update);
        this.sourceFactory = Objects.requireNonNull(sourceFactory);
        this.macSourceFactory = Objects.requireNonNull(macSourceFactory);
        this.windowsFactory = Objects.requireNonNull(windowsFactory);
        String os = Objects.requireNonNull(platform).toLowerCase(Locale.ROOT);
        backend = os.startsWith("windows") ? Backend.WINDOWS_GSMTC : os.contains("linux") ? Backend.LINUX_MPRIS : os.contains("mac") || os.contains("darwin") ? Backend.MACOS_APPLE_EVENTS : Backend.UNSUPPORTED_OS;
    }
    public MediaSnapshot latest() { return latest; }
    public Backend backend() { return backend; }
    public Availability availability() { return !closed && windows != null && windows.lastFailure() != null ? Availability.UNAVAILABLE : availability; }
    public String lastFailure() { WindowsBackend w = windows; return w != null ? w.lastFailure() : lastFailure; }

    public synchronized void start() {
        if (closed || started) return;
        started = true;
        if (backend == Backend.WINDOWS_GSMTC) {
            windows = windowsFactory.create(this::publish);
            windows.start();
        } else if (backend == Backend.LINUX_MPRIS || backend == Backend.MACOS_APPLE_EVENTS) {
            worker = new Thread(this::poll, backend == Backend.LINUX_MPRIS ? "zio-mpris-session" : "zio-mac-media");
            worker.setDaemon(true); worker.start();
        } else {
            availability = Availability.UNSUPPORTED;
            lastFailure = "No local-media adapter for this operating system";

        }
    }
    private synchronized void publish(MediaSnapshot state) {
        if (closed) return;
        latest = Objects.requireNonNull(state); availability = Availability.AVAILABLE;
        update.accept(state);
    }
    private synchronized void publishFailure(Throwable failure) {
        if (closed) return;
        lastFailure = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        latest = MediaSnapshot.empty(); availability = Availability.UNAVAILABLE;
        update.accept(latest);
    }
    private void poll() {
        while (!closed && !Thread.currentThread().isInterrupted()) {
            MediaSessionSource opened = null;
            try {
                opened = (backend == Backend.MACOS_APPLE_EVENTS ? macSourceFactory : sourceFactory).open();
                synchronized (this) { if (closed) return; source = opened; }
                while (!closed && !Thread.currentThread().isInterrupted()) {
                    publish(opened.read()); lastFailure = null; Thread.sleep(POLL_MILLIS);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException | LinkageError unavailable) {
                publishFailure(unavailable);
            } finally {
                synchronized (this) { if (source == opened) source = null; }
                if (opened != null) try { opened.close(); } catch (IOException cleanup) { if (!closed) lastFailure = "Media close failed: " + cleanup.getMessage(); }
            }
            if (!closed && !Thread.currentThread().isInterrupted()) {
                try { Thread.sleep(RETRY_MILLIS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        }
    }
    @Override public void close() {
        MediaSessionSource current; WindowsBackend w; Thread thread;
        synchronized (this) {
            if (closed) return;
            closed = true; availability = Availability.CLOSED;
            current = source; w = windows; thread = worker;
        }
        if (thread != null) thread.interrupt();
        if (current != null) try { current.close(); } catch (IOException cleanup) { lastFailure = "Media close failed: " + cleanup.getMessage(); }
        if (w != null) w.close();
    }
}
