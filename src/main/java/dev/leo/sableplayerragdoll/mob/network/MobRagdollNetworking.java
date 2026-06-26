package dev.leo.sableplayerragdoll.mob.network;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class MobRagdollNetworking {
    private MobRagdollNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(SablePlayerRagdoll.MOD_ID).optional();
        registrar.playToServer(MobRagdollSpawnPacket.TYPE, MobRagdollSpawnPacket.STREAM_CODEC, MobRagdollSpawnPacket::handle);
        registrar.playToServer(MobRagdollDespawnPacket.TYPE, MobRagdollDespawnPacket.STREAM_CODEC, MobRagdollDespawnPacket::handle);
        registrar.playToClient(MobRagdollLaunchRequestPacket.TYPE, MobRagdollLaunchRequestPacket.STREAM_CODEC, MobRagdollLaunchRequestPacket::handle);
    }
}
