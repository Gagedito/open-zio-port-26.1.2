package com.jagrosh.discordipc.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

public class WinRegistry {
   public static final int HKEY_CURRENT_USER = -2147483647;
   public static final int HKEY_LOCAL_MACHINE = -2147483646;
   public static final int REG_SUCCESS = 0;
   private static final int KEY_ALL_ACCESS = 983103;
   private static final int KEY_READ = 131097;
   private static final Preferences userRoot = Preferences.userRoot();
   private static final Preferences systemRoot = Preferences.systemRoot();
   private static final Class userClass = userRoot.getClass();
   private static final Method regOpenKey;
   private static final Method regCloseKey;
   private static final Method regQueryValueEx;
   private static final Method regEnumValue;
   private static final Method regQueryInfoKey;
   private static final Method regEnumKeyEx;
   private static final Method regCreateKeyEx;
   private static final Method regSetValueEx;
   private static final Method regDeleteKey;
   private static final Method regDeleteValue;
   private static final float javaSpec;

   private WinRegistry() {
   }

   public static String readString(int hkey, String key, String valueName) {
      if (hkey == -2147483646) {
         return readString(systemRoot, hkey, key, valueName);
      } else if (hkey == -2147483647) {
         return readString(userRoot, hkey, key, valueName);
      } else {
         throw new IllegalArgumentException("hkey=" + hkey);
      }
   }

   public static String readString(long hkey, String key, String valueName) {
      if (hkey == -2147483646L) {
         return readString(systemRoot, hkey, key, valueName);
      } else if (hkey == -2147483647L) {
         return readString(userRoot, hkey, key, valueName);
      } else {
         throw new IllegalArgumentException("hkey=" + hkey);
      }
   }

   public static Map readStringValues(int hkey, String key) {
      if (hkey == -2147483646) {
         return readStringValues(systemRoot, hkey, key);
      } else if (hkey == -2147483647) {
         return readStringValues(userRoot, hkey, key);
      } else {
         throw new IllegalArgumentException("hkey=" + hkey);
      }
   }

   public static List readStringSubKeys(int hkey, String key) {
      if (hkey == -2147483646) {
         return readStringSubKeys(systemRoot, hkey, key);
      } else if (hkey == -2147483647) {
         return readStringSubKeys(userRoot, hkey, key);
      } else {
         throw new IllegalArgumentException("hkey=" + hkey);
      }
   }

   public static void createKey(int hkey, String key) {
      int[] result;
      if (hkey == -2147483646) {
         result = createKey(systemRoot, hkey, key);
         invoke(systemRoot, regCloseKey, result[0]);
      } else {
         if (hkey != -2147483647) {
            throw new IllegalArgumentException("hkey=" + hkey);
         }

         result = createKey(userRoot, hkey, key);
         invoke(userRoot, regCloseKey, result[0]);
      }

      if (result[1] != 0) {
         throw new IllegalArgumentException("rc=" + result[1] + "  key=" + key);
      }
   }

   public static void createKey(long hkey, String key) {
      long[] result;
      if (hkey == -2147483646L) {
         result = createKey(systemRoot, hkey, key);
         invoke(systemRoot, regCloseKey, result[0]);
      } else {
         if (hkey != -2147483647L) {
            throw new IllegalArgumentException("hkey=" + hkey);
         }

         result = createKey(userRoot, hkey, key);
         invoke(userRoot, regCloseKey, result[0]);
      }

      if (result[1] != 0L) {
         throw new IllegalArgumentException("rc=" + result[1] + "  key=" + key);
      }
   }

   public static void writeStringValue(int hkey, String key, String valueName, String value) {
      if (hkey == -2147483646) {
         writeStringValue(systemRoot, hkey, key, valueName, value);
      } else {
         if (hkey != -2147483647) {
            throw new IllegalArgumentException("hkey=" + hkey);
         }

         writeStringValue(userRoot, hkey, key, valueName, value);
      }
   }

   public static void writeStringValue(long hkey, String key, String valueName, String value) {
      if (hkey == -2147483646L) {
         writeStringValue(systemRoot, hkey, key, valueName, value);
      } else {
         if (hkey != -2147483647L) {
            throw new IllegalArgumentException("hkey=" + hkey);
         }

         writeStringValue(userRoot, hkey, key, valueName, value);
      }
   }

   public static void deleteKey(int hkey, String key) {
      int result = -1;
      if (hkey == -2147483646) {
         result = deleteKey(systemRoot, hkey, key);
      } else if (hkey == -2147483647) {
         result = deleteKey(userRoot, hkey, key);
      }

      if (result != 0) {
         throw new IllegalArgumentException("rc=" + result + "  key=" + key);
      }
   }

   public static void deleteValue(int hkey, String key, String valueName) {
      int result = -1;
      if (hkey == -2147483646) {
         result = deleteValue(systemRoot, hkey, key, valueName);
      } else if (hkey == -2147483647) {
         result = deleteValue(userRoot, hkey, key, valueName);
      }

      if (result != 0) {
         throw new IllegalArgumentException("rc=" + result + "  key=" + key + "  value=" + valueName);
      }
   }

   private static int deleteValue(Preferences root, int hkey, String key, String valueName) {
      int[] openedKey = invoke(root, regOpenKey, hkey, toCstr(key), 983103);
      if (openedKey[1] != 0) {
         return openedKey[1];
      }

      int result = invoke(root, regDeleteValue, openedKey[0], toCstr(valueName));
      invoke(root, regCloseKey, openedKey[0]);
      return result;
   }

   private static int deleteKey(Preferences root, int hkey, String key) {
      return invoke(root, regDeleteKey, hkey, toCstr(key));
   }

   private static String readString(Preferences root, int hkey, String key, String valueName) {
      int[] openedKey = invoke(root, regOpenKey, hkey, toCstr(key), 131097);
      if (openedKey[1] != 0) {
         return null;
      }

      byte[] value = invoke(root, regQueryValueEx, openedKey[0], toCstr(valueName));
      invoke(root, regCloseKey, openedKey[0]);
      return value != null ? new String(value).trim() : null;
   }

   private static String readString(Preferences root, long hkey, String key, String valueName) {
      long[] openedKey = invoke(root, regOpenKey, hkey, toCstr(key), 131097);
      if (openedKey[1] != 0L) {
         return null;
      }

      byte[] value = invoke(root, regQueryValueEx, openedKey[0], toCstr(valueName));
      invoke(root, regCloseKey, openedKey[0]);
      return value != null ? new String(value).trim() : null;
   }

   private static Map readStringValues(Preferences root, int hkey, String key) {
      HashMap values = new HashMap();
      int[] openedKey = invoke(root, regOpenKey, hkey, toCstr(key), 131097);
      if (openedKey[1] != 0) {
         return null;
      }

      int[] info = invoke(root, regQueryInfoKey, openedKey[0]);
      int count = info[0];
      int maxNameLength = info[3];

      for (int index = 0; index < count; index++) {
         byte[] name = invoke(root, regEnumValue, openedKey[0], index, maxNameLength + 1);
         String value = readString(hkey, key, new String(name));
         values.put(new String(name).trim(), value);
      }

      invoke(root, regCloseKey, openedKey[0]);
      return values;
   }

   private static List readStringSubKeys(Preferences root, int hkey, String key) {
      ArrayList subkeys = new ArrayList();
      int[] openedKey = invoke(root, regOpenKey, hkey, toCstr(key), 131097);
      if (openedKey[1] != 0) {
         return null;
      }

      int[] info = invoke(root, regQueryInfoKey, openedKey[0]);
      int count = info[0];
      int maxNameLength = info[3];

      for (int index = 0; index < count; index++) {
         byte[] name = invoke(root, regEnumKeyEx, openedKey[0], index, maxNameLength + 1);
         subkeys.add(new String(name).trim());
      }

      invoke(root, regCloseKey, openedKey[0]);
      return subkeys;
   }

   private static int[] createKey(Preferences root, int hkey, String key) {
      return invoke(root, regCreateKeyEx, hkey, toCstr(key));
   }

   private static long[] createKey(Preferences root, long hkey, String key) {
      return invoke(root, regCreateKeyEx, hkey, toCstr(key));
   }

   private static void writeStringValue(Preferences root, int hkey, String key, String valueName, String value) {
      int[] openedKey = invoke(root, regOpenKey, hkey, toCstr(key), 983103);
      invoke(root, regSetValueEx, openedKey[0], toCstr(valueName), toCstr(value));
      invoke(root, regCloseKey, openedKey[0]);
   }

   private static void writeStringValue(Preferences root, long hkey, String key, String valueName, String value) {
      long[] openedKey = invoke(root, regOpenKey, hkey, toCstr(key), 983103);
      invoke(root, regSetValueEx, openedKey[0], toCstr(valueName), toCstr(value));
      invoke(root, regCloseKey, openedKey[0]);
   }


   private static Method getDeclaredMethod(Class<?> type, String name, Class<?>... params) throws Exception {
      for (Class<?> current = type; current != null; current = current.getSuperclass()) {
         try {
            Method found = current.getDeclaredMethod(name, params);
            found.setAccessible(true);
            return found;
         } catch (NoSuchMethodException ignored) {
         }
      }
      throw new NoSuchMethodException(name);
   }

   private static <T> T invoke(Preferences root, Method method, Object... arguments) {
      try {
         @SuppressWarnings("unchecked")
         T result = (T)method.invoke(root, arguments);
         return result;
      } catch (Exception failure) {
         throw new RuntimeException("Registry invocation failed: " + method.getName(), failure);
      }
   }

   private static byte[] toCstr(String text) {
      byte[] bytes = new byte[text.length() + 1];

      for (int index = 0; index < text.length(); index++) {
         bytes[index] = (byte)text.charAt(index);
      }

      bytes[text.length()] = 0;
      return bytes;
   }

   static {
      try {
         javaSpec = Float.parseFloat(System.getProperty("java.specification.version"));
         regOpenKey = getDeclaredMethod(userClass, "WindowsRegOpenKey", javaSpec >= 11.0F ? long.class : int.class, byte[].class, int.class);
         regCloseKey = getDeclaredMethod(userClass, "WindowsRegCloseKey", javaSpec >= 11.0F ? long.class : int.class);
         regQueryValueEx = getDeclaredMethod(userClass, "WindowsRegQueryValueEx", javaSpec >= 11.0F ? long.class : int.class, byte[].class);
         regEnumValue = getDeclaredMethod(userClass, "WindowsRegEnumValue", javaSpec >= 11.0F ? long.class : int.class, int.class, int.class);
         regQueryInfoKey = getDeclaredMethod(userClass, "WindowsRegQueryInfoKey1", javaSpec >= 11.0F ? long.class : int.class);
         regEnumKeyEx = getDeclaredMethod(userClass, "WindowsRegEnumKeyEx", javaSpec >= 11.0F ? long.class : int.class, int.class, int.class);
         regCreateKeyEx = getDeclaredMethod(userClass, "WindowsRegCreateKeyEx", javaSpec >= 11.0F ? long.class : int.class, byte[].class);
         regSetValueEx = getDeclaredMethod(userClass, "WindowsRegSetValueEx", javaSpec >= 11.0F ? long.class : int.class, byte[].class, byte[].class);
         regDeleteValue = getDeclaredMethod(userClass, "WindowsRegDeleteValue", javaSpec >= 11.0F ? long.class : int.class, byte[].class);
         regDeleteKey = getDeclaredMethod(userClass, "WindowsRegDeleteKey", javaSpec >= 11.0F ? long.class : int.class, byte[].class);
      } catch (Exception failure) {
         throw new RuntimeException("Unable to setup registry data", failure);
      }
   }
}
