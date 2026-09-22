package dev.leo.sableplayerragdoll.mob.client;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;

public final class MobRagdollClientState {
    private static final Set<Entity> HIDDEN_SOURCES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final String DEATH_PENDING_UNTIL = "sable_player_ragdoll_death_pending_until";
    private static final int DEATH_PENDING_TICKS = 60;

    private MobRagdollClientState() {
    }

    public static void setHidden(Entity entity, boolean hidden) {
        entity.getPersistentData().remove(DEATH_PENDING_UNTIL);
        if (hidden) {
            HIDDEN_SOURCES.add(entity);
        } else {
            HIDDEN_SOURCES.remove(entity);
        }
    }

    public static boolean isHidden(Entity entity) {
        return HIDDEN_SOURCES.contains(entity);
    }

    public static void setDeathPending(Entity entity) {
        entity.getPersistentData().putLong(
                DEATH_PENDING_UNTIL,
                entity.level().getGameTime() + DEATH_PENDING_TICKS);
    }

    public static boolean isDeathPending(Entity entity) {
        var data = entity.getPersistentData();
        if (!data.contains(DEATH_PENDING_UNTIL)) return false;
        if (entity.level().getGameTime() < data.getLong(DEATH_PENDING_UNTIL)) return true;
        data.remove(DEATH_PENDING_UNTIL);
        return false;
    }
}
