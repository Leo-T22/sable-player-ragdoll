package dev.leo.sableplayerragdoll.mob;

import dev.leo.sableplayerragdoll.mob.block.MobPartRole;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.mob.api.MobRagdollLaunchOptions;
import dev.leo.sableplayerragdoll.physics.RagdollBlockOwnership;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class MobRagdollAssemblyData {
    private static final int DEFAULT_DURATION_TICKS = 80;
    private MobRagdollAssemblyData() {}

    public static void migrate(ServerLevel level, MobRagdollPartBlockEntity block,
                               dev.ryanhcode.sable.sublevel.ServerSubLevel body) {
        var id = block.ragdollIdentity();
        if (id.severed()) return;
        if (id.assembly().getInt("MobDataVersion") >= 2) return;
        if (!block.renderAnchor() && id.createdAt() >= 0) {
            CompoundTag marker = new CompoundTag();
            marker.putInt("MobDataVersion", 2);
            id.assembly(marker);
            block.setChanged();
            return;
        }
        CompoundTag tag = id.assembly().copy();
        if (!tag.hasUUID("EntityId") && body != null) {
            for (var item : MobRagdollSavedData.legacyEntries(level)) {
                CompoundTag candidate = (CompoundTag) item;
                var parts = candidate.getList("Parts", Tag.TAG_COMPOUND);
                for (int i = 0; i < parts.size(); i++) {
                    var part = parts.getCompound(i);
                    if (!part.hasUUID("SubLevelId") || !body.getUniqueId().equals(part.getUUID("SubLevelId"))) continue;
                    tag = candidate.copy();
                    if (!id.known()) {
                        UUID owner = UUID.nameUUIDFromBytes(("legacy-mob:" + candidate.getUUID("EntityId") + ":" + candidate.getLong("SpawnedAt"))
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        block.setRagdollIdentity(owner, body.getUniqueId(), part.getString("Name"));
                    }
                    break;
                }
                if (tag.hasUUID("EntityId")) break;
            }
        }
        if (!tag.hasUUID("EntityId") && id.limb() != null) {
            for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
                if (be instanceof MobRagdollPartBlockEntity anchor && anchor.renderAnchor()
                        && id.limb().equals(anchor.ragdollIdentity().limb())
                        && anchor.ragdollIdentity().assembly().hasUUID("EntityId")) {
                    tag = anchor.ragdollIdentity().assembly().copy();
                    break;
                }
            }
        }
        if (!tag.hasUUID("EntityId")) return;
        tag.putInt("MobDataVersion", 2);
        tag.remove("EntityData");
        tag.remove("EntityType");
        if (tag.hasUUID("EntityId") && id.owner() != null) {
            tag.putUUID("SessionId", id.owner());
            boolean mobless = tag.getBoolean("Mobless");
            int duration = tag.contains("DurationTicks") ? tag.getInt("DurationTicks") : DEFAULT_DURATION_TICKS;
            long created = tag.getLong("SpawnedAt");
            boolean permanent = mobless && duration == MobRagdollAssembly.DEFAULT_MOBLESS_DURATION_TICKS;
            if (id.createdAt() < 0 || mobless) id.lifetime(created, permanent ? "PERMANENT" : mobless ? "TIMED" : "MOB",
                    mobless ? null : tag.getUUID("EntityId"), permanent ? -1 : created + (long) duration);
        }
        if (!block.renderAnchor()) {
            tag = new CompoundTag();
            tag.putInt("MobDataVersion", 2);
        }
        id.assembly(tag);
        block.setChanged();
    }

    public static Map<UUID, Entry> loaded(ServerLevel level) {
        return loaded(RagdollBlockOwnership.loadedBlocks(level));
    }

    public static Map<UUID, Entry> loaded(java.util.Collection<net.minecraft.world.level.block.entity.BlockEntity> blocks) {
        Map<UUID, Entry> result = new HashMap<>();
        for (var be : blocks) {
            if (!(be instanceof MobRagdollPartBlockEntity mob) || !mob.renderAnchor()) continue;
            var id = mob.ragdollIdentity();
            if (id.owner() == null || id.severed()) continue;
            var tag = id.assembly();
            if (tag.hasUUID("EntityId")) result.computeIfAbsent(id.owner(), owner -> decode(tag, owner));
        }
        return result;
    }

    public static void write(ServerLevel level, Entry entry) {
        CompoundTag tag = encode(entry);
        for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
            if (!(be instanceof MobRagdollPartBlockEntity mob)) continue;
            var id = mob.ragdollIdentity();
            if (entry.sessionId().equals(id.owner()) && !id.severed()) {
                CompoundTag stored = mob.renderAnchor() ? tag : new CompoundTag();
                stored.putInt("MobDataVersion", 2);
                id.assembly(stored);
                be.setChanged();
            }
        }
    }

    public static CompoundTag encode(Entry entry) {
        CompoundTag entryTag = new CompoundTag();
        entryTag.putInt("MobDataVersion", 2);
        entryTag.putUUID("EntityId", entry.sourceId());
        entryTag.putUUID("SessionId", entry.sessionId());
        entryTag.putLong("SpawnedAt", entry.spawnedAtTick);
        entryTag.putInt("DurationTicks", entry.durationTicks);
        entryTag.putInt("CorpseDurationTicks", entry.corpseDurationTicks);
        entryTag.putBoolean("FallApartOnDeath", entry.fallApartOnDeath);
        entryTag.putBoolean("Mobless", entry.mobless);
        CompoundTag posTag = new CompoundTag();
        Vec3 pos = entry.preRagdollPos;
        posTag.putDouble("X", pos.x);
        posTag.putDouble("Y", pos.y);
        posTag.putDouble("Z", pos.z);
        entryTag.put("PreRagdollPos", posTag);
        ListTag partsTag = new ListTag();
        for (var partEntry : entry.partInfos.entrySet()) {
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
            UUID subLevelId = entry.partIds.get(partEntry.getKey());
            if (subLevelId != null) {
                partTag.putUUID("LimbId", subLevelId);
            }
            partsTag.add(partTag);
        }
        entryTag.put("Parts", partsTag);
        return entryTag;
    }

    public static Entry decode(CompoundTag entryTag, UUID session) {
        long spawnedAtTick = entryTag.getLong("SpawnedAt");
        int durationTicks = entryTag.contains("DurationTicks", Tag.TAG_INT)
                ? entryTag.getInt("DurationTicks")
                : DEFAULT_DURATION_TICKS;
        int corpseDurationTicks = entryTag.contains("CorpseDurationTicks", Tag.TAG_INT)
                ? entryTag.getInt("CorpseDurationTicks")
                : MobRagdollLaunchOptions.DEFAULT_CORPSE_DURATION_TICKS;
        CompoundTag posTag = entryTag.getCompound("PreRagdollPos");
        Vec3 preRagdollPos = new Vec3(posTag.getDouble("X"), posTag.getDouble("Y"), posTag.getDouble("Z"));
        ListTag partsTag = entryTag.getList("Parts", Tag.TAG_COMPOUND);
        Map<String, PartInfo> partInfos = new HashMap<>();
        Map<String, UUID> partIds = new HashMap<>();
        for (int j = 0; j < partsTag.size(); j++) {
            CompoundTag partTag = partsTag.getCompound(j);
            String name = partTag.getString("Name");
            MobPartRole role = MobPartRole.valueOf(partTag.getString("Role").toUpperCase(java.util.Locale.ROOT));
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
            if (partTag.hasUUID("LimbId") || partTag.hasUUID("SubLevelId")) {
                partIds.put(name, partTag.getUUID(partTag.hasUUID("LimbId") ? "LimbId" : "SubLevelId"));
            }
        }
        boolean mobless = entryTag.getBoolean("Mobless");
        return new Entry(session, entryTag.getUUID("EntityId"), spawnedAtTick, durationTicks, corpseDurationTicks,
                entryTag.getBoolean("FallApartOnDeath"), preRagdollPos, partInfos, partIds, mobless);
    }

    public record Entry(UUID sessionId, UUID sourceId, long spawnedAtTick, int durationTicks,
                        int corpseDurationTicks, boolean fallApartOnDeath, Vec3 preRagdollPos,
                        Map<String, PartInfo> partInfos, Map<String, UUID> partIds, boolean mobless) {
        public Entry {
            partInfos = Map.copyOf(partInfos);
            partIds = Map.copyOf(partIds);
        }
    }

    public record PartInfo(MobPartRole role, float pivotX, float pivotY, float pivotZ,
                            float centerX, float centerY, float centerZ,
                            float rotQx, float rotQy, float rotQz, float rotQw) {
    }
}
