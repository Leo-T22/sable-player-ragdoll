package dev.leo.sableplayerragdoll.physics;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public final class RagdollBlockIdentity {
    private static final String KEY = "RagdollIdentity";
    private UUID owner;
    private UUID limb;
    private UUID parent;
    private long orphanDeadline = -1;
    long nextRelationshipCheck;
    long nextJointCheck;
    long nextPolicyCheck;
    net.minecraft.world.level.block.entity.BlockEntity parentReference;
    java.util.List<java.lang.ref.WeakReference<net.minecraft.world.level.block.entity.BlockEntity>> relatives = java.util.List.of();
    java.util.Map<UUID, UUID> jointBodies = java.util.Map.of();

    public @Nullable UUID parent() { return parent; }
    public void parent(@Nullable UUID parent) {
        this.parent = parent;
        parentReference = null;
        nextRelationshipCheck = 0;
        // Reparenting does not extend an existing unresolved deadline
        if (parent == null) orphanDeadline = -1;
    }
    public boolean orphanExpired(long now) { return orphanDeadline >= 0 && now >= orphanDeadline; }
    public void parentResolved() { orphanDeadline = -1; }
    public void parentMissing(long now) {
        if (orphanDeadline < 0) orphanDeadline = now + RagdollBlockLifetime.CREATION_GRACE_TICKS;
    }
    private void resetReferences() {
        relatives = java.util.List.of();
        parentReference = null;
        jointBodies = java.util.Map.of();
        nextRelationshipCheck = 0;
        nextJointCheck = 0;
        nextPolicyCheck = 0;
    }
    private String kind = "";
    private boolean severed;
    private long createdAt = -1;
    private long expiresAt = -1;
    private String lifetime = "UNBOUND";
    private UUID source;
    private CompoundTag assembly = new CompoundTag();
    private CompoundTag session = new CompoundTag();
    private long loadedAt = -1;

    public CompoundTag assembly() { return assembly; }
    public void assembly(CompoundTag tag) { assembly = tag.copy(); }
    public boolean ready(long now) {
        if (loadedAt < 0) loadedAt = now;
        return now - loadedAt >= RagdollBlockLifetime.CREATION_GRACE_TICKS;
    }

    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }
    public String lifetime() { return lifetime; }
    public UUID source() { return source; }
    public CompoundTag session() { return session; }
    public void session(CompoundTag tag) { session = tag.copy(); }
    public void lifetime(long createdAt, String mode, UUID source, long expiresAt) {
        this.createdAt = createdAt;
        this.lifetime = mode;
        this.source = source;
        this.expiresAt = expiresAt;
    }

    public @Nullable UUID owner() { return owner; }
    public @Nullable UUID limb() { return limb; }
    public String kind() { return kind; }
    public boolean severed() { return severed; }
    public boolean known() { return limb != null; }

    public void assign(UUID owner, UUID limb, String kind) {
        this.owner = owner;
        this.limb = limb;
        this.kind = kind;
        this.severed = false;
        resetReferences();
    }

    public void sever(long now) {
        createdAt = now;
        owner = null;
        parent = null;
        orphanDeadline = -1;
        resetReferences();
        source = null;
        session = new CompoundTag();
        assembly = new CompoundTag();
        lifetime = "TIMED";
        expiresAt = createdAt + 1200;
        severed = true;
    }

    public void save(CompoundTag tag) {
        CompoundTag identity = new CompoundTag();
        identity.putInt("Version", 3);
        if (parent != null) identity.putUUID("Parent", parent);
        identity.putLong("OrphanDeadline", orphanDeadline);
        identity.putLong("CreatedAt", createdAt);
        identity.putLong("ExpiresAt", expiresAt);
        identity.putString("Lifetime", lifetime);
        if (source != null) identity.putUUID("Source", source);
        identity.put("Session", session.copy());
        identity.put("Assembly", assembly.copy());
        if (owner != null) identity.putUUID("Owner", owner);
        if (limb != null) identity.putUUID("Limb", limb);
        identity.putString("Kind", kind);
        identity.putBoolean("Severed", severed);
        tag.put(KEY, identity);
    }

    public void load(CompoundTag tag) {
        CompoundTag identity = tag.getCompound(KEY);
        createdAt = identity.contains("CreatedAt") ? identity.getLong("CreatedAt") : -1;
        expiresAt = identity.contains("ExpiresAt") ? identity.getLong("ExpiresAt") : -1;
        lifetime = identity.contains("Lifetime") ? identity.getString("Lifetime") : "UNBOUND";
        source = identity.hasUUID("Source") ? identity.getUUID("Source") : null;
        session = identity.getCompound("Session").copy();
        assembly = identity.getCompound("Assembly").copy();
        loadedAt = -1;
        limb = identity.hasUUID("Limb") ? identity.getUUID("Limb") : null;
        severed = identity.getBoolean("Severed");
        owner = !severed && identity.hasUUID("Owner") ? identity.getUUID("Owner") : null;
        kind = identity.getString("Kind");
        parent = identity.hasUUID("Parent") ? identity.getUUID("Parent") : null;
        // Version 2 player limbs depended directly on the torso. Mob ownership IDs
        // are not necessarily limb IDs, so do not infer a mob hierarchy here.
        if (identity.getInt("Version") < 3 && owner != null && !owner.equals(limb)
                && session.contains("startTick")) parent = owner;
        orphanDeadline = identity.contains("OrphanDeadline") ? identity.getLong("OrphanDeadline") : -1;
        resetReferences();
    }
}
