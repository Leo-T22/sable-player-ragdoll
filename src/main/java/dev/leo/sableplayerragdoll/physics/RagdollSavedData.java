package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.api.RagdollLimbConfig;
import dev.leo.sableplayerragdoll.api.RagdollLimbOptions;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class RagdollSavedData extends SavedData {
   private static final String FILE_ID = SablePlayerRagdoll.MOD_ID;
   private static final SavedData.Factory<RagdollSavedData> FACTORY = new SavedData.Factory<>(RagdollSavedData::new, RagdollSavedData::load);
   private final Map<UUID, Map<BodyPart, UUID>> ragdolls = new HashMap<>();
   private final Map<UUID, RagdollLimbOptions> ragdollLimbs = new HashMap<>();

   public static RagdollSavedData get(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
   }

   public static RagdollSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
      RagdollSavedData data = new RagdollSavedData();
      ListTag ragdollList = tag.getList("Ragdolls", Tag.TAG_COMPOUND);
      for (int i = 0; i < ragdollList.size(); i++) {
         CompoundTag ragdollTag = ragdollList.getCompound(i);
         if (!ragdollTag.hasUUID("Root")) {
            continue;
         }

         UUID rootId = ragdollTag.getUUID("Root");
         Map<BodyPart, UUID> parts = new EnumMap<>(BodyPart.class);
         ListTag partList = ragdollTag.getList("Parts", Tag.TAG_COMPOUND);
         for (int partIndex = 0; partIndex < partList.size(); partIndex++) {
            CompoundTag partTag = partList.getCompound(partIndex);
            if (!partTag.hasUUID("SubLevel")) {
               continue;
            }

            BodyPart bodyPart = BodyPart.byName(partTag.getString("BodyPart"));
            parts.put(bodyPart, partTag.getUUID("SubLevel"));
         }

         if (parts.containsKey(BodyPart.TORSO)) {
            data.ragdolls.put(rootId, parts);
            data.ragdollLimbs.put(rootId, loadLimbOptions(ragdollTag));
         }
      }

      return data;
   }

   private static RagdollLimbOptions loadLimbOptions(CompoundTag ragdollTag) {
      if (!ragdollTag.contains("Limbs")) return RagdollLimbOptions.defaults();
      ListTag limbList = ragdollTag.getList("Limbs", Tag.TAG_COMPOUND);
      RagdollLimbOptions.Builder builder = RagdollLimbOptions.builder();
      for (int i = 0; i < limbList.size(); i++) {
         CompoundTag limbTag = limbList.getCompound(i);
         BodyPart part = BodyPart.byName(limbTag.getString("BodyPart"));
         RagdollLimbConfig.Builder cfg = RagdollLimbConfig.builder();
         if (limbTag.contains("Pitch")) cfg.pitch(limbTag.getDouble("Pitch"));
         if (limbTag.contains("Yaw")) cfg.yaw(limbTag.getDouble("Yaw"));
         if (limbTag.contains("Roll")) cfg.roll(limbTag.getDouble("Roll"));
         if (limbTag.contains("InitialPitch") || limbTag.contains("InitialYaw") || limbTag.contains("InitialRoll")) {
            cfg.initialRotation(
               limbTag.contains("InitialPitch") ? limbTag.getDouble("InitialPitch") : 0.0,
               limbTag.contains("InitialYaw") ? limbTag.getDouble("InitialYaw") : 0.0,
               limbTag.contains("InitialRoll") ? limbTag.getDouble("InitialRoll") : 0.0
            );
         }
         if (limbTag.contains("Right") || limbTag.contains("Up") || limbTag.contains("Forward")) {
            cfg.offset(
               limbTag.contains("Right") ? limbTag.getDouble("Right") : 0.0,
               limbTag.contains("Up") ? limbTag.getDouble("Up") : 0.0,
               limbTag.contains("Forward") ? limbTag.getDouble("Forward") : 0.0
            );
         }
         if (limbTag.contains("Stiffness")) cfg.stiffness(limbTag.getDouble("Stiffness"));
         if (limbTag.contains("Damping")) cfg.damping(limbTag.getDouble("Damping"));
         builder.limb(part, cfg);
      }
      return builder.build();
   }

   public void saveRagdoll(UUID rootSubLevelId, Map<BodyPart, UUID> partSubLevelIds, RagdollLimbOptions limbs) {
      this.ragdolls.put(rootSubLevelId, immutableCopy(partSubLevelIds));
      this.ragdollLimbs.put(rootSubLevelId, limbs);
      this.setDirty();
   }

   public void removeRagdoll(UUID rootSubLevelId) {
      boolean removed = this.ragdolls.remove(rootSubLevelId) != null;
      this.ragdollLimbs.remove(rootSubLevelId);
      if (removed) this.setDirty();
   }

   public Map<BodyPart, UUID> ragdoll(UUID rootSubLevelId) {
      Map<BodyPart, UUID> parts = this.ragdolls.get(rootSubLevelId);
      return parts == null ? Map.of() : Collections.unmodifiableMap(parts);
   }

   public RagdollLimbOptions ragdollLimbs(UUID rootSubLevelId) {
      RagdollLimbOptions limbs = this.ragdollLimbs.get(rootSubLevelId);
      return limbs == null ? RagdollLimbOptions.defaults() : limbs;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      ListTag ragdollList = new ListTag();
      this.ragdolls.forEach((rootId, parts) -> {
         CompoundTag ragdollTag = new CompoundTag();
         ragdollTag.putUUID("Root", rootId);
         ListTag partList = new ListTag();
         parts.forEach((bodyPart, partId) -> {
            CompoundTag partTag = new CompoundTag();
            partTag.putString("BodyPart", bodyPart.name());
            partTag.putUUID("SubLevel", partId);
            partList.add(partTag);
         });
         ragdollTag.put("Parts", partList);
         RagdollLimbOptions limbs = this.ragdollLimbs.get(rootId);
         if (limbs != null && !limbs.isEmpty()) {
            ListTag limbList = new ListTag();
            for (BodyPart part : BodyPart.values()) {
               RagdollLimbConfig cfg = limbs.get(part);
               if (cfg == null) continue;
               CompoundTag limbTag = new CompoundTag();
               limbTag.putString("BodyPart", part.getSerializedName());
               cfg.pitchDegrees().ifPresent(v -> limbTag.putDouble("Pitch", v));
               cfg.yawDegrees().ifPresent(v -> limbTag.putDouble("Yaw", v));
               cfg.rollDegrees().ifPresent(v -> limbTag.putDouble("Roll", v));
               cfg.initialPitchDegrees().ifPresent(v -> limbTag.putDouble("InitialPitch", v));
               cfg.initialYawDegrees().ifPresent(v -> limbTag.putDouble("InitialYaw", v));
               cfg.initialRollDegrees().ifPresent(v -> limbTag.putDouble("InitialRoll", v));
               cfg.rightOffset().ifPresent(v -> limbTag.putDouble("Right", v));
               cfg.upOffset().ifPresent(v -> limbTag.putDouble("Up", v));
               cfg.forwardOffset().ifPresent(v -> limbTag.putDouble("Forward", v));
               cfg.angularStiffness().ifPresent(v -> limbTag.putDouble("Stiffness", v));
               cfg.angularDamping().ifPresent(v -> limbTag.putDouble("Damping", v));
               limbList.add(limbTag);
            }
            if (!limbList.isEmpty()) ragdollTag.put("Limbs", limbList);
         }
         ragdollList.add(ragdollTag);
      });
      tag.put("Ragdolls", ragdollList);
      return tag;
   }

   private static Map<BodyPart, UUID> immutableCopy(Map<BodyPart, UUID> partSubLevelIds) {
      Map<BodyPart, UUID> copy = new EnumMap<>(BodyPart.class);
      copy.putAll(partSubLevelIds);
      return copy;
   }
}
