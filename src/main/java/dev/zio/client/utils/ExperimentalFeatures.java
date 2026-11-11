/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils;

import dev.zio.client.modules.Module;
import dev.zio.client.settings.Setting;

import java.lang.reflect.Field;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public class ExperimentalFeatures {
   private static boolean experimentalFeaturesAllowed;

   public static boolean isExperimentalDisabled() {
      return !experimentalFeaturesAllowed;
   }

   static {
      experimentalFeaturesAllowed = false;

      FabricLoader loader = FabricLoader.getInstance();

      for (String launchArgument : loader.getLaunchArguments(true)) {
         if ("--zio-allow-experimental".equals(launchArgument)) {
            experimentalFeaturesAllowed = true;
            break;
         }
      }

      Properties propertiesForPresenceCheck = System.getProperties();
      if (propertiesForPresenceCheck.get("zio.allowExperimental") != null) {
         String expectedPropertyValue = "true";
         Properties propertiesForValueCheck = System.getProperties();
         String propertyValue = (String)propertiesForValueCheck.get("zio.allowExperimental");
         if (expectedPropertyValue.equalsIgnoreCase(propertyValue)) {
            experimentalFeaturesAllowed = true;
         }
      }
   }

   public static boolean isFieldHidden(Field field) {
      return isExperimentalDisabled() && field.isAnnotationPresent(Experimental.class);
   }

   public static boolean isEnumConstantHidden(Enum enumConstant) {
      try {
         if (!isExperimentalDisabled()) {
            return false;
         }
         Class declaringClass = enumConstant.getDeclaringClass();
         String constantName = enumConstant.name();
         return declaringClass.getDeclaredField(constantName).isAnnotationPresent(Experimental.class);
      } catch (NoSuchFieldException missingField) {
         return false;
      }
   }

   public static boolean isClassHidden(Class type) {
      return isExperimentalDisabled() && type.isAnnotationPresent(Experimental.class);
   }

   public static void unregisterHiddenSettings(Module module) {
      Class moduleType = module.getClass();

      for (Field field : moduleType.getDeclaredFields()) {
         if (!isFieldHidden(field)) {
            continue;
         }
         field.setAccessible(true);
         try {
            Setting<?> setting = (Setting<?>)field.get(module);
            module.unregister(setting);
         } catch (IllegalAccessException accessFailure) {
            accessFailure.printStackTrace();
         }
      }
   }
}
