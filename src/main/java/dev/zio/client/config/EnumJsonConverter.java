/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.config;

import dev.zio.client.utils.ExperimentalFeatures;

import dev.zio.client.settings.NamedEntry;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class EnumJsonConverter<E extends Enum<E>> {
   private final Class<E> enumClass;

   public EnumJsonConverter(Class<E> enumClass) {
      this.enumClass = enumClass;
   }

   public JsonElement toJson(Enum value) {
      if (value instanceof NamedEntry namedValue) {
         return new JsonPrimitive(namedValue.getName());
      } else {
         throw new IllegalArgumentException();
      }
   }

   public static int findSelectableIndex(Enum value) {
      int index = 0;

      while (true) {
         Class classForLength = value.getDeclaringClass();
         if (index >= ((Enum[])classForLength.getEnumConstants()).length) {
            return -1;
         }

         Class classForRead = value.getDeclaringClass();
         Enum candidate = ((Enum[])classForRead.getEnumConstants())[index];

         if (!ExperimentalFeatures.isEnumConstantHidden(candidate)) {
            String candidateName = candidate.name();
            String valueName = value.name();
            if (candidateName.equalsIgnoreCase(valueName)) {
               return index;
            }
         }

         index++;
      }
   }

   public E fromJson(JsonElement json) {
      for (E candidate : this.enumClass.getEnumConstants()) {
         if (!(candidate instanceof NamedEntry namedCandidate)) {
            throw new IllegalArgumentException();
         }

         if (!ExperimentalFeatures.isEnumConstantHidden(candidate)) {
            String jsonName = json.getAsString();
            String candidateName = namedCandidate.getName();
            if (jsonName.equalsIgnoreCase(candidateName)) {
               return candidate;
            }
         }
      }

      return this.enumClass.getEnumConstants()[0];
   }

   public static Enum nextValue(Enum value) {
      int currentIndex = findSelectableIndex(value);
      int index = 0;

      while (true) {
         Class classForLength = value.getDeclaringClass();
         if (index >= classForLength.getEnumConstants().length) {
            Class classForFallback = value.getDeclaringClass();
            return (Enum)classForFallback.getEnumConstants()[0];
         }

         Class classForRead = value.getDeclaringClass();
         Enum candidate = (Enum)classForRead.getEnumConstants()[index];

         if (!ExperimentalFeatures.isEnumConstantHidden(candidate) && index > currentIndex) {
            return candidate;
         }

         index++;
      }
   }

   public static String getName(Enum value) {
      if (value instanceof NamedEntry namedValue) {
         return namedValue.getName();
      } else {
         throw new IllegalArgumentException();
      }
   }

}
