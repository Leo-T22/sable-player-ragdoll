package dev.leo.sableplayerragdoll.neoforge.network;

import dev.leo.sableplayerragdoll.neoforge.client.RagdollCameraHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RagdollUnlockedCameraPacket() implements CustomPacketPayload {
   public static final Type<RagdollUnlockedCameraPacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "unlocked_contraption_camera")
   );
   public static final StreamCodec<ByteBuf, RagdollUnlockedCameraPacket> STREAM_CODEC = StreamCodec.unit(new RagdollUnlockedCameraPacket());

   public static void handle(RagdollUnlockedCameraPacket payload, IPayloadContext context) {
      context.enqueueWork(RagdollCameraHelper::requestUnlockedContraptionCamera);
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
