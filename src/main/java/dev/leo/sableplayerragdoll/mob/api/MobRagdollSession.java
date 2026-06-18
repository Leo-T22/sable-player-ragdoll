package dev.leo.sableplayerragdoll.mob.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public interface MobRagdollSession {

    LivingEntity entity();

    Vec3 currentVelocity();

    long elapsedTicks();

    void release();
}
