package dev.leo.sableplayerragdoll.mob.network;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.mob.client.MobRagdollClientExtractor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MobRagdollLaunchRequestPacket(int entityId) implements CustomPacketPayload {
    public static final Type<MobRagdollLaunchRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SablePlayerRagdoll.MOD_ID, "mob_ragdoll_launch_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MobRagdollLaunchRequestPacket> STREAM_CODEC = StreamCodec.of(
            MobRagdollLaunchRequestPacket::encode,
            MobRagdollLaunchRequestPacket::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MobRagdollLaunchRequestPacket packet) {
        buffer.writeVarInt(packet.entityId());
    }

    private static MobRagdollLaunchRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new MobRagdollLaunchRequestPacket(buffer.readVarInt());
    }

    public static void handle(MobRagdollLaunchRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> MobRagdollClientExtractor.extractAndSend(packet.entityId()));
    }
}
