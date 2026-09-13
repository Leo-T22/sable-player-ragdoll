package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class RagdollBlockOwnership {
    private RagdollBlockOwnership() {}

    public static List<BlockEntity> blocks(ServerSubLevel subLevel) {
        List<BlockEntity> result = new ArrayList<>();
        if (subLevel.getPlot() == null) return result;
        for (var holder : subLevel.getPlot().getLoadedChunks()) {
            for (var be : holder.getChunk().getBlockEntities().values()) {
                if (be instanceof RagdollOwnedBlock) result.add(be);
            }
        }
        return result;
    }

    private static final ThreadLocal<UUID> SELECTED_OWNER = new ThreadLocal<>();

    public static void withOwner(UUID owner, Runnable action) {
        UUID previous = SELECTED_OWNER.get();
        SELECTED_OWNER.set(owner);
        try { action.run(); }
        finally {
            if (previous == null) SELECTED_OWNER.remove();
            else SELECTED_OWNER.set(previous);
        }
    }

    public static UUID ownerForPlayer(ServerLevel level, UUID player) {
        var source = level.getEntity(player);
        if (source != null && source.getVehicle() != null) {
            var tag = source.getVehicle().getPersistentData();
            if (tag.hasUUID(RagdollBlockLifetime.SOURCE_SESSION)) return tag.getUUID(RagdollBlockLifetime.SOURCE_SESSION);
        }
        for (var be : loadedBlocks(level)) {
            var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
            if (player.equals(identity.source()) && !identity.severed()) return identity.owner();
        }
        return null;
    }

    public static List<BlockEntity> loadedBlocks(ServerLevel level) {
        List<BlockEntity> result = new ArrayList<>();
        var container = SubLevelContainer.getContainer(level);
        if (container != null) for (var part : container.getAllSubLevels()) result.addAll(blocks(part));
        return result;
    }

    public static UUID ownerOf(ServerSubLevel part) {
        if (SELECTED_OWNER.get() != null) return SELECTED_OWNER.get();
        UUID candidate = null;
        for (var be : blocks(part)) {
            var id = ((RagdollOwnedBlock) be).ragdollIdentity();
            if (id.owner() == null || !id.owner().equals(id.limb())) continue;
            if (id.limb().equals(part.getUniqueId())) return id.owner();
            if (candidate != null && !candidate.equals(id.owner())) return null;
            candidate = id.owner();
        }
        return candidate;
    }

    public static UUID sessionId(ServerSubLevel part) {
        UUID owner = ownerOf(part);
        return owner == null ? part.getUniqueId() : owner;
    }

    public static ServerSubLevel root(ServerLevel level, UUID owner) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null || owner == null) return null;
        var cached = RagdollRelationships.currentMembers(level, owner);
        if (cached != null) {
            for (var be : cached) {
                if (owner.equals(((RagdollOwnedBlock) be).ragdollIdentity().limb())
                        && dev.ryanhcode.sable.Sable.HELPER.getContaining(level, be.getBlockPos()) instanceof ServerSubLevel current)
                    return current;
            }
            return null;
        }
        if (container.getSubLevel(owner) instanceof ServerSubLevel original) {
            for (var be : blocks(original)) {
                var id = ((RagdollOwnedBlock) be).ragdollIdentity();
                if (owner.equals(id.owner()) && owner.equals(id.limb())) return original;
            }
        }
        for (var part : container.getAllSubLevels()) for (var be : blocks(part)) {
            var id = ((RagdollOwnedBlock) be).ragdollIdentity();
            if (owner.equals(id.owner()) && owner.equals(id.limb())) return part;
        }
        return null;
    }

    public static net.minecraft.nbt.CompoundTag session(ServerSubLevel part) {
        UUID owner = ownerOf(part);
        for (var be : blocks(part)) {
            var id = ((RagdollOwnedBlock) be).ragdollIdentity();
            if (owner != null && owner.equals(id.owner()) && !id.session().isEmpty()) return id.session().copy();
        }
        return new net.minecraft.nbt.CompoundTag();
    }

    public static void session(ServerSubLevel part, net.minecraft.nbt.CompoundTag tag) {
        RagdollBlockLifetime.playerPolicy(part, tag);
    }

    public static BlockEntity findLimb(ServerLevel level, UUID limb) {
        var cached = RagdollRelationships.currentMembers(level, SELECTED_OWNER.get());
        for (var be : cached == null ? loadedBlocks(level) : cached) {
            var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
            if (limb.equals(identity.limb()) && (!(be instanceof
                    MobRagdollPartBlockEntity mob) || mob.renderAnchor())) return be;
        }
        return null;
    }

    public static ServerSubLevel partForLimb(ServerLevel level, UUID limb) {
        var be = findLimb(level, limb);
        if (be != null && dev.ryanhcode.sable.Sable.HELPER.getContaining(level, be.getBlockPos()) instanceof ServerSubLevel part) return part;
        return null;
    }

    public static UUID limbAt(ServerLevel level, BlockPos pos, UUID fallback) {
        if (level.getBlockEntity(pos) instanceof RagdollOwnedBlock block && block.ragdollIdentity().known()) {
            return block.ragdollIdentity().limb();
        }
        return fallback;
    }

    public static boolean hasLimb(ServerLevel level, UUID limb) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return false;
        for (var subLevel : container.getAllSubLevels()) {
            for (var be : blocks(subLevel)) {
                if (limb.equals(((RagdollOwnedBlock) be).ragdollIdentity().limb())) return true;
            }
        }
        return false;
    }

    public static void assign(ServerSubLevel subLevel, UUID owner, UUID limb, String kind) {
        for (var be : blocks(subLevel)) ((RagdollOwnedBlock) be).setRagdollIdentity(owner, limb, kind);
    }

    public static void sever(ServerLevel level, UUID limb) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        for (var subLevel : List.copyOf(container.getAllSubLevels())) {
            for (var be : blocks(subLevel)) {
                var owned = (RagdollOwnedBlock) be;
                if (limb.equals(owned.ragdollIdentity().limb())) owned.markSevered();
            }
        }
    }

}
