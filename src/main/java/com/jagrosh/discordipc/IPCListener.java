package com.jagrosh.discordipc;

import com.google.gson.JsonObject;
import com.jagrosh.discordipc.entities.Packet;
import com.jagrosh.discordipc.entities.User;

public interface IPCListener {
   void onPacketSent(IPCClient client, Packet packet);

   void onPacketReceived(IPCClient client, Packet packet);

   void onActivityJoin(IPCClient client, String secret);

   void onActivitySpectate(IPCClient client, String secret);

   void onActivityJoinRequest(IPCClient client, String secret, User user);

   void onReady(IPCClient client);

   void onClose(IPCClient client, JsonObject response);

   void onDisconnect(IPCClient client, Throwable cause);
}
