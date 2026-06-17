package dev.leo.sableplayerragdoll.neoforge.network;

import dev.leo.sableplayerragdoll.api.RagdollAsyncPoseRequests;
import dev.leo.sableplayerragdoll.api.RagdollLimbConfig;
import dev.leo.sableplayerragdoll.api.RagdollLimbOptions;
import dev.leo.sableplayerragdoll.api.RagdollPoseSnapshot;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RagdollPoseResponsePacket(long requestId, RagdollLimbOptions pose, float bodyYaw) implements CustomPacketPayload {
   public static final Type<RagdollPoseResponsePacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "pose_response")
   );
   public static final StreamCodec<RegistryFriendlyByteBuf, RagdollPoseResponsePacket> STREAM_CODEC = StreamCodec.of(
      RagdollPoseResponsePacket::write,
      RagdollPoseResponsePacket::read
   );

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(RagdollPoseResponsePacket packet, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            RagdollAsyncPoseRequests.resolve(player, packet.requestId(), new RagdollPoseSnapshot(packet.pose(), packet.bodyYaw()));
         }
      });
   }

   private static void write(RegistryFriendlyByteBuf buffer, RagdollPoseResponsePacket packet) {
      buffer.writeLong(packet.requestId());
      buffer.writeFloat(packet.bodyYaw());
      for (BodyPart part : BodyPart.values()) {
         RagdollLimbConfig config = packet.pose().get(part);
         buffer.writeBoolean(config != null);
         if (config != null) {
            buffer.writeDouble(config.rightOffset().orElse(0.0));
            buffer.writeDouble(config.upOffset().orElse(0.0));
            buffer.writeDouble(config.forwardOffset().orElse(0.0));
            buffer.writeDouble(config.initialPitchDegrees().orElse(0.0));
            buffer.writeDouble(config.initialYawDegrees().orElse(0.0));
            buffer.writeDouble(config.initialRollDegrees().orElse(0.0));
         }
      }
   }

   private static RagdollPoseResponsePacket read(RegistryFriendlyByteBuf buffer) {
      long requestId = buffer.readLong();
      float bodyYaw = buffer.readFloat();
      RagdollLimbOptions.Builder builder = RagdollLimbOptions.builder();
      for (BodyPart part : BodyPart.values()) {
         if (buffer.readBoolean()) {
            builder.limb(part, RagdollLimbConfig.builder()
               .offset(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
               .initialRotation(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
         }
      }
      return new RagdollPoseResponsePacket(requestId, builder.build(), bodyYaw);
   }
}
