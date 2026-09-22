package dev.leo.sableplayerragdoll.mob;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class MobRagdollEntityTags {
    public static final TagKey<EntityType<?>> FALL_APART_ON_DEATH = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(SablePlayerRagdoll.MOD_ID, "fall_apart_on_death"));

    private MobRagdollEntityTags() {
    }
}
