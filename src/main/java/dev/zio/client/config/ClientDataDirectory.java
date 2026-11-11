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
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public final class ClientDataDirectory {
    private ClientDataDirectory() {
    }

    public static Path migrateLegacy(Path destination) {
        Path legacy = destination.resolveSibling("mio-fabric");
        Path staging = null;
        try {
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(destination)) {
                    throw new IOException("Zio data path is not a directory: " + destination);
                }
                return destination;
            }
            if (!Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)) {
                return destination;
            }
            if (!Files.isDirectory(legacy)) {
                throw new IOException("Legacy data path is not a directory: " + legacy);
            }
            staging = Files.createTempDirectory(destination.toAbsolutePath().getParent(), ".zio-migration-");
            Path copied = staging;
            Files.walkFileTree(legacy, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    if (!directory.equals(legacy)) {
                        Files.createDirectory(copied.resolve(legacy.relativize(directory)));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {

                    Files.copy(file, copied.resolve(legacy.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                    return FileVisitResult.CONTINUE;
                }
            });

            Files.move(staging, destination);
            return destination;
        } catch (IOException failure) {
            if (staging != null) {
                try {
                    removeStaging(staging);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw new UncheckedIOException("Unable to copy legacy client data to " + destination, failure);
        }
    }

    private static void removeStaging(Path staging) throws IOException {
        Files.walkFileTree(staging, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
