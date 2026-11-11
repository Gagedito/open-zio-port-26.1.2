/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.ShaderRegistry;
import dev.zio.client.managers.SoundManager;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.managers.ConfigurationManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.zio.mixin.TextureManagerAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
public final class ReloadCommand extends Command {
   private static final byte[] PNG_SIGNATURE;
   private CompletableFuture reloadFuture;

   public ReloadCommand() {
      super("reload");
      this.reloadFuture = CompletableFuture.completedFuture(null);
      this.setAliases(new String[]{"rl"});
   }

   static {

      byte[] signature = new byte[8];
      signature[0] = (byte)(-119);
      signature[1] = (byte)(80);
      signature[(2)] = (byte)(78);
      signature[(3)] = (byte)(71);
      signature[(4)] = (byte)(13);
      signature[(5)] = (byte)(10);
      signature[(6)] = (byte)(26);
      signature[(7)] = (byte)(10);
      PNG_SIGNATURE = signature;
   }

   @Override
   public void exec(LiteralArgumentBuilder commandBuilder) {
      commandBuilder.executes(
         (com.mojang.brigadier.Command<Object>)commandContext -> {
            CompletableFuture previousReload = this.reloadFuture;
            if (!previousReload.isDone()) {
               return 0;
            }

            MutableComponent reloadingMessage = Component.literal("Reloading Zio resources...");
            ReloadCommand command = this;
            ChatUtils.printChat(
               reloadingMessage, ChatUtils.objectSignature(command)
            );
            this.reloadFuture = CompletableFuture.runAsync(
               (Runnable)this::reloadResources,
               ClientAccess.executorService
            );
            this.reloadFuture.whenComplete(

               (BiConsumer<Void, Throwable>)(ignoredResult, ignoredFailure) -> {

                  MutableComponent completionMessage = Component.literal("Reload complete!").withStyle((java.util.function.UnaryOperator<Style>)(style -> {
                     ChatFormatting format = ChatFormatting.GREEN;
                     return style.applyFormat(format);
                  }));
                  ReloadCommand completedCommand = this;
                  ChatUtils.printChat(
                     completionMessage,
                     ChatUtils.objectSignature(completedCommand)
                  );
               }
            );
            return 1;
         }
      );
   }

   private void reloadResources() {
      ClientAccess.zioMinecraft.execute((Runnable)() -> {
         ShaderRegistry queuedShaderRegistry = ClientContext.shaderRegistry;
         queuedShaderRegistry.reloadAll();
      });
      ConfigurationManager directoryManager = ClientContext.configurationManager;
      directoryManager.prepareDirectories();
      SoundManager soundManager = ClientContext.soundManager;
      soundManager.reload();
      HashSet mountedTextures = new HashSet();
      Minecraft textureClient = ClientAccess.zioMinecraft;
      Iterator textures = ((TextureManagerAccessor)textureClient.getTextureManager()).getAllTextures().keySet().iterator();
      while (textures.hasNext()) {
         Identifier texture = (Identifier)textures.next();
         String textureNamespace = texture.getNamespace();
         if (textureNamespace.equals("zio-mount")) {
            mountedTextures.add(texture);
         }
      }

      textures = mountedTextures.iterator();
      while (textures.hasNext()) {
         Identifier mountedTexture = (Identifier)textures.next();
         Path dataDirectory = ConfigurationManager.ROOT_DIRECTORY;
         Path imagePath = dataDirectory.resolve(mountedTexture.getPath());
         try {

            validatePngSignature(imagePath);
         } catch (IOException | RuntimeException invalidImage) {
            String errorDetail = invalidImage.getMessage();
            String errorText = "Invalid Image! " + String.valueOf(errorDetail);
            MutableComponent errorMessage = Component.literal(errorText).withStyle((java.util.function.UnaryOperator<Style>)(style -> {
               ChatFormatting format = ChatFormatting.RED;
               return style.applyFormat(format);
            }));
            ChatUtils.printChat(
               errorMessage, ChatUtils.createSignature(-999)
            );
            continue;
         }

         ClientAccess.zioMinecraft.executeIfPossible((Runnable)() -> {
            Minecraft releaseClient = ClientAccess.zioMinecraft;
            TextureManager textureManager = releaseClient.getTextureManager();
            textureManager.release((mountedTexture));
            Minecraft registrationClient = ClientAccess.zioMinecraft;
            textureManager = registrationClient.getTextureManager();
            SimpleTexture replacementTexture = new SimpleTexture(mountedTexture);
            textureManager.registerAndLoad((mountedTexture), replacementTexture);
         });
      }
   }

   private static void validatePngSignature(Path imagePath) throws IOException {
      try (InputStream input = Files.newInputStream(imagePath, new OpenOption[0])) {
         int signatureLength = PNG_SIGNATURE.length;
         byte[] actualSignature = input.readNBytes(signatureLength);
         if (!Arrays.equals(actualSignature, PNG_SIGNATURE)) {
            throw new RuntimeException("File is not a png");
         }
      }
   }

}
