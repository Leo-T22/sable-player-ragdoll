package dev.leo.sableplayerragdoll.mob;

import dev.leo.sableplayerragdoll.mob.block.MobPartRole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public class MobRagdollSavedData extends SavedData {
    private static final String DATA_NAME = "sable_player_ragdoll_mob_ragdolls";
    private static final int DEFAULT_DURATION_TICKS = 80;
    private static final SavedData.Factory<MobRagdollSavedData> FACTORY = new SavedData.Factory<>(
            MobRagdollSavedData::new, MobRagdollSavedData::load, null
    );

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static MobRagdollSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void addEntry(UUID entityId, long spawnedAtTick, int durationTicks, Vec3 preRagdollPos,
                          String entityType, CompoundTag entityData,
                          Map<String, PartInfo> partInfos, Map<String, UUID> partIds) {
        entries.put(entityId, new Entry(
                spawnedAtTick,
                durationTicks,
                preRagdollPos,
                entityType,
                entityData == null ? new CompoundTag() : entityData.copy(),
                Map.copyOf(partInfos),
                Map.copyOf(partIds),
                false));
        setDirty();
    }

    public void markMobless(UUID entityId) {
        Entry e = entries.get(entityId);
        if (e != null && !e.mobless()) {
            entries.put(entityId, new Entry(e.spawnedAtTick(), e.durationTicks(), e.preRagdollPos(),
                    e.entityType(), e.entityData(), e.partInfos(), e.partIds(), true));
            setDirty();
        }
    }

    public void removeEntry(UUID entityId) {
        if (entries.remove(entityId) != null) {
            setDirty();
        }
    }

    public void removePart(UUID entityId, UUID partSubLevelId) {
        Entry e = entries.get(entityId);
        if (e == null) {
            return;
        }
        String partName = null;
        for (var pe : e.partIds().entrySet()) {
            if (partSubLevelId.equals(pe.getValue())) {
                partName = pe.getKey();
                break;
            }
        }
        if (partName == null) {
            return;
        }
        Map<String, PartInfo> partInfos = new HashMap<>(e.partInfos());
        Map<String, UUID> partIds = new HashMap<>(e.partIds());
        partInfos.remove(partName);
        partIds.remove(partName);
        entries.put(entityId, new Entry(e.spawnedAtTick(), e.durationTicks(), e.preRagdollPos(),
                e.entityType(), e.entityData(), Map.copyOf(partInfos), Map.copyOf(partIds), e.mobless()));
        setDirty();
    }

    public Entry getEntry(UUID entityId) {
        return entries.get(entityId);
    }

    public Map<UUID, Entry> entries() {
        return Map.copyOf(entries);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("EntityId", entry.getKey());
            entryTag.putLong("SpawnedAt", entry.getValue().spawnedAtTick);
            entryTag.putInt("DurationTicks", entry.getValue().durationTicks);
            entryTag.putBoolean("Mobless", entry.getValue().mobless);
            entryTag.putString("EntityType", entry.getValue().entityType);
            entryTag.put("EntityData", entry.getValue().entityData.copy());
            CompoundTag posTag = new CompoundTag();
            Vec3 pos = entry.getValue().preRagdollPos;
            posTag.putDouble("X", pos.x);
            posTag.putDouble("Y", pos.y);
            posTag.putDouble("Z", pos.z);
            entryTag.put("PreRagdollPos", posTag);
            ListTag partsTag = new ListTag();
            for (var partEntry : entry.getValue().partInfos.entrySet()) {
                CompoundTag partTag = new CompoundTag();
                PartInfo info = partEntry.getValue();
                partTag.putString("Name", partEntry.getKey());
                partTag.putString("Role", info.role().getSerializedName());
                partTag.putFloat("PivotX", info.pivotX());
                partTag.putFloat("PivotY", info.pivotY());
                partTag.putFloat("PivotZ", info.pivotZ());
                partTag.putFloat("CenterX", info.centerX());
                partTag.putFloat("CenterY", info.centerY());
                partTag.putFloat("CenterZ", info.centerZ());
                partTag.putFloat("RotQx", info.rotQx());
                partTag.putFloat("RotQy", info.rotQy());
                partTag.putFloat("RotQz", info.rotQz());
                partTag.putFloat("RotQw", info.rotQw());
                UUID subLevelId = entry.getValue().partIds.get(partEntry.getKey());
                if (subLevelId != null) {
                    partTag.putUUID("SubLevelId", subLevelId);
                }
                partsTag.add(partTag);
            }
            entryTag.put("Parts", partsTag);
            list.add(entryTag);
        }
        tag.put("Entries", list);
        return tag;
    }

    private static MobRagdollSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MobRagdollSavedData data = new MobRagdollSavedData();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            UUID entityId = entryTag.getUUID("EntityId");
            long spawnedAtTick = entryTag.getLong("SpawnedAt");
            int durationTicks = entryTag.contains("DurationTicks", Tag.TAG_INT)
                    ? entryTag.getInt("DurationTicks")
                    : DEFAULT_DURATION_TICKS;
            String entityType = entryTag.getString("EntityType");
            CompoundTag entityData = entryTag.contains("EntityData", Tag.TAG_COMPOUND)
                    ? entryTag.getCompound("EntityData").copy()
                    : new CompoundTag();
            CompoundTag posTag = entryTag.getCompound("PreRagdollPos");
            Vec3 preRagdollPos = new Vec3(posTag.getDouble("X"), posTag.getDouble("Y"), posTag.getDouble("Z"));
            ListTag partsTag = entryTag.getList("Parts", Tag.TAG_COMPOUND);
            Map<String, PartInfo> partInfos = new HashMap<>();
            Map<String, UUID> partIds = new HashMap<>();
            for (int j = 0; j < partsTag.size(); j++) {
                CompoundTag partTag = partsTag.getCompound(j);
                String name = partTag.getString("Name");
                MobPartRole role = MobPartRole.valueOf(partTag.getString("Role").toUpperCase());
                float pivotX = partTag.getFloat("PivotX");
                float pivotY = partTag.getFloat("PivotY");
                float pivotZ = partTag.getFloat("PivotZ");
                float centerX = partTag.getFloat("CenterX");
                float centerY = partTag.getFloat("CenterY");
                float centerZ = partTag.getFloat("CenterZ");
                float rotQx = partTag.getFloat("RotQx");
                float rotQy = partTag.getFloat("RotQy");
                float rotQz = partTag.getFloat("RotQz");
                float rotQw = partTag.contains("RotQw", Tag.TAG_FLOAT) ? partTag.getFloat("RotQw") : 1.0F;
                partInfos.put(name, new PartInfo(role, pivotX, pivotY, pivotZ, centerX, centerY, centerZ,
                        rotQx, rotQy, rotQz, rotQw));
                if (partTag.hasUUID("SubLevelId")) {
                    partIds.put(name, partTag.getUUID("SubLevelId"));
                }
            }
            boolean mobless = entryTag.getBoolean("Mobless");
            data.entries.put(entityId, new Entry(spawnedAtTick, durationTicks, preRagdollPos, entityType, entityData, partInfos, partIds, mobless));
        }
        return data;
    }

    public record Entry(long spawnedAtTick, int durationTicks, Vec3 preRagdollPos, String entityType, CompoundTag entityData,
                        Map<String, PartInfo> partInfos, Map<String, UUID> partIds, boolean mobless) {
        public Entry {
            entityData = entityData == null ? new CompoundTag() : entityData.copy();
        }
    }

    public record PartInfo(MobPartRole role, float pivotX, float pivotY, float pivotZ,
                            float centerX, float centerY, float centerZ,
                            float rotQx, float rotQy, float rotQz, float rotQw) {
    }
}
