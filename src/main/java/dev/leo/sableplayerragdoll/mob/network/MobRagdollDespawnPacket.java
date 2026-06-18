package dev.leo.sableplayerragdoll.mob.network;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MobRagdollDespawnPacket(int entityId) implements CustomPacketPayload {
    public static final Type<MobRagdollDespawnPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SablePlayerRagdoll.MOD_ID, "mob_ragdoll_despawn")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MobRagdollDespawnPacket> STREAM_CODEC = StreamCodec.of(
            MobRagdollDespawnPacket::encode,
            MobRagdollDespawnPacket::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MobRagdollDespawnPacket packet) {
        buffer.writeVarInt(packet.entityId());
    }

    private static MobRagdollDespawnPacket decode(RegistryFriendlyByteBuf buffer) {
        return new MobRagdollDespawnPacket(buffer.readVarInt());
    }

    public static void handle(MobRagdollDespawnPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof LivingEntity livingEntity) || target == player) {
                return;
            }
            MobRagdollAssembly.despawn(player.serverLevel(), livingEntity);
        });
    }
}
