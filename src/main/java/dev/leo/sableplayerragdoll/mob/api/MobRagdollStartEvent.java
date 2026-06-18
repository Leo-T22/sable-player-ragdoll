package dev.leo.sableplayerragdoll.mob.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class MobRagdollStartEvent extends Event implements ICancellableEvent {
    private final LivingEntity entity;
    private Vec3 velocity;

    public MobRagdollStartEvent(LivingEntity entity, Vec3 velocity) {
        this.entity = entity;
        this.velocity = velocity;
    }

    public LivingEntity entity() {
        return this.entity;
    }

    public Vec3 velocity() {
        return this.velocity;
    }

    public void setVelocity(Vec3 velocity) {
        this.velocity = velocity;
    }
}
