/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.modules.exploit.PingSpoofModule;
import dev.zio.client.modules.misc.NoPacketKickModule;
import dev.zio.client.utils.network.PingSpoofDelayHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
   private static NoPacketKickModule noPacketKickModule = ClientContext.getModule(NoPacketKickModule.class);
   private static final PingSpoofModule pingSpoofModule = ClientContext.getModule(PingSpoofModule.class);
   @Shadow
   private Channel channel;
   @Shadow
   @Final
   private PacketFlow receiving;

   @Inject(method = "channelActive", at = @At("TAIL"))
   private void installPingSpoofDelayHandler(ChannelHandlerContext context, CallbackInfo callback) {
      if (this.receiving != PacketFlow.CLIENTBOUND) {
         return;
      }
      ChannelPipeline pipeline = context.pipeline();
      if (pipeline.get(PingSpoofDelayHandler.NAME) == null) {
         pipeline.addFirst(PingSpoofDelayHandler.NAME, new PingSpoofDelayHandler(
            pingSpoofModule, () -> ((Connection)(Object)this).getPacketListener() instanceof ClientGamePacketListener
         ));
      }
   }

   @Inject(method = "channelRead0*", at = @At("HEAD"), cancellable = true)
   public void onPacketReceive(
      ChannelHandlerContext context, Packet packet, CallbackInfo callback
   ) {
      if (this.channel.isOpen() && packet != null) {
         try {
            PacketEvent.Receive event = new PacketEvent.Receive(packet);
            ClientAccess.eventBus
               .post(event);
            if (event.isCancelled()) {
               callback.cancel();
            }
         } catch (Exception ignoredException) {
         }
      }
   }

   @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
   private void onPacketSend(
      Packet packet, ChannelFutureListener listener, boolean flush, CallbackInfo callback
   ) {
      if (this.receiving == PacketFlow.CLIENTBOUND) {
         try {
            PacketEvent.Send event = new PacketEvent.Send(packet);
            ClientAccess.eventBus
               .post(event);
            if (event.isCancelled()) {
               callback.cancel();
            }
         } catch (Exception ignoredException) {
         }
      }
   }

   @Inject(method = "doSendPacket", at = @At("TAIL"))
   private void onPacketPostSend(
      Packet packet, ChannelFutureListener listener, boolean flush, CallbackInfo callback
   ) {
      if (this.receiving == PacketFlow.CLIENTBOUND) {
         try {
            PacketEvent.PostSend event = new PacketEvent.PostSend(packet);
            ClientAccess.eventBus
               .post(event);
         } catch (Exception ignoredException) {
         }
      }
   }

   @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
   private void onConnectionException(
      ChannelHandlerContext context, Throwable failure, CallbackInfo callback
   ) {
      if (noPacketKickModule.isToggled()) {
         noPacketKickModule.logInterceptedException(failure);
         callback.cancel();
      }
   }
}
