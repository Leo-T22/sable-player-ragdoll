package dev.leo.sableplayerragdoll.neoforge.network;

import dev.leo.sableplayerragdoll.neoforge.client.RagdollCameraHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RagdollResetCameraPacket() implements CustomPacketPayload {
   public static final Type<RagdollResetCameraPacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "reset_camera")
   );
   public static final StreamCodec<RegistryFriendlyByteBuf, RagdollResetCameraPacket> STREAM_CODEC = StreamCodec.unit(new RagdollResetCameraPacket());

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(RagdollResetCameraPacket packet, IPayloadContext context) {
      context.enqueueWork(RagdollCameraHelper::resetFromContraptionCamera);
   }
}
