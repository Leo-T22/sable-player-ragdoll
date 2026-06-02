package dev.leo.sableplayerragdoll.neoforge.client;

import dev.leo.sableplayerragdoll.neoforge.network.RagdollInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RagdollInputClient {
   private static float lastStrafe;
   private static float lastForward;
   private static int keepAliveTicks;

   private RagdollInputClient() {
   }

   public static void init() {
      NeoForge.EVENT_BUS.addListener(RagdollInputClient::onClientTick);
   }

   private static void onClientTick(Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null || !player.isPassenger()) {
         sendIfChanged(0.0F, 0.0F, true);
         return;
      }
      float strafe = axis(player.input.left, player.input.right);
      float forward = axis(player.input.down, player.input.up);
      sendIfChanged(strafe, forward, false);
   }

   private static void sendIfChanged(float strafe, float forward, boolean forceZero) {
      boolean changed = strafe != lastStrafe || forward != lastForward;
      if (!changed && !forceZero && ++keepAliveTicks < 5) return;
      if (!changed && forceZero && lastStrafe == 0.0F && lastForward == 0.0F) return;
      keepAliveTicks = 0;
      lastStrafe = strafe;
      lastForward = forward;
      PacketDistributor.sendToServer(new RagdollInputPacket(strafe, forward), new CustomPacketPayload[0]);
   }

   private static float axis(boolean negative, boolean positive) {
      if (positive == negative) return 0.0F;
      return positive ? 1.0F : -1.0F;
   }
}
