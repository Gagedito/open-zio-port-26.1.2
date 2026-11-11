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
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.MultiPlayerGameModeDestroyBlockEvent;
import dev.zio.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.zio.client.utils.network.PacketSender;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;

import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

public class NoGlitchBlocksModule extends Module {
   private final Setting<Boolean> placeSetting;
   public final Setting<Boolean> breakSetting;

   @EventHandler
   private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
      InteractionHand interactionHand = event.getHand();
      Item heldItem = ClientAccess.zioMinecraft.player.getItemInHand(interactionHand).getItem();
      if ((this.placeSetting.getValue()) && heldItem instanceof BlockItem blockItem) {
         BlockPos clickedPos = event.getHitResult().getBlockPos();
         Direction clickedFace = event.getHitResult().getDirection();
         BlockPos placementPos = clickedPos.relative(clickedFace);
         Block placedBlock = blockItem.getBlock();
         if (BlockInteractionUtils.canPlaceBlock(placementPos, placedBlock, false)) {
            InteractionHand packetHand = event.getHand();
            BlockHitResult packetHit = event.getHitResult();
            PacketSender.sendUseItemOn(packetHand, packetHit);
            LocalPlayer playerForSwing = ClientAccess.zioMinecraft.player;
            InteractionHand swingHand = event.getHand();
            playerForSwing.swing(swingHand);
            event.cancel();
         }
      }
   }

   @EventHandler
   private void onMultiPlayerGameModeDestroyBlockEvent(MultiPlayerGameModeDestroyBlockEvent event) {
      if (this.breakSetting.getValue()) {
         event.cancel();
      }
   }

   public NoGlitchBlocksModule() {
      super("NoGlitchBlocks", "Makes sure there are no ghost blocks as you interact with them. \n"
         + String.valueOf(ChatFormatting.RED) + "Not recommended on 2b2t/Grim servers.", ModuleCategory.PLAYER, new String[0]);
      Boolean defaultPlace = true;
      BooleanSetting placeOption = new BooleanSetting("Place", defaultPlace);
      this.placeSetting = this.add(placeOption);
      Boolean defaultBreak = true;
      BooleanSetting breakOption = new BooleanSetting("Break", defaultBreak);
      this.breakSetting = this.add(breakOption);
   }

}
