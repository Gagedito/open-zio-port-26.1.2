/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class OfflineSoundAssets {
    private static final String PREFIX = "/assets/zio/offline/killstreaks/";

    private OfflineSoundAssets() {
    }

    public static void installKillstreaks(Path path) {
        Path directory = path.toAbsolutePath().normalize();
        if (Files.isDirectory(directory)) {
            return;
        }
        try {
            String manifest;
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Sound-pack path is occupied by a file: " + directory);
            }
            try (InputStream inputStream = OfflineSoundAssets.class.getResourceAsStream(PREFIX + "manifest.txt")) {
                if (inputStream == null) {
                    throw new IOException("Preserved sound-pack manifest is missing");
                }
                manifest = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String relativePath : manifest.lines().toList()) {
                if (relativePath.isBlank()) {
                    continue;
                }
                Path destination = directory.resolve(relativePath).normalize();
                if (!destination.startsWith(directory) || !relativePath.endsWith(".ogg")) {
                    throw new IOException("Invalid preserved sound path");
                }
                InputStream inputStream = OfflineSoundAssets.class.getResourceAsStream(PREFIX + relativePath);

                if (inputStream == null) {
                    continue;
                }
                try {
                    Files.createDirectories(destination.getParent());
                    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    Files.copy(inputStream, destination);
                } finally {
                    inputStream.close();
                }
            }
        } catch (IOException | SecurityException exception) {
            System.getLogger("Zio Offline Sounds").log(System.Logger.Level.WARNING, "Optional killstreak sounds are unavailable: " + exception.getMessage());
        }
    }
}

