package dev.leo.sableplayerragdoll.physics;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

final class RagdollAccessoriesLiveSync {
    private static final ConcurrentHashMap<UUID, Long> LAST_SIGNATURE = new ConcurrentHashMap<>();

    private RagdollAccessoriesLiveSync() {}

    static void poll(ServerLevel level, UUID rootId, Player player) {
        if (!ModList.get().isLoaded("accessories")) return;
        long signature = RagdollAccessoriesEquipmentHelper.accessoriesSignature(player);
        Long previous = LAST_SIGNATURE.put(rootId, signature);
        if (previous != null && previous == signature) return;
        RagdollEquipmentHelper.syncAccessoriesAndSend(level, rootId, player);
    }

    static void clear(UUID rootId) {
        LAST_SIGNATURE.remove(rootId);
    }
}
