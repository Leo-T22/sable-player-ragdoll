package dev.leo.sableplayerragdoll.mob.api;

import dev.leo.sableplayerragdoll.api.RagdollWailingOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public interface MobRagdollSession {

    LivingEntity entity();

    Vec3 currentVelocity();

    long elapsedTicks();

    void applyWailing(RagdollWailingOptions options);

    default void applyWailing(double stiffness, int durationTicks, int intervalTicks) {
        applyWailing(RagdollWailingOptions.builder()
                .stiffness(stiffness)
                .durationTicks(durationTicks)
                .intervalTicks(intervalTicks)
                .build());
    }

    default void applyWailing(int durationTicks) {
        applyWailing(RagdollWailingOptions.builder().durationTicks(durationTicks).build());
    }

    void stopWailing();

    void release();
}
