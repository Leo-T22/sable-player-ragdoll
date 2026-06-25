package dev.leo.sableplayerragdoll.mob.network;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly;
import dev.leo.sableplayerragdoll.mob.block.MobPartRole;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MobRagdollSpawnPacket(int entityId, String entityType, float bodyYaw, List<Part> parts) implements CustomPacketPayload {
    public static final Type<MobRagdollSpawnPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SablePlayerRagdoll.MOD_ID, "mob_ragdoll_spawn")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MobRagdollSpawnPacket> STREAM_CODEC = StreamCodec.of(
            MobRagdollSpawnPacket::encode,
            MobRagdollSpawnPacket::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MobRagdollSpawnPacket packet) {
        buffer.writeVarInt(packet.entityId());
        buffer.writeUtf(packet.entityType(), 256);
        buffer.writeFloat(packet.bodyYaw());
        buffer.writeVarInt(packet.parts().size());
        for (Part part : packet.parts()) {
            buffer.writeEnum(part.role());
            buffer.writeUtf(part.partName(), 128);
            buffer.writeVarInt(part.keepPartNames().size());
            for (String keepPartName : part.keepPartNames()) {
                buffer.writeUtf(keepPartName, 128);
            }
            buffer.writeUtf(part.texture(), 256);
            buffer.writeUtf(part.parentName() != null ? part.parentName() : "", 128);
            buffer.writeBoolean(part.variantData() != null);
            if (part.variantData() != null) {
                buffer.writeNbt(part.variantData());
            }
            buffer.writeBoolean(part.baby());
            buffer.writeFloat(part.renderScale());
            buffer.writeFloat(part.xOffset());
            buffer.writeFloat(part.yOffset());
            buffer.writeFloat(part.zOffset());
            buffer.writeFloat(part.pivotX());
            buffer.writeFloat(part.pivotY());
            buffer.writeFloat(part.pivotZ());
            buffer.writeFloat(part.rotQx());
            buffer.writeFloat(part.rotQy());
            buffer.writeFloat(part.rotQz());
            buffer.writeFloat(part.rotQw());
            buffer.writeFloat(part.renderQx());
            buffer.writeFloat(part.renderQy());
            buffer.writeFloat(part.renderQz());
            buffer.writeFloat(part.renderQw());
            buffer.writeFloat(part.xSize());
            buffer.writeFloat(part.ySize());
            buffer.writeFloat(part.zSize());
            buffer.writeVarInt(part.quads().size());
            for (Quad quad : part.quads()) {
                buffer.writeFloat(quad.normalX());
                buffer.writeFloat(quad.normalY());
                buffer.writeFloat(quad.normalZ());
                for (Vertex vertex : quad.vertices()) {
                    buffer.writeFloat(vertex.x());
                    buffer.writeFloat(vertex.y());
                    buffer.writeFloat(vertex.z());
                    buffer.writeFloat(vertex.u());
                    buffer.writeFloat(vertex.v());
                }
            }
        }
    }

    private static MobRagdollSpawnPacket decode(RegistryFriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        String entityType = buffer.readUtf(256);
        float bodyYaw = buffer.readFloat();
        int count = Math.min(buffer.readVarInt(), 64);
        List<Part> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            MobPartRole role = buffer.readEnum(MobPartRole.class);
            String partName = buffer.readUtf(128);
            int keepNameCount = Math.min(buffer.readVarInt(), 64);
            List<String> keepPartNames = new ArrayList<>(keepNameCount);
            for (int k = 0; k < keepNameCount; k++) {
                keepPartNames.add(buffer.readUtf(128));
            }
            String texture = buffer.readUtf(256);
            String parentName = buffer.readUtf(128);
            CompoundTag variantData = buffer.readBoolean() ? buffer.readNbt() : null;
            boolean baby = buffer.readBoolean();
            float renderScale = buffer.readFloat();
            float xOffset = buffer.readFloat();
            float yOffset = buffer.readFloat();
            float zOffset = buffer.readFloat();
            float pivotX = buffer.readFloat();
            float pivotY = buffer.readFloat();
            float pivotZ = buffer.readFloat();
            float rotQx = buffer.readFloat();
            float rotQy = buffer.readFloat();
            float rotQz = buffer.readFloat();
            float rotQw = buffer.readFloat();
            float renderQx = buffer.readFloat();
            float renderQy = buffer.readFloat();
            float renderQz = buffer.readFloat();
            float renderQw = buffer.readFloat();
            float xSize = buffer.readFloat();
            float ySize = buffer.readFloat();
            float zSize = buffer.readFloat();
            int quadCount = Math.min(buffer.readVarInt(), 128);
            List<Quad> quads = new ArrayList<>(quadCount);
            for (int q = 0; q < quadCount; q++) {
                float normalX = buffer.readFloat();
                float normalY = buffer.readFloat();
                float normalZ = buffer.readFloat();
                List<Vertex> vertices = new ArrayList<>(4);
                for (int v = 0; v < 4; v++) {
                    vertices.add(new Vertex(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));
                }
                quads.add(new Quad(List.copyOf(vertices), normalX, normalY, normalZ));
            }
            parts.add(new Part(
                    role,
                    partName,
                    List.copyOf(keepPartNames),
                    texture,
                    parentName.isEmpty() ? null : parentName,
                    variantData,
                    baby,
                    renderScale,
                    xOffset,
                    yOffset,
                    zOffset,
                    pivotX,
                    pivotY,
                    pivotZ,
                    rotQx,
                    rotQy,
                    rotQz,
                    rotQw,
                    renderQx,
                    renderQy,
                    renderQz,
                    renderQw,
                    xSize,
                    ySize,
                    zSize,
                    List.copyOf(quads)
            ));
        }
        return new MobRagdollSpawnPacket(entityId, entityType, bodyYaw, List.copyOf(parts));
    }

    public static void handle(MobRagdollSpawnPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof LivingEntity livingEntity) || target == player) {
                return;
            }
            if (!MobRagdollAssembly.hasPendingLaunch(livingEntity.getUUID())) {
                return;
            }

            List<MobRagdollAssembly.PartSpawn> spawns = packet.parts().stream()
                    .map(part -> new MobRagdollAssembly.PartSpawn(
                            part.role(),
                            packet.entityType(),
                            part.partName(),
                            part.keepPartNames(),
                            part.parentName(),
                            part.variantData() != null ? part.variantData().copy() : null,
                            part.baby(),
                            part.renderScale(),
                            part.xOffset(),
                            part.yOffset(),
                            part.zOffset(),
                            part.pivotX(),
                            part.pivotY(),
                            part.pivotZ(),
                            part.rotQx(),
                            part.rotQy(),
                            part.rotQz(),
                            part.rotQw(),
                            part.renderQx(),
                            part.renderQy(),
                            part.renderQz(),
                            part.renderQw(),
                            part.xSize(),
                            part.ySize(),
                            part.zSize(),
                            part.texture(),
                            part.quads().stream()
                                    .map(quad -> new MobRagdollAssembly.Quad(
                                            quad.vertices().stream()
                                                    .map(vertex -> new MobRagdollAssembly.Vertex(vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v()))
                                                    .toList(),
                                            quad.normalX(),
                                            quad.normalY(),
                                            quad.normalZ()
                                    ))
                                    .toList()
                    ))
                    .toList();

            MobRagdollAssembly.setClientBodyYaw(livingEntity.getUUID(), packet.bodyYaw());
            MobRagdollAssembly.consumePendingLaunch(player.serverLevel(), livingEntity, spawns);
        });
    }

    public record Part(
            MobPartRole role,
            String partName,
            List<String> keepPartNames,
            String texture,
            String parentName,
            CompoundTag variantData,
            boolean baby,
            float renderScale,
            float xOffset,
            float yOffset,
            float zOffset,
            float pivotX,
            float pivotY,
            float pivotZ,
            float rotQx,
            float rotQy,
            float rotQz,
            float rotQw,
            float renderQx,
            float renderQy,
            float renderQz,
            float renderQw,
            float xSize,
            float ySize,
            float zSize,
            List<Quad> quads
    ) {
    }

    public record Quad(List<Vertex> vertices, float normalX, float normalY, float normalZ) {
    }

    public record Vertex(float x, float y, float z, float u, float v) {
    }
}
