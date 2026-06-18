package dev.leo.sableplayerragdoll.mob.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

public class MobRagdollEndEvent extends Event {
    private final LivingEntity entity;
    private final Vec3 exitVelocity;
    private final Reason reason;

    public MobRagdollEndEvent(LivingEntity entity, Vec3 exitVelocity, Reason reason) {
        this.entity = entity;
        this.exitVelocity = exitVelocity;
        this.reason = reason;
    }

    public LivingEntity entity() {
        return this.entity;
    }

    public Vec3 exitVelocity() {
        return this.exitVelocity;
    }

    public Reason reason() {
        return this.reason;
    }

    public enum Reason {
        EXPIRED,
        RELEASED,
        ENTITY_DEATH,
        ENTITY_REMOVED
    }
}
