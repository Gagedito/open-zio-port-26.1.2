/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import dev.zio.client.ZioClient;
import dev.zio.client.managers.ConfigurationManager;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

public final class BuiltinResourcePack implements PackResources {
   PackLocationInfo location;

   public BuiltinResourcePack() {
      this.location = new PackLocationInfo("zio", Component.literal(ZioClient.NAME), PackSource.BUILT_IN, Optional.empty());
   }

   public static Optional<Resource> resolveResource(Identifier id, Optional<Resource> original) {
      if (id.getNamespace().equals("zio-mount")) {
         Path path = ConfigurationManager.ROOT_DIRECTORY.resolve(id.getPath());
         if (Files.isRegularFile(path)) {
            return Optional.of(new Resource(new BuiltinResourcePack(), () -> Files.newInputStream(path)));
         }
      } else if (original.isEmpty()) {
         String path = id.getPath();
         boolean builtinShader = path.contains("/blur_mask.") || path.contains("/bloom.");
         if (id.getNamespace().equals("zio") || builtinShader) {
            String namespace = builtinShader ? "minecraft" : "zio";
            URL resource = ZioClient.class.getResource("/assets/%s/%s".formatted(namespace, path));
            if (resource != null) {
               return Optional.of(new Resource(new BuiltinResourcePack(), resource::openStream));
            }
         }
      }

      return original;
   }

   @Override
   public void close() {
   }

   @Override
   public Set getNamespaces(PackType packType) {
      return null;
   }

   @Override
   public void listResources(PackType packType, String namespace, String path, ResourceOutput output) {
   }

   @Nullable
   @Override
   public Object getMetadataSection(MetadataSectionType metadataType) {
      return null;
   }

   @Nullable
   @Override
   public IoSupplier getResource(PackType packType, Identifier location) {
      return null;
   }

   @Override
   public PackLocationInfo location() {
      return this.location;
   }

   @Nullable
   @Override
   public IoSupplier getRootResource(String... path) {
      return null;
   }
}
