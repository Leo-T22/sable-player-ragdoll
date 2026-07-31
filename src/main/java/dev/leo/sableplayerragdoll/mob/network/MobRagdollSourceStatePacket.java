package dev.leo.sableplayerragdoll.mob.network;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.mob.client.MobRagdollClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MobRagdollSourceStatePacket(int entityId, boolean hidden) implements CustomPacketPayload {
    public static final Type<MobRagdollSourceStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SablePlayerRagdoll.MOD_ID, "mob_ragdoll_source_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MobRagdollSourceStatePacket> STREAM_CODEC = StreamCodec.of(
            MobRagdollSourceStatePacket::encode,
            MobRagdollSourceStatePacket::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MobRagdollSourceStatePacket packet) {
        buffer.writeVarInt(packet.entityId());
        buffer.writeBoolean(packet.hidden());
    }

    private static MobRagdollSourceStatePacket decode(RegistryFriendlyByteBuf buffer) {
        return new MobRagdollSourceStatePacket(buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(MobRagdollSourceStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) {
                return;
            }
            Entity entity = Minecraft.getInstance().level.getEntity(packet.entityId());
            if (entity != null) {
                MobRagdollClientState.setHidden(entity, packet.hidden());
            }
        });
    }
}
