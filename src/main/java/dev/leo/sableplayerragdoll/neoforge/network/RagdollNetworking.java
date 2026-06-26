package dev.leo.sableplayerragdoll.neoforge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RagdollNetworking {
   private static final ResourceLocation GRAB_SLOWDOWN_ID = ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "grab_slowdown");

   private RagdollNetworking() {
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar("sable_player_ragdoll").optional();
      registrar.playToClient(RagdollGrabSyncPacket.TYPE, RagdollGrabSyncPacket.STREAM_CODEC, RagdollGrabSyncPacket::handle);
      registrar.playToClient(RagdollRequestPosePacket.TYPE, RagdollRequestPosePacket.STREAM_CODEC, RagdollRequestPosePacket::handle);
      registrar.playToServer(RagdollTriggerPacket.TYPE, RagdollTriggerPacket.STREAM_CODEC, RagdollTriggerPacket::handle);
      registrar.playToServer(RagdollGrabPacket.TYPE, RagdollGrabPacket.STREAM_CODEC, RagdollGrabPacket::handle);
      registrar.playToServer(RagdollInputPacket.TYPE, RagdollInputPacket.STREAM_CODEC, RagdollInputPacket::handle);
      registrar.playToServer(RagdollArmInputPacket.TYPE, RagdollArmInputPacket.STREAM_CODEC, RagdollArmInputPacket::handle);
      registrar.playToServer(RagdollPoseResponsePacket.TYPE, RagdollPoseResponsePacket.STREAM_CODEC, RagdollPoseResponsePacket::handle);
   }

   public static void notifyReleased(ServerPlayer player) {
      player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), player.getDeltaMovement()));
   }

   public static void notifyRequestPose(ServerPlayer player, long requestId) {
      PacketDistributor.sendToPlayer(player, new RagdollRequestPosePacket(requestId), new CustomPacketPayload[0]);
   }

   public static void notifyGrabStarted(ServerPlayer player) {
      applySlowdown(player);
      PacketDistributor.sendToAllPlayers(new RagdollGrabSyncPacket(player.getUUID(), true), new CustomPacketPayload[0]);
   }

   public static void notifyGrabEnded(ServerPlayer player) {
      removeSlowdown(player);
      PacketDistributor.sendToAllPlayers(new RagdollGrabSyncPacket(player.getUUID(), false), new CustomPacketPayload[0]);
   }

   private static void applySlowdown(ServerPlayer player) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr == null) return;
      attr.removeModifier(GRAB_SLOWDOWN_ID);
      attr.addTransientModifier(new AttributeModifier(GRAB_SLOWDOWN_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
   }

   private static void removeSlowdown(ServerPlayer player) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) attr.removeModifier(GRAB_SLOWDOWN_ID);
   }
}
