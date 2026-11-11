/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import dev.zio.client.managers.ConfigurationManager;
import dev.zio.client.utils.FileUtils;
import dev.zio.client.ZioClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;

public final class BundledResourceInstaller {
   private final HashMap<String, String> resourcePaths = new HashMap<>();

   public void installMissingResources() {
      this.resourcePaths.forEach(this::installMissingResource);
   }

   public BundledResourceInstaller() {
      this.resourcePaths.put("textures/overlay.png", "assets/zio/textures/overlay.png");
      this.resourcePaths.put("textures/skin_protect.png", "assets/zio/textures/skin_protect.png");
      this.resourcePaths.put("textures/shine.png", "assets/zio/textures/shine.png");
      this.resourcePaths.put("shaders/sky.frag", "assets/zio/shaders/sky/sky.frag");
      this.resourcePaths.put("shaders/custom.frag", "assets/zio/shaders/custom/example.frag");
   }

   private void installMissingResource(String relativeDestination, String bundledResource) {
      Path destinationPath = ConfigurationManager.ROOT_DIRECTORY.resolve(relativeDestination);
      File destinationFile = destinationPath.toFile();
      if (!destinationFile.exists()) {
         try (InputStream input = ZioClient.class.getResourceAsStream("/" + bundledResource)) {
            if (input == null) {
               return;
            }
            byte[] resourceBytes = input.readAllBytes();
            FileUtils.writeBytes(destinationPath, resourceBytes);
         } catch (IOException ioFailure) {
            ioFailure.printStackTrace();
         }
      }
   }
}
