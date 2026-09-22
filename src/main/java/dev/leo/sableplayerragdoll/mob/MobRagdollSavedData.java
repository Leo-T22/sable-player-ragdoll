package dev.leo.sableplayerragdoll.mob;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class MobRagdollSavedData extends SavedData {
    private ListTag entries = new ListTag();
    private static final Factory<MobRagdollSavedData> FACTORY = new Factory<>(
            MobRagdollSavedData::new, MobRagdollSavedData::load, null);

    public static ListTag legacyEntries(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "sable_player_ragdoll_mob_ragdolls").entries.copy();
    }

    private static MobRagdollSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new MobRagdollSavedData();
        data.entries = tag.getList("Entries", Tag.TAG_COMPOUND).copy();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Entries", entries.copy());
        return tag;
    }
}
