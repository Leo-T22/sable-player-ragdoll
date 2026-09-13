package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.api.RagdollLimbConfig;
import dev.leo.sableplayerragdoll.api.RagdollLimbOptions;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public final class RagdollAssemblyData {
   private RagdollAssemblyData() {}

   public static void write(ServerLevel level, UUID owner, RagdollLimbOptions limbs) {
      CompoundTag record = encode(limbs);
      var cached = RagdollRelationships.currentMembers(level, owner);
      for (var be : cached == null ? RagdollBlockOwnership.loadedBlocks(level) : cached) {
         if (!(be instanceof RagdollPartBlockEntity)) continue;
         var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
         if (!owner.equals(identity.owner())) continue;
         identity.assembly(record);
         be.setChanged();
      }
   }

   public static Map<BodyPart, UUID> parts(ServerLevel level, UUID owner) {
      Map<BodyPart, UUID> result = new EnumMap<>(BodyPart.class);
      var cached = RagdollRelationships.currentMembers(level, owner);
      for (var be : cached == null ? RagdollBlockOwnership.loadedBlocks(level) : cached) {
         if (!(be instanceof RagdollPartBlockEntity part)) continue;
         var identity = part.ragdollIdentity();
         if (owner.equals(identity.owner()) && !identity.severed() && identity.limb() != null)
            result.put(part.bodyPart(), identity.limb());
      }
      return result;
   }

   public static RagdollLimbOptions limbs(ServerLevel level, UUID owner) {
      CompoundTag record = record(level, owner);
      return record == null ? RagdollLimbOptions.defaults() : decodeLimbs(record);
   }

   public static @Nullable UUID ownerForLimb(ServerLevel level, UUID limb) {
      if (limb == null) return null;
      for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
         if (!(be instanceof RagdollPartBlockEntity)) continue;
         var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
         if (limb.equals(identity.limb()) && !identity.severed()) return identity.owner();
      }
      return null;
   }

   private static @Nullable CompoundTag record(ServerLevel level, UUID owner) {
      var cached = RagdollRelationships.currentMembers(level, owner);
      for (var be : cached == null ? RagdollBlockOwnership.loadedBlocks(level) : cached) {
         if (!(be instanceof RagdollPartBlockEntity)) continue;
         var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
         if (owner.equals(identity.owner())) {
            return identity.assembly().copy();
         }
      }
      return null;
   }

   private static RagdollLimbOptions decodeLimbs(CompoundTag record) {
      if (!record.contains("Limbs")) return RagdollLimbOptions.defaults();
      RagdollLimbOptions.Builder builder = RagdollLimbOptions.builder();
      for (Tag entry : record.getList("Limbs", Tag.TAG_COMPOUND)) {
         CompoundTag limbTag = (CompoundTag) entry;
         RagdollLimbConfig.Builder config = RagdollLimbConfig.builder();
         if (limbTag.contains("Pitch")) config.pitch(limbTag.getDouble("Pitch"));
         if (limbTag.contains("Yaw")) config.yaw(limbTag.getDouble("Yaw"));
         if (limbTag.contains("Roll")) config.roll(limbTag.getDouble("Roll"));
         if (limbTag.contains("InitialPitch") || limbTag.contains("InitialYaw") || limbTag.contains("InitialRoll")) {
            config.initialRotation(
               limbTag.contains("InitialPitch") ? limbTag.getDouble("InitialPitch") : 0.0,
               limbTag.contains("InitialYaw") ? limbTag.getDouble("InitialYaw") : 0.0,
               limbTag.contains("InitialRoll") ? limbTag.getDouble("InitialRoll") : 0.0
            );
         }
         if (limbTag.contains("Right") || limbTag.contains("Up") || limbTag.contains("Forward")) {
            config.offset(
               limbTag.contains("Right") ? limbTag.getDouble("Right") : 0.0,
               limbTag.contains("Up") ? limbTag.getDouble("Up") : 0.0,
               limbTag.contains("Forward") ? limbTag.getDouble("Forward") : 0.0
            );
         }
         if (limbTag.contains("Stiffness")) config.stiffness(limbTag.getDouble("Stiffness"));
         if (limbTag.contains("Damping")) config.damping(limbTag.getDouble("Damping"));
         builder.limb(BodyPart.byName(limbTag.getString("BodyPart")), config);
      }
      return builder.build();
   }

   private static CompoundTag encode(RagdollLimbOptions limbs) {
      CompoundTag record = new CompoundTag();
      if (limbs != null && !limbs.isEmpty()) {
         ListTag limbList = new ListTag();
         for (BodyPart part : BodyPart.values()) {
            RagdollLimbConfig config = limbs.get(part);
            if (config == null) continue;
            CompoundTag limbTag = new CompoundTag();
            limbTag.putString("BodyPart", part.getSerializedName());
            config.pitchDegrees().ifPresent(value -> limbTag.putDouble("Pitch", value));
            config.yawDegrees().ifPresent(value -> limbTag.putDouble("Yaw", value));
            config.rollDegrees().ifPresent(value -> limbTag.putDouble("Roll", value));
            config.initialPitchDegrees().ifPresent(value -> limbTag.putDouble("InitialPitch", value));
            config.initialYawDegrees().ifPresent(value -> limbTag.putDouble("InitialYaw", value));
            config.initialRollDegrees().ifPresent(value -> limbTag.putDouble("InitialRoll", value));
            config.rightOffset().ifPresent(value -> limbTag.putDouble("Right", value));
            config.upOffset().ifPresent(value -> limbTag.putDouble("Up", value));
            config.forwardOffset().ifPresent(value -> limbTag.putDouble("Forward", value));
            config.angularStiffness().ifPresent(value -> limbTag.putDouble("Stiffness", value));
            config.angularDamping().ifPresent(value -> limbTag.putDouble("Damping", value));
            limbList.add(limbTag);
         }
         if (!limbList.isEmpty()) record.put("Limbs", limbList);
      }
      return record;
   }
}
