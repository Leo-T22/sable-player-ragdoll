package dev.leo.sableplayerragdoll.neoforge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RagdollNetworking {
   private RagdollNetworking() {
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar("sable_player_ragdoll");
      registrar.playToClient(RagdollUnlockedCameraPacket.TYPE, RagdollUnlockedCameraPacket.STREAM_CODEC, RagdollUnlockedCameraPacket::handle);
      registrar.playToClient(RagdollResetCameraPacket.TYPE, RagdollResetCameraPacket.STREAM_CODEC, RagdollResetCameraPacket::handle);
      registrar.playToServer(RagdollTriggerPacket.TYPE, RagdollTriggerPacket.STREAM_CODEC, RagdollTriggerPacket::handle);
      registrar.playToServer(RagdollTorsoGrabPacket.TYPE, RagdollTorsoGrabPacket.STREAM_CODEC, RagdollTorsoGrabPacket::handle);
      registrar.playToServer(RagdollInputPacket.TYPE, RagdollInputPacket.STREAM_CODEC, RagdollInputPacket::handle);
   }

   public static void notifyAutoSeated(ServerPlayer player) {
      PacketDistributor.sendToPlayer(player, new RagdollUnlockedCameraPacket(), new CustomPacketPayload[0]);
   }

   public static void notifyReleased(ServerPlayer player) {
      PacketDistributor.sendToPlayer(player, new RagdollResetCameraPacket(), new CustomPacketPayload[0]);
      player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), player.getDeltaMovement()));
   }
}
