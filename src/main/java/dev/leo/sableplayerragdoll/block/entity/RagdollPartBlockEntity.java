package dev.leo.sableplayerragdoll.block.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.leo.sableplayerragdoll.RagdollGrabCallbacks;
import dev.leo.sableplayerragdoll.physics.RagdollAssemblyHelper;
import dev.leo.sableplayerragdoll.physics.RagdollControlHelper;
import dev.leo.sableplayerragdoll.physics.RagdollRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.leo.sableplayerragdoll.physics.SableConstraintCompat;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
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
   private static final double GRAB_STIFFNESS = 500.0;
   private static final double GRAB_DAMPING = 50.0;
   private static final double GRAB_MAX_FORCE = 200.0;
   private static final double GRAB_HOLD_DISTANCE = 1.0;
   private static final double GRAB_ANCHOR_Y_OFFSET = -0.6;
   private static final double GRAB_MAX_DISTANCE = 4.0;
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
   private float maxHealth = 20f;
   private boolean corpse;
   private Map<String, List<ItemStack>> curiosItems = new LinkedHashMap<>();
   private Map<String, List<ItemStack>> curiosCosmeticItems = new LinkedHashMap<>();
   private Map<String, List<Boolean>> curiosRenderOptions = new LinkedHashMap<>();
   private Map<String, List<ItemStack>> accessoriesItems = new LinkedHashMap<>();
   private Map<String, List<ItemStack>> accessoriesCosmeticItems = new LinkedHashMap<>();
   private Map<String, List<Boolean>> accessoriesRenderOptions = new LinkedHashMap<>();
   private final Map<UUID, GrabConstraint> grabbers = new HashMap<>();

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
      this.maxHealth = (float) player.getMaxHealth();
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

   public float maxHealth() {
      return this.maxHealth;
   }

   public boolean isCorpse() {
      return this.corpse;
   }

   public void setCorpse(boolean corpse) {
      if (this.corpse != corpse) {
         this.corpse = corpse;
         this.setChanged();
      }
   }

   public void startGrab(UUID playerId) {
      this.grabbers.computeIfAbsent(playerId, GrabConstraint::new);
      this.setChanged();
   }

   public void stopGrab(UUID playerId) {
      GrabConstraint constraint = this.grabbers.remove(playerId);
      if (constraint != null) {
         constraint.removeJoint();
         this.setChanged();
      }
   }

   @Override
   public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
      if (this.bodyPart == BodyPart.TORSO && subLevel.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
         RagdollRegistry.tryRestoreOnLoad(serverLevel, subLevel);
      }
      this.checkGrabbers();

      for (GrabConstraint constraint : this.grabbers.values()) {
         constraint.physicsTick(subLevel);
      }

      if (this.bodyPart == BodyPart.TORSO) {
         RagdollControlHelper.apply(subLevel, handle, timeStep);
      } else if (this.bodyPart == BodyPart.LEFT_ARM || this.bodyPart == BodyPart.RIGHT_ARM) {
         RagdollControlHelper.applyArm(subLevel, handle, timeStep, this.bodyPart);
         RagdollControlHelper.applyArmGrab(subLevel, this.bodyPart);
      }
   }

   private void checkGrabbers() {
      if (this.level == null || this.grabbers.isEmpty()) {
         return;
      }

      Vector3d center = this.grabCenter();
      for (Iterator<Map.Entry<UUID, GrabConstraint>> it = this.grabbers.entrySet().iterator(); it.hasNext();) {
         Map.Entry<UUID, GrabConstraint> entry = it.next();
         Player player = this.level.getPlayerByUUID(entry.getKey());
         boolean invalid = player == null || player.isDeadOrDying() || player.isSpectator();
         if (!invalid) {
            double distanceSq = Sable.HELPER.distanceSquaredWithSubLevels(
               this.level, JOMLConversion.toJOML(player.getEyePosition()), center);
            invalid = distanceSq > GRAB_MAX_DISTANCE * GRAB_MAX_DISTANCE;
         }
         if (invalid) {
            entry.getValue().removeJoint();
            it.remove();
            this.notifyReleased(entry.getKey());
            this.setChanged();
         }
      }
   }

   private void removeAllGrabbers() {
      if (!this.grabbers.isEmpty()) {
         for (Map.Entry<UUID, GrabConstraint> entry : this.grabbers.entrySet()) {
            entry.getValue().removeJoint();
            this.notifyReleased(entry.getKey());
         }
         this.grabbers.clear();
         this.setChanged();
      }
   }

   private void notifyReleased(UUID playerId) {
      if (this.level != null && this.level.getPlayerByUUID(playerId) instanceof ServerPlayer serverPlayer) {
         RagdollGrabCallbacks.notifyReleased(serverPlayer);
      }
   }

   @Override
   public void setRemoved() {
      super.setRemoved();
      this.removeAllGrabbers();
   }

   public Vector3d grabCenter() {
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

   public void setItemForSlot(EquipmentSlot slot, ItemStack stack) {
      switch (slot) {
         case MAINHAND -> this.mainHandItem = stack.copy();
         case OFFHAND -> this.offHandItem = stack.copy();
         case HEAD -> this.headItem = stack.copy();
         case CHEST -> this.chestItem = stack.copy();
         case LEGS -> this.legsItem = stack.copy();
         case FEET -> this.feetItem = stack.copy();
         default -> { return; }
      }
      this.setChanged();
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

   public void setCurioItems(String slotId, List<ItemStack> stacks) {
      if (stacks == null || stacks.stream().allMatch(ItemStack::isEmpty)) {
         this.curiosItems.remove(slotId);
      } else {
         this.curiosItems.put(slotId, Collections.unmodifiableList(new ArrayList<>(stacks)));
      }
      this.setChanged();
   }

   public Map<String, List<ItemStack>> getCurioItems() {
      return Collections.unmodifiableMap(this.curiosItems);
   }

   public void setCurioCosmeticItems(String slotId, List<ItemStack> stacks) {
      if (stacks == null || stacks.stream().allMatch(ItemStack::isEmpty)) {
         this.curiosCosmeticItems.remove(slotId);
      } else {
         this.curiosCosmeticItems.put(slotId, Collections.unmodifiableList(new ArrayList<>(stacks)));
      }
      this.setChanged();
   }

   public Map<String, List<ItemStack>> getCurioCosmeticItems() {
      return Collections.unmodifiableMap(this.curiosCosmeticItems);
   }

   public void setCurioRenderOptions(String slotId, List<Boolean> options) {
      if (options == null || options.isEmpty()) {
         this.curiosRenderOptions.remove(slotId);
      } else {
         this.curiosRenderOptions.put(slotId, List.copyOf(options));
      }
      this.setChanged();
   }

   public Map<String, List<Boolean>> getCurioRenderOptions() {
      return Collections.unmodifiableMap(this.curiosRenderOptions);
   }

   public boolean hasCurioItems() {
      return !this.curiosItems.isEmpty() || !this.curiosCosmeticItems.isEmpty() || !this.curiosRenderOptions.isEmpty();
   }

   public void setAccessoriesItems(String slotName, List<ItemStack> stacks) {
      if (stacks == null || stacks.stream().allMatch(ItemStack::isEmpty)) {
         this.accessoriesItems.remove(slotName);
      } else {
         this.accessoriesItems.put(slotName, Collections.unmodifiableList(new ArrayList<>(stacks)));
      }
      this.setChanged();
   }

   public void setAccessoriesCosmeticItems(String slotName, List<ItemStack> stacks) {
      if (stacks == null || stacks.stream().allMatch(ItemStack::isEmpty)) {
         this.accessoriesCosmeticItems.remove(slotName);
      } else {
         this.accessoriesCosmeticItems.put(slotName, Collections.unmodifiableList(new ArrayList<>(stacks)));
      }
      this.setChanged();
   }

   public void setAccessoriesRenderOptions(String slotName, List<Boolean> options) {
      if (options == null || options.isEmpty()) {
         this.accessoriesRenderOptions.remove(slotName);
      } else {
         this.accessoriesRenderOptions.put(slotName, List.copyOf(options));
      }
      this.setChanged();
   }

   public Map<String, List<ItemStack>> getAccessoriesItems() {
      return Collections.unmodifiableMap(this.accessoriesItems);
   }

   public Map<String, List<ItemStack>> getAccessoriesCosmeticItems() {
      return Collections.unmodifiableMap(this.accessoriesCosmeticItems);
   }

   public Map<String, List<Boolean>> getAccessoriesRenderOptions() {
      return Collections.unmodifiableMap(this.accessoriesRenderOptions);
   }

   public boolean hasAccessoriesItems() {
      return !this.accessoriesItems.isEmpty() || !this.accessoriesCosmeticItems.isEmpty() || !this.accessoriesRenderOptions.isEmpty();
   }

   @Override
   protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
      super.saveAdditional(tag, registries);
      tag.putString("BodyPart", this.bodyPart.name());
      if (this.skinUuid != null) {
         tag.putUUID("SkinUuid", this.skinUuid);
      }

      tag.putFloat("MaxHealth", this.maxHealth);
      tag.putBoolean("Corpse", this.corpse);
      tag.putString("SkinName", this.skinName);
      tag.putString("SkinTextures", this.skinTextures);
      tag.putString("SkinTexturesSignature", this.skinTexturesSignature);
      saveItem(tag, registries, "MainHandItem", this.mainHandItem);
      saveItem(tag, registries, "OffHandItem", this.offHandItem);
      saveItem(tag, registries, "HeadItem", this.headItem);
      saveItem(tag, registries, "ChestItem", this.chestItem);
      saveItem(tag, registries, "LegsItem", this.legsItem);
      saveItem(tag, registries, "FeetItem", this.feetItem);
      if (!this.curiosItems.isEmpty()) tag.put("CurioItems", saveSlotMap(this.curiosItems, registries));
      if (!this.curiosCosmeticItems.isEmpty()) tag.put("CurioCosmeticItems", saveSlotMap(this.curiosCosmeticItems, registries));
      if (!this.curiosRenderOptions.isEmpty()) tag.put("CurioRenderOptions", saveBooleanSlotMap(this.curiosRenderOptions));
      if (!this.accessoriesItems.isEmpty()) tag.put("AccessoriesItems", saveSlotMap(this.accessoriesItems, registries));
      if (!this.accessoriesCosmeticItems.isEmpty()) tag.put("AccessoriesCosmeticItems", saveSlotMap(this.accessoriesCosmeticItems, registries));
      if (!this.accessoriesRenderOptions.isEmpty()) tag.put("AccessoriesRenderOptions", saveBooleanSlotMap(this.accessoriesRenderOptions));
   }

   @Override
   protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
      super.loadAdditional(tag, registries);
      this.bodyPart = BodyPart.byName(tag.getString("BodyPart"));
      this.maxHealth = tag.contains("MaxHealth") ? tag.getFloat("MaxHealth") : 20f;
      this.corpse = tag.getBoolean("Corpse");
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
      loadSlotMap(tag, registries, "CurioItems", this.curiosItems);
      loadSlotMap(tag, registries, "CurioCosmeticItems", this.curiosCosmeticItems);
      loadBooleanSlotMap(tag, "CurioRenderOptions", this.curiosRenderOptions);
      loadSlotMap(tag, registries, "AccessoriesItems", this.accessoriesItems);
      loadSlotMap(tag, registries, "AccessoriesCosmeticItems", this.accessoriesCosmeticItems);
      loadBooleanSlotMap(tag, "AccessoriesRenderOptions", this.accessoriesRenderOptions);
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

   private static ListTag saveSlotMap(Map<String, List<ItemStack>> slotMap, HolderLookup.Provider registries) {
      ListTag list = new ListTag();
      slotMap.forEach((slotId, stacks) -> {
         CompoundTag slotTag = new CompoundTag();
         slotTag.putString("SlotId", slotId);
         ListTag itemList = new ListTag();
         for (ItemStack stack : stacks) {
            CompoundTag itemTag = new CompoundTag();
            if (!stack.isEmpty()) itemTag.put("Item", stack.save(registries));
            itemList.add(itemTag);
         }
         slotTag.put("Stacks", itemList);
         list.add(slotTag);
      });
      return list;
   }

   private static void loadSlotMap(CompoundTag tag, HolderLookup.Provider registries, String key, Map<String, List<ItemStack>> out) {
      out.clear();
      if (!tag.contains(key, Tag.TAG_LIST)) return;
      ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag slotTag = list.getCompound(i);
         String slotId = slotTag.getString("SlotId");
         ListTag itemList = slotTag.getList("Stacks", Tag.TAG_COMPOUND);
         List<ItemStack> stacks = new ArrayList<>(itemList.size());
         boolean hasItem = false;
         for (int j = 0; j < itemList.size(); j++) {
            CompoundTag itemTag = itemList.getCompound(j);
            Tag itemNbt = itemTag.get("Item");
            if (itemNbt != null) {
               ItemStack stack = ItemStack.parse(registries, itemNbt).orElse(ItemStack.EMPTY);
               stacks.add(stack);
               if (!stack.isEmpty()) hasItem = true;
            } else {
               stacks.add(ItemStack.EMPTY);
            }
         }
         if (hasItem) out.put(slotId, stacks);
      }
   }

   private static ListTag saveBooleanSlotMap(Map<String, List<Boolean>> slotMap) {
      ListTag list = new ListTag();
      slotMap.forEach((slotId, options) -> {
         CompoundTag slotTag = new CompoundTag();
         slotTag.putString("SlotId", slotId);
         ListTag optionList = new ListTag();
         for (Boolean option : options) {
            CompoundTag optionTag = new CompoundTag();
            optionTag.putBoolean("Render", Boolean.TRUE.equals(option));
            optionList.add(optionTag);
         }
         slotTag.put("Options", optionList);
         list.add(slotTag);
      });
      return list;
   }

   private static void loadBooleanSlotMap(CompoundTag tag, String key, Map<String, List<Boolean>> out) {
      out.clear();
      if (!tag.contains(key, Tag.TAG_LIST)) return;
      ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag slotTag = list.getCompound(i);
         String slotId = slotTag.getString("SlotId");
         ListTag optionList = slotTag.getList("Options", Tag.TAG_COMPOUND);
         List<Boolean> options = new ArrayList<>(optionList.size());
         for (int j = 0; j < optionList.size(); j++) {
            options.add(optionList.getCompound(j).getBoolean("Render"));
         }
         if (!slotId.isBlank()) out.put(slotId, List.copyOf(options));
      }
   }

   private final class GrabConstraint {
      private final UUID playerId;
      @Nullable
      private PhysicsConstraintHandle constraintHandle;

      private GrabConstraint(UUID playerId) {
         this.playerId = playerId;
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
         if (standingSubLevel != null && RagdollAssemblyHelper.isRagdollPart(standingSubLevel.getUniqueId())) {
            return;
         }

         Vector3d constraintGoal = JOMLConversion.toJOML(player.getEyePosition().add(0, GRAB_ANCHOR_Y_OFFSET, 0).add(player.getLookAngle().scale(GRAB_HOLD_DISTANCE)));
         Vector3d constraintPosition = RagdollPartBlockEntity.this.grabCenter();
         double validRange = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue() + 2.0;
         double currentDistance = Sable.HELPER.distanceSquaredWithSubLevels(RagdollPartBlockEntity.this.level, constraintGoal, constraintPosition);
         if (currentDistance > validRange * validRange) {
            return;
         }

         ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
         if (container == null) {
            return;
         }

         SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
         this.constraintHandle = SableConstraintCompat.addConstraint(
            physicsSystem.getPipeline(), null, subLevel,
            SableConstraintCompat.free(constraintGoal, constraintPosition, new Quaterniond()));

         for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            this.constraintHandle.setMotor(axis, 0.0, GRAB_STIFFNESS, GRAB_DAMPING, true, GRAB_MAX_FORCE);
         }
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

      public float renderScale() {
         return switch (this) {
            case HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG -> 0.9375F;
         };
      }

      @Override
      public String getSerializedName() {
         return this.serializedName;
      }

      public static BodyPart byName(String name) {
         for (BodyPart part : values()) {
            if (part.name().equals(name) || part.serializedName.equals(name)) {
               return part;
            }
         }

         return TORSO;
      }
   }
}
