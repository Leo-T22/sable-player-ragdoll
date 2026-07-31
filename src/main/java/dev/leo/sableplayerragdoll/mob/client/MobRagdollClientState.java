package dev.leo.sableplayerragdoll.mob.client;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;

public final class MobRagdollClientState {
    private static final Set<Entity> HIDDEN_SOURCES = Collections.newSetFromMap(new WeakHashMap<>());

    private MobRagdollClientState() {
    }

    public static void setHidden(Entity entity, boolean hidden) {
        if (hidden) {
            HIDDEN_SOURCES.add(entity);
        } else {
            HIDDEN_SOURCES.remove(entity);
        }
    }

    public static boolean isHidden(Entity entity) {
        return HIDDEN_SOURCES.contains(entity);
    }
}
