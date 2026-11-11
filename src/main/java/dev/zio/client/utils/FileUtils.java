/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils;

import dev.zio.client.render.text.ReverseStringFormatter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class FileUtils {
   private static final String suffixPathPattern;

   static {
      suffixPathPattern = "\u0001\u0001";
   }

   public static byte[] readBytes(Path path) throws IOException {
      FileInputStream input = null;

      byte[] bytes;
      try {
         input = new FileInputStream(path.toFile());
         bytes = input.readAllBytes();
      } finally {
         if (input != null) {
            try {
               input.close();
            } catch (Exception ignoredCloseFailure) {
            }
         }
      }

      return bytes;
   }

   public static Path findExistingWithSuffixes(Path path, String... suffixes) {
      File baseFile = path.toFile();
      if (baseFile.exists()) {
         return path;
      }

      for (String suffix : suffixes) {
         String pathText = String.valueOf(path);
         String candidatePathText = new ReverseStringFormatter()
            .prepend(suffix)
            .prepend(pathText)
            .format(suffixPathPattern);
         Path candidatePath = Path.of(candidatePathText, new String[0]);
         File candidateFile = candidatePath.toFile();
         if (candidateFile.exists()) {
            return candidatePath;
         }
      }

      return path;
   }

   public static void writeBytes(Path path, byte[] bytes) throws IOException {
      try (FileOutputStream output = new FileOutputStream(path.toFile())) {
         output.write(bytes);
      }
   }

   public static void writeUtf8(Path path, String text) throws IOException {
      Charset charset = StandardCharsets.UTF_8;
      writeBytes(path, text.getBytes(charset));
   }

   public static String readUtf8(Path path) throws IOException {
      return new String(readBytes(path), StandardCharsets.UTF_8);
   }
}
