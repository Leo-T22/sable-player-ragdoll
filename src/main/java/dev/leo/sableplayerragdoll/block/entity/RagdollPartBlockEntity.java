package dev.leo.sableplayerragdoll.block.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.leo.sableplayerragdoll.physics.RagdollControlHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.free.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RagdollPartBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
   private static final double TORSO_GRAB_STIFFNESS = 70.0;
   private static final double TORSO_GRAB_DAMPING = 12.0;
   private static final double TORSO_GRAB_ANGULAR_DAMPING = 2.0;
   private static final double TORSO_GRAB_MAX_FORCE = 180.0;
   private static final double TORSO_GRAB_MAX_RANGE = 5.0;
   private BodyPart bodyPart = BodyPart.TORSO;
   @Nullable
   private UUID skinUuid;
   private String skinName = "Player";
   private String skinTextures = "";
   private String skinTexturesSignature = "";
   private ItemStack mainHandItem = ItemStack.EMPTY;
   private ItemStack offHandItem = ItemStack.EMPTY;
   private ItemStack headItem = ItemStack.EMPTY;
   private ItemStack chestItem = ItemStack.EMPTY;
   private ItemStack legsItem = ItemStack.EMPTY;
   private ItemStack feetItem = ItemStack.EMPTY;
   private final Map<UUID, TorsoGrabConstraint> torsoGrabbers = new HashMap<>();

   public RagdollPartBlockEntity(BlockPos pos, BlockState state) {
      super(RagdollPartBlockEntities.ragdollPart(), pos, state);
   }

   public void configure(BodyPart bodyPart, GameProfile profile) {
      this.bodyPart = bodyPart;
      this.skinUuid = profile.getId();
      this.skinName = profile.getName() == null || profile.getName().isBlank() ? "Player" : profile.getName();
      Property textures = profile.getProperties().get("textures").stream().findFirst().orElse(null);
      this.skinTextures = textures == null ? "" : textures.value();
      this.skinTexturesSignature = textures == null || textures.signature() == null ? "" : textures.signature();
      this.setChanged();
   }

   public void configure(BodyPart bodyPart, Player player) {
      this.configure(bodyPart, player.getGameProfile());
      this.mainHandItem = player.getItemBySlot(EquipmentSlot.MAINHAND).copy();
      this.offHandItem = player.getItemBySlot(EquipmentSlot.OFFHAND).copy();
      this.headItem = player.getItemBySlot(EquipmentSlot.HEAD).copy();
      this.chestItem = player.getItemBySlot(EquipmentSlot.CHEST).copy();
      this.legsItem = player.getItemBySlot(EquipmentSlot.LEGS).copy();
      this.feetItem = player.getItemBySlot(EquipmentSlot.FEET).copy();
      this.setChanged();
   }

   public BodyPart bodyPart() {
      return this.bodyPart;
   }

   public boolean canBeGrabbedAsTorso() {
      return this.bodyPart == BodyPart.TORSO;
   }

   public void startTorsoGrab(UUID playerId, float desiredRange) {
      if (!this.canBeGrabbedAsTorso()) {
         return;
      }

      this.torsoGrabbers.compute(playerId, (unused, existing) -> {
         if (existing != null) {
            existing.setDesiredRange(desiredRange);
            return existing;
         }

         return new TorsoGrabConstraint(playerId, desiredRange);
      });
      this.setChanged();
   }

   public void stopTorsoGrab(UUID playerId) {
      TorsoGrabConstraint constraint = this.torsoGrabbers.remove(playerId);
      if (constraint != null) {
         constraint.removeJoint();
         this.setChanged();
      }
   }

   @Override
   public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
      if (!this.canBeGrabbedAsTorso()) {
         this.removeAllTorsoGrabbers();
         return;
      }

      this.checkTorsoGrabbers();
      for (TorsoGrabConstraint constraint : this.torsoGrabbers.values()) {
         constraint.physicsTick(subLevel);
      }

      RagdollControlHelper.apply(subLevel, handle, timeStep);
   }

   private void checkTorsoGrabbers() {
      if (this.level == null || this.torsoGrabbers.isEmpty()) {
         return;
      }

      for (Iterator<Map.Entry<UUID, TorsoGrabConstraint>> it = this.torsoGrabbers.entrySet().iterator(); it.hasNext();) {
         Map.Entry<UUID, TorsoGrabConstraint> entry = it.next();
         Player player = this.level.getPlayerByUUID(entry.getKey());
         if (player == null || player.isDeadOrDying() || player.isSpectator()) {
            entry.getValue().removeJoint();
            it.remove();
            this.setChanged();
         }
      }
   }

   private void removeAllTorsoGrabbers() {
      if (!this.torsoGrabbers.isEmpty()) {
         this.torsoGrabbers.values().forEach(TorsoGrabConstraint::removeJoint);
         this.torsoGrabbers.clear();
         this.setChanged();
      }
   }

   @Override
   public void setRemoved() {
      super.setRemoved();
      this.removeAllTorsoGrabbers();
   }

   public Vector3d torsoGrabCenter() {
      return JOMLConversion.atCenterOf(this.getBlockPos());
   }

   public GameProfile skinProfile() {
      GameProfile profile = new GameProfile(this.skinUuid != null ? this.skinUuid : UUID.nameUUIDFromBytes(this.skinName.getBytes()), this.skinName);
      if (!this.skinTextures.isBlank()) {
         profile.getProperties()
            .put("textures", this.skinTexturesSignature.isBlank() ? new Property("textures", this.skinTextures) : new Property("textures", this.skinTextures, this.skinTexturesSignature));
      }

      return profile;
   }

   public ItemStack itemBySlot(EquipmentSlot slot) {
      return switch (slot) {
         case MAINHAND -> this.mainHandItem;
         case OFFHAND -> this.offHandItem;
         case HEAD -> this.headItem;
         case CHEST -> this.chestItem;
         case LEGS -> this.legsItem;
         case FEET -> this.feetItem;
         default -> ItemStack.EMPTY;
      };
   }

   @Override
   protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
      super.saveAdditional(tag, registries);
      tag.putString("BodyPart", this.bodyPart.name());
      if (this.skinUuid != null) {
         tag.putUUID("SkinUuid", this.skinUuid);
      }

      tag.putString("SkinName", this.skinName);
      tag.putString("SkinTextures", this.skinTextures);
      tag.putString("SkinTexturesSignature", this.skinTexturesSignature);
      saveItem(tag, registries, "MainHandItem", this.mainHandItem);
      saveItem(tag, registries, "OffHandItem", this.offHandItem);
      saveItem(tag, registries, "HeadItem", this.headItem);
      saveItem(tag, registries, "ChestItem", this.chestItem);
      saveItem(tag, registries, "LegsItem", this.legsItem);
      saveItem(tag, registries, "FeetItem", this.feetItem);
   }

   @Override
   protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
      super.loadAdditional(tag, registries);
      this.bodyPart = BodyPart.byName(tag.getString("BodyPart"));
      this.skinUuid = tag.hasUUID("SkinUuid") ? tag.getUUID("SkinUuid") : null;
      this.skinName = tag.getString("SkinName").isBlank() ? "Player" : tag.getString("SkinName");
      this.skinTextures = tag.getString("SkinTextures");
      this.skinTexturesSignature = tag.getString("SkinTexturesSignature");
      this.mainHandItem = loadItem(tag, registries, "MainHandItem");
      this.offHandItem = loadItem(tag, registries, "OffHandItem");
      this.headItem = loadItem(tag, registries, "HeadItem");
      this.chestItem = loadItem(tag, registries, "ChestItem");
      this.legsItem = loadItem(tag, registries, "LegsItem");
      this.feetItem = loadItem(tag, registries, "FeetItem");
   }

   @Override
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   @Override
   public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
      return this.saveCustomOnly(registries);
   }

   private static void saveItem(CompoundTag tag, HolderLookup.Provider registries, String key, ItemStack stack) {
      if (!stack.isEmpty()) {
         tag.put(key, stack.save(registries));
      }
   }

   private static ItemStack loadItem(CompoundTag tag, HolderLookup.Provider registries, String key) {
      return tag.contains(key) ? ItemStack.parse(registries, tag.getCompound(key)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
   }

   private final class TorsoGrabConstraint {
      private final UUID playerId;
      private float desiredRange;
      @Nullable
      private PhysicsConstraintHandle constraintHandle;

      private TorsoGrabConstraint(UUID playerId, float desiredRange) {
         this.playerId = playerId;
         this.setDesiredRange(desiredRange);
      }

      private void physicsTick(ServerSubLevel subLevel) {
         this.removeJoint();
         if (RagdollPartBlockEntity.this.level == null) {
            return;
         }

         Player player = RagdollPartBlockEntity.this.level.getPlayerByUUID(this.playerId);
         if (player == null || player.isDeadOrDying() || player.isSpectator()) {
            return;
         }

         SubLevel standingSubLevel = Sable.HELPER.getTrackingSubLevel(player);
         if (standingSubLevel == subLevel) {
            return;
         }

         Vector3d constraintGoal = JOMLConversion.toJOML(player.getEyePosition().add(player.getLookAngle().scale(Math.max(2.0, this.desiredRange))));
         Vector3d constraintPosition = RagdollPartBlockEntity.this.torsoGrabCenter();
         double validRange = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue() + 2.0;
         double currentDistance = Sable.HELPER.distanceSquaredWithSubLevels(RagdollPartBlockEntity.this.level, constraintGoal, constraintPosition);
         if (Mth.equal(-1.0F, this.desiredRange) || currentDistance > validRange * validRange) {
            return;
         }

         ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
         if (container == null) {
            return;
         }

         SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
         this.constraintHandle = physicsSystem.getPipeline()
            .addConstraint(null, subLevel, new FreeConstraintConfiguration(constraintGoal, constraintPosition, new Quaterniond()));

         for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            this.constraintHandle.setMotor(axis, 0.0, TORSO_GRAB_STIFFNESS, TORSO_GRAB_DAMPING, true, TORSO_GRAB_MAX_FORCE);
         }

         for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
            this.constraintHandle.setMotor(axis, 0.0, 0.0, TORSO_GRAB_ANGULAR_DAMPING, true, TORSO_GRAB_MAX_FORCE);
         }
      }

      private void setDesiredRange(float desiredRange) {
         this.desiredRange = (float)Math.clamp(desiredRange, 1.0F, TORSO_GRAB_MAX_RANGE);
      }

      private void removeJoint() {
         if (this.constraintHandle != null) {
            this.constraintHandle.remove();
            this.constraintHandle = null;
         }
      }
   }

   public enum BodyPart implements StringRepresentable {
      HEAD("head"),
      TORSO("torso"),
      LEFT_ARM("left_arm"),
      RIGHT_ARM("right_arm"),
      LEFT_LEG("left_leg"),
      RIGHT_LEG("right_leg");

      private final String serializedName;

      BodyPart(String serializedName) {
         this.serializedName = serializedName;
      }

      public double renderYOffset() {
         return switch (this) {
            case HEAD -> 0.0;
            case TORSO -> 0.65;
            case LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG -> 0.45;
         };
      }

      public float renderScale() {
         return switch (this) {
            case HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG -> 0.9375F;
         };
      }

      @Override
      public String getSerializedName() {
         return this.serializedName;
      }

      private static BodyPart byName(String name) {
         for (BodyPart part : values()) {
            if (part.name().equals(name) || part.serializedName.equals(name)) {
               return part;
            }
         }

         return TORSO;
      }
   }
}
