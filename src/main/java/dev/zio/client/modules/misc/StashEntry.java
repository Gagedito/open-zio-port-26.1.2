/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.settings.NamedEntry;

import com.google.gson.annotations.SerializedName;

public class StashEntry implements NamedEntry {
   @SerializedName("id")
   private final String id;
   @SerializedName("x")
   private final int x;
   @SerializedName("z")
   private final int z;
   @SerializedName("dimension")
   private final String dimension;
   @SerializedName("server")
   private final String server;

   public StashEntry(String id, int x, int z, String dimension, String server) {
      this.id = id;
      this.x = x;
      this.z = z;
      this.dimension = dimension;
      this.server = server;
   }

   public String formatCoordinates() {
      int xCoordinate = this.x;
      int zCoordinate = this.z;
      return xCoordinate + ", " + zCoordinate + ".";
   }

   public double getX() {
      return this.x;
   }

   public double getZ() {
      return this.z;
   }

   public String getDimension() {
      return this.dimension;
   }

   public String getServer() {
      return this.server;
   }

   @Override
   public String getName() {
      return this.id;
   }

}
