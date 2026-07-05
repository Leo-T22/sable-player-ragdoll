package dev.leo.sableplayerragdoll.entity;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public final class RagdollDollEntity extends LivingEntity implements IEntityWithComplexSpawn {
   private static final EntityDataAccessor<Optional<UUID>> DATA_SKIN_UUID = SynchedEntityData.defineId(
      RagdollDollEntity.class, EntityDataSerializers.OPTIONAL_UUID
   );
   private static final EntityDataAccessor<String> DATA_SKIN_NAME = SynchedEntityData.defineId(RagdollDollEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> DATA_SKIN_TEXTURES = SynchedEntityData.defineId(RagdollDollEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> DATA_SKIN_TEXTURES_SIGNATURE = SynchedEntityData.defineId(
      RagdollDollEntity.class, EntityDataSerializers.STRING
   );
   private static final EntityDataAccessor<Integer> DATA_BODY_PART = SynchedEntityData.defineId(RagdollDollEntity.class, EntityDataSerializers.INT);
   private final java.util.EnumMap<EquipmentSlot, ItemStack> equipment = new java.util.EnumMap<>(EquipmentSlot.class);

   public RagdollDollEntity(EntityType<? extends RagdollDollEntity> entityType, Level level) {
      super(entityType, level);
   }

   public RagdollDollEntity(Level level) {
      this(RagdollSeatEntities.ragdollDoll(), level);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return LivingEntity.createLivingAttributes()
         .add(Attributes.MAX_HEALTH, 0.0)
         .add(Attributes.MOVEMENT_SPEED, 0.0)
         .add(Attributes.STEP_HEIGHT, 0.0);
   }

   public void setSkinProfile(GameProfile profile) {
      this.entityData.set(DATA_SKIN_UUID, Optional.ofNullable(profile.getId()));
      this.entityData.set(DATA_SKIN_NAME, profile.getName() == null ? "" : profile.getName());
      Property textures = profile.getProperties().get("textures").stream().findFirst().orElse(null);
      this.entityData.set(DATA_SKIN_TEXTURES, textures == null ? "" : textures.value());
      this.entityData.set(DATA_SKIN_TEXTURES_SIGNATURE, textures == null || textures.signature() == null ? "" : textures.signature());
   }

   public GameProfile getSkinProfile() {
      Optional<UUID> uuid = this.entityData.get(DATA_SKIN_UUID);
      String name = this.entityData.get(DATA_SKIN_NAME);
      String textures = this.entityData.get(DATA_SKIN_TEXTURES);
      String signature = this.entityData.get(DATA_SKIN_TEXTURES_SIGNATURE);
      GameProfile profile = new GameProfile(uuid.orElse(this.getUUID()), name.isBlank() ? "Player" : name);
      if (!textures.isBlank()) {
         profile.getProperties().put("textures", signature.isBlank() ? new Property("textures", textures) : new Property("textures", textures, signature));
      }
      return profile;
   }

   public void setBodyPart(BodyPart bodyPart) {
      this.entityData.set(DATA_BODY_PART, bodyPart.id);
   }

   public BodyPart getBodyPart() {
      return BodyPart.byId(this.entityData.get(DATA_BODY_PART));
   }

   @Override
   public boolean canCollideWith(net.minecraft.world.entity.Entity entity) {
      return !(entity instanceof RagdollDollEntity) && super.canCollideWith(entity);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_SKIN_UUID, Optional.empty());
      builder.define(DATA_SKIN_NAME, "");
      builder.define(DATA_SKIN_TEXTURES, "");
      builder.define(DATA_SKIN_TEXTURES_SIGNATURE, "");
      builder.define(DATA_BODY_PART, BodyPart.TORSO.id);
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      if (tag.hasUUID("SkinUuid")) {
         this.entityData.set(DATA_SKIN_UUID, Optional.of(tag.getUUID("SkinUuid")));
      }
      this.entityData.set(DATA_SKIN_NAME, tag.getString("SkinName"));
      this.entityData.set(DATA_SKIN_TEXTURES, tag.getString("SkinTextures"));
      this.entityData.set(DATA_SKIN_TEXTURES_SIGNATURE, tag.getString("SkinTexturesSignature"));
      this.entityData.set(DATA_BODY_PART, tag.getInt("BodyPart"));
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      this.entityData.get(DATA_SKIN_UUID).ifPresent(uuid -> tag.putUUID("SkinUuid", uuid));
      tag.putString("SkinName", this.entityData.get(DATA_SKIN_NAME));
      tag.putString("SkinTextures", this.entityData.get(DATA_SKIN_TEXTURES));
      tag.putString("SkinTexturesSignature", this.entityData.get(DATA_SKIN_TEXTURES_SIGNATURE));
      tag.putInt("BodyPart", this.entityData.get(DATA_BODY_PART));
   }

   @Override
   public Iterable<ItemStack> getArmorSlots() {
      return ImmutableList.of(
         this.getItemBySlot(EquipmentSlot.FEET),
         this.getItemBySlot(EquipmentSlot.LEGS),
         this.getItemBySlot(EquipmentSlot.CHEST),
         this.getItemBySlot(EquipmentSlot.HEAD)
      );
   }

   @Override
   public ItemStack getItemBySlot(EquipmentSlot slot) {
      return this.equipment.getOrDefault(slot, ItemStack.EMPTY);
   }

   @Override
   public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
      this.equipment.put(slot, stack.copy());
   }

   @Override
   public HumanoidArm getMainArm() {
      return HumanoidArm.RIGHT;
   }

   @Override
   public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
   }

   @Override
   public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
   }

   public enum BodyPart {
      TORSO(0),
      HEAD(1),
      LEFT_ARM(2),
      RIGHT_ARM(3),
      LEFT_LEG(4),
      RIGHT_LEG(5);

      private static final BodyPart[] VALUES = values();
      public final int id;

      BodyPart(int id) {
         this.id = id;
      }

      public static BodyPart byId(int id) {
         return id >= 0 && id < VALUES.length ? VALUES[id] : TORSO;
      }
   }
}
