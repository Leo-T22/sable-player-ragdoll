package dev.leo.sableplayerragdoll.neoforge.network;

import dev.leo.sableplayerragdoll.neoforge.client.RagdollClientPoseCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RagdollRequestPosePacket(long requestId) implements CustomPacketPayload {
   public static final Type<RagdollRequestPosePacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "request_pose")
   );
   public static final StreamCodec<RegistryFriendlyByteBuf, RagdollRequestPosePacket> STREAM_CODEC = StreamCodec.of(
      (buffer, packet) -> buffer.writeLong(packet.requestId()),
      buffer -> new RagdollRequestPosePacket(buffer.readLong())
   );

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(RagdollRequestPosePacket packet, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (Minecraft.getInstance().player == null) return;
         PacketDistributor.sendToServer(new RagdollPoseResponsePacket(
            packet.requestId(),
            RagdollClientPoseCapture.capture(),
            Minecraft.getInstance().player.yBodyRot
         ));
      });
   }
}
