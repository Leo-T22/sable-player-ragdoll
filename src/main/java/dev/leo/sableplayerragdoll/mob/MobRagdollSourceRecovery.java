package dev.leo.sableplayerragdoll.mob;

import dev.leo.sableplayerragdoll.physics.RagdollBlockLifetime;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class MobRagdollSourceRecovery {
    private static final String KEY = "sable_player_ragdoll_mob_recovery";

    private MobRagdollSourceRecovery() {}

    public static void begin(LivingEntity entity, UUID session, long expiresAt) {
        // Never snapshot flags that this mod has already overridden.
        restore(entity);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Session", session);
        tag.putLong("ExpiresAt", expiresAt);
        tag.putLong("CreatedAt", entity.level().getGameTime());
        tag.putBoolean("Invisible", entity.isInvisible());
        tag.putBoolean("NoPhysics", entity.noPhysics);
        if (entity instanceof Mob mob) tag.putBoolean("NoAI", mob.isNoAi());
        entity.getPersistentData().put(KEY, tag);
        entity.getPersistentData().putUUID(RagdollBlockLifetime.SOURCE_SESSION, session);
    }

    public static boolean hasRecord(LivingEntity entity) {
        return entity.getPersistentData().getCompound(KEY).hasUUID("Session");
    }

    public static UUID session(LivingEntity entity) {
        var tag = entity.getPersistentData().getCompound(KEY);
        return tag.hasUUID("Session") ? tag.getUUID("Session") : null;
    }

    public static boolean active(LivingEntity entity) {
        return active(entity.getPersistentData(), entity.level().getGameTime());
    }

    static boolean active(CompoundTag data, long now) {
        var tag = data.getCompound(KEY);
        return tag.hasUUID("Session") && data.hasUUID(RagdollBlockLifetime.SOURCE_SESSION)
                && tag.getUUID("Session").equals(data.getUUID(RagdollBlockLifetime.SOURCE_SESSION))
                && now < tag.getLong("ExpiresAt");
    }

    public static boolean matches(LivingEntity entity, UUID session) {
        var data = entity.getPersistentData();
        return session != null && data.hasUUID(RagdollBlockLifetime.SOURCE_SESSION)
                && session.equals(data.getUUID(RagdollBlockLifetime.SOURCE_SESSION));
    }

    public static void extend(LivingEntity entity, long expiresAt) {
        CompoundTag tag = entity.getPersistentData().getCompound(KEY);
        if (!tag.hasUUID("Session")) return;
        tag.putLong("ExpiresAt", expiresAt);
        entity.getPersistentData().put(KEY, tag);
    }

    public static void restore(LivingEntity entity) {
        var data = entity.getPersistentData();
        CompoundTag tag = data.getCompound(KEY);
        if (!tag.hasUUID("Session")) return;
        if (matches(entity, tag.getUUID("Session"))) {
            data.remove(RagdollBlockLifetime.SOURCE_SESSION);
            entity.setInvisible(tag.getBoolean("Invisible"));
            entity.noPhysics = tag.getBoolean("NoPhysics");
            if (entity instanceof Mob mob) mob.setNoAi(tag.getBoolean("NoAI"));
        }
        data.remove(KEY);
        entity.refreshDimensions();
    }

    public static void clear(LivingEntity entity) {
        entity.getPersistentData().remove(RagdollBlockLifetime.SOURCE_SESSION);
        entity.getPersistentData().remove(KEY);
    }
}
