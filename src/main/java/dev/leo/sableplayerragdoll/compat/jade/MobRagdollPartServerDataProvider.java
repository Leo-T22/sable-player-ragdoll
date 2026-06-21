package dev.leo.sableplayerragdoll.compat.jade;

import dev.leo.sableplayerragdoll.mob.block.MobPartRole;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public final class MobRagdollPartServerDataProvider implements IServerDataProvider<BlockAccessor> {
    public static final MobRagdollPartServerDataProvider INSTANCE = new MobRagdollPartServerDataProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "mob_ragdoll_part_data");

    private MobRagdollPartServerDataProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(final CompoundTag data, final BlockAccessor accessor) {
        final BlockEntity be = RagdollPartServerDataProvider.resolveBlockEntity(accessor);
        if (!(be instanceof MobRagdollPartBlockEntity mob)) {
            return;
        }
        final ResourceLocation entityType = mob.entityType();
        if (entityType != null) {
            data.putString("EntityType", entityType.toString());
        }
        final MobPartRole role = mob.role();
        if (role != null) {
            data.putString("Role", role.getSerializedName());
        }
        data.putBoolean("Baby", mob.baby());

        final UUID sourceId = mob.sourceEntityId();
        if (sourceId != null && accessor.getLevel() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(sourceId) instanceof LivingEntity living) {
            RagdollPartServerDataProvider.putLivingStats(data, living);
            return;
        }
        data.putFloat("Health", 0f);
        data.putFloat("MaxHealth", 20f);
    }
}
