package dev.leo.sableplayerragdoll.physics;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class RagdollRelationships {
    private static final Set<BlockEntity> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ThreadLocal<BlockEntity> CURRENT = new ThreadLocal<>();
    private RagdollRelationships() {}

    public static boolean live(BlockEntity be, ServerLevel level) {
        return be != null && !be.isRemoved() && be.getLevel() == level
                && level.hasChunkAt(be.getBlockPos()) && level.getBlockEntity(be.getBlockPos()) == be;
    }

    public static List<BlockEntity> members(BlockEntity be) {
        var id = ((RagdollOwnedBlock) be).ragdollIdentity();
        if (!(be.getLevel() instanceof ServerLevel level) || id.owner() == null) return List.of();
        return id.relatives.stream().map(java.lang.ref.WeakReference::get).filter(other -> live(other, level))
                .filter(other -> id.owner().equals(((RagdollOwnedBlock) other).ragdollIdentity().owner()))
                .toList();
    }

    public static List<BlockEntity> currentMembers(ServerLevel level, UUID owner) {
        var be = CURRENT.get();
        if (be == null || be.getLevel() != level || owner == null
                || !owner.equals(((RagdollOwnedBlock) be).ragdollIdentity().owner())) return null;
        return members(be);
    }

    public static void withBlock(BlockEntity be, Runnable action) {
        var previous = CURRENT.get();
        CURRENT.set(be);
        try { RagdollBlockOwnership.withOwner(((RagdollOwnedBlock) be).ragdollIdentity().owner(), action); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }

    public static void wire(List<BlockEntity> blocks) {
        var family = blocks.stream().map(java.lang.ref.WeakReference::new).toList();
        Map<UUID, BlockEntity> limbs = new HashMap<>();
        for (var be : blocks) {
            var id = ((RagdollOwnedBlock) be).ragdollIdentity();
            if (id.limb() != null) limbs.putIfAbsent(id.limb(), be);
        }
        for (var be : blocks) {
            var id = ((RagdollOwnedBlock) be).ragdollIdentity();
            id.relatives = family;
            id.parentReference = limbs.get(id.parent());
            if (id.parentReference != null && be.getLevel() instanceof ServerLevel level
                    && !id.orphanExpired(level.getGameTime()) && live(id.parentReference, level)
                    && Objects.equals(id.owner(), ((RagdollOwnedBlock) id.parentReference).ragdollIdentity().owner())) {
                id.parentResolved();
                be.setChanged();
            }
        }
    }

    public static void check(BlockEntity be) {
        var level = (ServerLevel) be.getLevel();
        var id = ((RagdollOwnedBlock) be).ragdollIdentity();
        long now = level.getGameTime();
        if (now < id.nextRelationshipCheck) return;
        id.nextRelationshipCheck = now + 10 + level.random.nextInt(11);
        if (id.owner() == null || id.severed()) return;
        if (id.relatives.isEmpty()) PENDING.add(be);
        if (id.parent() == null) return;
        var parent = id.parentReference;
        boolean valid = live(parent, level)
                && id.owner().equals(((RagdollOwnedBlock) parent).ragdollIdentity().owner())
                && id.parent().equals(((RagdollOwnedBlock) parent).ragdollIdentity().limb());
        if (valid) {
            id.parentResolved();
        } else {
            id.parentReference = null;
            id.parentMissing(now);
            PENDING.add(be);
        }
        be.setChanged();
    }

    public static void discover(ServerLevel level) {
        if (level.getGameTime() % 10 != 0 || PENDING.isEmpty()) return;
        Set<UUID> owners = new HashSet<>();
        PENDING.removeIf(be -> {
            if (be.getLevel() != level) return false;
            if (live(be, level)) {
                var owner = ((RagdollOwnedBlock) be).ragdollIdentity().owner();
                if (owner != null) owners.add(owner);
            }
            return true;
        });
        if (owners.isEmpty()) return;
        Map<UUID, List<BlockEntity>> found = new HashMap<>();
        for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
            var owner = ((RagdollOwnedBlock) be).ragdollIdentity().owner();
            if (owners.contains(owner) && live(be, level))
                found.computeIfAbsent(owner, unused -> new ArrayList<>()).add(be);
        }
        found.values().forEach(RagdollRelationships::wire);
    }

    public static void forget(BlockEntity be) {
        PENDING.remove(be);
        var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
        identity.relatives = List.of();
        identity.parentReference = null;
    }

    public static void reset() { PENDING.clear(); CURRENT.remove(); }
}
