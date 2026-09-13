package dev.leo.sableplayerragdoll.physics;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public final class RagdollCleanup {
    private RagdollCleanup() {}

    public static boolean isOwned(ServerSubLevel part) {
        return !RagdollBlockOwnership.blocks(part).isEmpty();
    }

    public static void removePart(ServerLevel level, ServerSubLevel part) {
        removeLimb(level, part.getUniqueId());
    }

    public static void removeLimb(ServerLevel level, UUID limb) {
        removeLimbs(level, java.util.Set.of(limb));
    }

    public static void removeLimbs(ServerLevel level, java.util.Set<UUID> limbs) {
        for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
            UUID limb = ((RagdollOwnedBlock) be).ragdollIdentity().limb();
            if (limb != null && limbs.contains(limb) && !be.isRemoved()
                    && level.getBlockEntity(be.getBlockPos()) == be) {
                level.setBlock(be.getBlockPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
