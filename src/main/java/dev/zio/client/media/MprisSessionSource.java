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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MprisSessionSource implements MediaSessionSource {
    interface Connection extends AutoCloseable {
        Object call(String destination, String path, String iface, String member, List<String> arguments) throws IOException;
        @Override void close() throws IOException;
    }
    private final Connection connection;
    private String selectedPlayer;
    MprisSessionSource(Connection connection) { this.connection = Objects.requireNonNull(connection); }
    public static MprisSessionSource connect() throws IOException {
        return new MprisSessionSource(DbusSessionConnection.connect(System.getenv("DBUS_SESSION_BUS_ADDRESS")));
    }
    @Override public MediaSnapshot read() throws IOException {
        Object result = connection.call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "ListNames", List.of());
        if (!(result instanceof List<?> names) || names.stream().anyMatch(name -> !(name instanceof String))) throw new IOException("Invalid D-Bus player list");
        List<String> players = names.stream().map(String.class::cast).filter(name -> name.startsWith("org.mpris.MediaPlayer2.")).sorted().toList();
        List<String> ordered = new ArrayList<>(players);
        if (selectedPlayer != null && ordered.remove(selectedPlayer)) ordered.add(0, selectedPlayer);
        MediaSnapshot fallback = MediaSnapshot.empty(); String fallbackPlayer = null;
        for (String player : ordered) {
            Object response;
            try { response = connection.call(player, "/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties", "GetAll", List.of("org.mpris.MediaPlayer2.Player")); }
            catch (DbusSessionConnection.MethodFailure gone) { continue; }
            MediaSnapshot state = fromProperties(response);
            if (state.playbackStatus() == MediaSnapshot.PLAYING) { selectedPlayer = player; return state; }
            if (fallbackPlayer == null) { fallback = state; fallbackPlayer = player; }
        }
        selectedPlayer = fallbackPlayer;
        return fallback;
    }
    static MediaSnapshot fromProperties(Object response) throws IOException {
        if (!(response instanceof Map<?,?> properties)) throw new IOException("Invalid MPRIS properties");
        String playback = string(property(properties, "PlaybackStatus", "s"));
        int status = switch (playback) { case "Playing" -> 4; case "Paused" -> 5; case "Stopped" -> 3; default -> 0; };
        Object metadata = property(properties, "Metadata", "a{sv}");
        if (metadata == null) metadata = Map.of();
        if (!(metadata instanceof Map<?,?> track)) throw new IOException("Invalid MPRIS track metadata");
        String title = string(property(track, "xesam:title", "s"));
        Object artists = property(track, "xesam:artist", "as");
        String artist = "";
        if (artists != null) {
            if (!(artists instanceof List<?> list) || list.stream().anyMatch(value -> !(value instanceof String))) throw new IOException("Invalid MPRIS artist list");
            artist = String.join(", ", list.stream().map(String.class::cast).toList());
        }
        return new MediaSnapshot(status, artist, title, millis(property(properties, "Position", "x")), millis(property(track, "mpris:length", "x")));
    }
    private static Object property(Map<?,?> values, String key, String signature) throws IOException {
        Object value = values.get(key);
        if (value instanceof DbusMessageCodec.Variant variant) {
            if (!variant.signature().equals(signature)) throw new IOException("Unexpected MPRIS type for " + key);
            return variant.value();
        }
        return value;
    }
    private static String string(Object value) throws IOException { if (value == null) return ""; if (value instanceof String text) return text; throw new IOException("Invalid MPRIS string property"); }
    private static long millis(Object value) throws IOException { if (value == null) return 0; if (value instanceof Long micros) return Math.max(0, micros / 1000); throw new IOException("Invalid MPRIS microsecond property"); }
    @Override public void close() throws IOException { connection.close(); }
}
