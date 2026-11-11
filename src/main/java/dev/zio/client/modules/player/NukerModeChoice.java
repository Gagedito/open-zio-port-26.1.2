/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.settings.NamedEntry;

import java.util.Iterator;
import java.util.Set;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;

enum NukerModeChoice implements NamedEntry {
   WHITELIST("WhiteList") {
      @Override
      public boolean matchesBlock(
         BlockPos blockPos, Block block, NukerModule nukerModule
      ) {
         return ((Set<?>)nukerModule.blocksSetting.getValue()).contains(block);
      }
   },
   BLACKLIST("BlackList") {
      @Override
      public boolean matchesBlock(
         BlockPos blockPos, Block block, NukerModule nukerModule
      ) {
         NukerModeChoice whitelistMode = WHITELIST;
         return !whitelistMode.matchesBlock(blockPos, block, nukerModule);
      }
   },
   SHULKERS("Shulkers") {
      @Override
      public boolean matchesBlock(
         BlockPos blockPos, Block block, NukerModule nukerModule
      ) {
         if (!(block instanceof ShulkerBoxBlock)) {
            return false;
         }

         if (nukerModule.playerProximitySetting.getValue()) {
            Iterator<AbstractClientPlayer> players = ClientAccess.zioMinecraft.level.players().iterator();

            while (players.hasNext()) {
               AbstractClientPlayer player = players.next();
               if (!ClientAccess.isLocalPlayer(player)) {
                  SocialRegistry friendManager = ClientContext.socialRegistry;
                  if (!friendManager.isFriendPlayer(player)) {
                     Vec3 eyePositionSnapshot = player.getEyePosition();
                     Vec3 blockCenter = blockPos.getCenter();
                     if (!(eyePositionSnapshot.distanceTo(blockCenter) > 6.5)) {
                        return true;
                     }
                  }
               }
            }

            return false;
         } else {
            return true;
         }
      }
   },
   BEDS("Bed") {
      @Override
      public boolean matchesBlock(
         BlockPos blockPos, Block block, NukerModule nukerModule
      ) {
         if (block instanceof BedBlock) {
            BlockState stateSnapshot = ClientAccess.zioMinecraft.level.getBlockState(blockPos);
            EnumProperty partProperty = BedBlock.PART;
            if (stateSnapshot.getValue(partProperty) == BedPart.HEAD) {
               return true;
            }
         }

         return false;
      }
   },
   CLICK("Click") {
      @Override
      public boolean matchesBlock(
         BlockPos blockPos, Block block, NukerModule nukerModule
      ) {
         return nukerModule.getClickedBlock() == block;
      }
   };

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   NukerModeChoice(String displayName) {
      this.displayName = displayName;
   }

   public abstract boolean matchesBlock(
      BlockPos blockPos, Block block, NukerModule nukerModule
   );
}
