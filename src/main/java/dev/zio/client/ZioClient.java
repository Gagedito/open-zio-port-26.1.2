/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client;

import dev.zio.client.bootstrap.ClientInitializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZioClient implements ModInitializer, ClientModInitializer {
   public static final String MOD_ID = "zio";
   public static final String NAME = "Zio";
   public static final String PROJECT_URL = "https://github.com/Cypphi/open-zio";
   public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
      .orElseThrow(() -> new IllegalStateException("Zio mod metadata is missing"))
      .getMetadata().getVersion().getFriendlyString();
   public static final String USER_AGENT = NAME + "/" + VERSION;
   public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

   private static final ClientInitializer INITIALIZER = new ClientInitializer();
   public static ClientInitializer getInitializer() {
      return INITIALIZER;
   }

   @Override
   public void onInitialize() {
      // MC 26.1.2 (boot guard): el modulo Fonts usa AWT (BufferedImage/Font
      // metrics) para construir su atlas de texto; AWT en Linux intenta abrir
      // el X server al primer getLocalGraphicsEnvironment y si una hebra
      // worker tropieza antes de que exista display accesible, la clase
      // GraphicsEnvironment queda inutilizable para todo el proceso. Zio solo
      // dibuja en buffers offscreen (una app de juego nunca crea ventanas
      // AWT), asi que forzamos modo headless desde el entrypoint para que el
      // AWT inicialice sin X11.
      System.setProperty("java.awt.headless", "true");
      INITIALIZER.initialize();
   }

   @Override
   public void onInitializeClient() {
      INITIALIZER.initializeClient();
   }
}
