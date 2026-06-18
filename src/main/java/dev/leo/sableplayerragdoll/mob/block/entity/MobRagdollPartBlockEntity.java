package dev.leo.sableplayerragdoll.mob.block.entity;

import dev.leo.sableplayerragdoll.RagdollGrabCallbacks;
import dev.leo.sableplayerragdoll.physics.RagdollAssemblyHelper;
import dev.leo.sableplayerragdoll.physics.SableConstraintCompat;
import dev.leo.sableplayerragdoll.mob.MobRagdollBlocks;
import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly;
import dev.leo.sableplayerragdoll.mob.block.MobPartRole;
import dev.leo.sableplayerragdoll.mob.block.MobRagdollPartBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class MobRagdollPartBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    private static final double GRAB_STIFFNESS = 500.0;
    private static final double GRAB_DAMPING = 50.0;
    private static final double GRAB_MAX_FORCE = 200.0;
    private static final double GRAB_HOLD_DISTANCE = 1.0;
    private static final double GRAB_ANCHOR_Y_OFFSET = -0.6;
    private static final double GRAB_MAX_DISTANCE = 4.0;
    private ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/block/light_blue_stained_glass.png");
    private ResourceLocation entityType;
    private String partName = "";
    private List<String> keepPartNames = List.of();
    private List<Quad> quads = List.of();
    private CompoundTag variantData;
    private UUID sourceEntityId;
    private int sourceEntityNetworkId = -1;
    private boolean baby;
    private float renderScale = 1.0F;
    private float renderQx;
    private float renderQy;
    private float renderQz;
    private float renderQw = 1.0F;
    private MobPartRole role = MobPartRole.OTHER;
    private boolean renderAnchor = true;
    private float visualXSize = 8.0F;
    private float visualYSize = 8.0F;
    private float visualZSize = 8.0F;
    private boolean restoreTriggered;
    private final Map<UUID, GrabConstraint> grabbers = new HashMap<>();

    public MobRagdollPartBlockEntity(BlockPos pos, BlockState blockState) {
        super(MobRagdollBlocks.MOB_RAGDOLL_PART_ENTITY.get(), pos, blockState);
    }

    public void configure(ResourceLocation texture, List<Quad> quads, ResourceLocation entityType, UUID sourceEntityId, int sourceEntityNetworkId, String partName, List<String> keepPartNames, CompoundTag variantData, boolean baby, float renderScale, float renderQx, float renderQy, float renderQz, float renderQw, MobPartRole role, float visualXSize, float visualYSize, float visualZSize) {
        this.texture = texture;
        this.quads = List.copyOf(quads);
        this.entityType = entityType;
        this.sourceEntityId = sourceEntityId;
        this.sourceEntityNetworkId = sourceEntityNetworkId;
        this.partName = partName == null ? "" : partName;
        this.keepPartNames = List.copyOf(keepPartNames == null ? List.of() : keepPartNames);
        this.variantData = sanitizedVariantData(variantData);
        this.baby = baby;
        this.renderScale = renderScale;
        this.renderQx = renderQx;
        this.renderQy = renderQy;
        this.renderQz = renderQz;
        this.renderQw = renderQw;
        this.role = role == null ? MobPartRole.OTHER : role;
        this.renderAnchor = true;
        this.visualXSize = Math.max(1.0F, visualXSize);
        this.visualYSize = Math.max(1.0F, visualYSize);
        this.visualZSize = Math.max(1.0F, visualZSize);
        this.setChanged();
    }

    public void configureCollisionOnly() {
        this.texture = ResourceLocation.withDefaultNamespace("textures/block/light_blue_stained_glass.png");
        this.quads = List.of();
        this.entityType = null;
        this.sourceEntityId = null;
        this.sourceEntityNetworkId = -1;
        this.partName = "";
        this.keepPartNames = List.of();
        this.variantData = null;
        this.baby = false;
        this.renderScale = 1.0F;
        this.renderQx = 0.0F;
        this.renderQy = 0.0F;
        this.renderQz = 0.0F;
        this.renderQw = 1.0F;
        this.role = MobPartRole.OTHER;
        this.renderAnchor = false;
        this.visualXSize = 1.0F;
        this.visualYSize = 1.0F;
        this.visualZSize = 1.0F;
        this.setChanged();
    }

    public ResourceLocation texture() {
        return this.texture;
    }

    public List<Quad> quads() {
        return this.quads;
    }

    public ResourceLocation entityType() {
        return this.entityType;
    }

    public UUID sourceEntityId() {
        return this.sourceEntityId;
    }

    public int sourceEntityNetworkId() {
        return this.sourceEntityNetworkId;
    }

    public String partName() {
        return this.partName;
    }

    public List<String> keepPartNames() {
        return this.keepPartNames;
    }

    public CompoundTag variantData() {
        return this.variantData;
    }

    public boolean baby() {
        return this.baby;
    }

    public float renderScale() {
        return this.renderScale;
    }

    public float renderQx() {
        return this.renderQx;
    }

    public float renderQy() {
        return this.renderQy;
    }

    public float renderQz() {
        return this.renderQz;
    }

    public float renderQw() {
        return this.renderQw;
    }

    public MobPartRole role() {
        return this.role;
    }

    public boolean renderAnchor() {
        return this.renderAnchor;
    }

    public void startGrab(UUID playerId) {
        if (!this.grabbers.containsKey(playerId)) {
            this.grabbers.put(playerId, new GrabConstraint(playerId));
            this.markSourceGrabbed();
            this.setChanged();
        }
    }

    public void stopGrab(UUID playerId) {
        GrabConstraint constraint = this.grabbers.remove(playerId);
        if (constraint != null) {
            constraint.removeJoint();
            this.markSourceReleased();
            this.setChanged();
        }
    }

    public VoxelShape visualShape() {
        return Block.box(
                (16.0F - this.visualXSize) * 0.5F,
                (16.0F - this.visualYSize) * 0.5F,
                (16.0F - this.visualZSize) * 0.5F,
                (16.0F + this.visualXSize) * 0.5F,
                (16.0F + this.visualYSize) * 0.5F,
                (16.0F + this.visualZSize) * 0.5F
        );
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        this.checkGrabbers();
        for (GrabConstraint constraint : this.grabbers.values()) {
            constraint.physicsTick(subLevel);
        }

        if (this.restoreTriggered) {
            return;
        }
        if (this.role == MobPartRole.TORSO
                && subLevel.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (MobRagdollAssembly.restoreFromSave(serverLevel, subLevel.getUniqueId())) {
                this.restoreTriggered = true;
            }
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
                this.markSourceReleased();
                this.notifyReleased(entry.getKey());
                this.setChanged();
            }
        }
    }

    private void removeAllGrabbers() {
        if (!this.grabbers.isEmpty()) {
            for (Map.Entry<UUID, GrabConstraint> entry : this.grabbers.entrySet()) {
                entry.getValue().removeJoint();
                this.markSourceReleased();
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

    private void markSourceGrabbed() {
        if (this.sourceEntityId != null && this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            MobRagdollAssembly.markGrabbed(serverLevel, this.sourceEntityId);
        }
    }

    private void markSourceReleased() {
        if (this.sourceEntityId != null && this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            MobRagdollAssembly.markReleased(serverLevel, this.sourceEntityId);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.removeAllGrabbers();
    }

    private Vector3d grabCenter() {
        return JOMLConversion.atCenterOf(this.getBlockPos());
    }

    public float xSize() {
        return this.visualXSize / 16.0F;
    }

    public float ySize() {
        return this.visualYSize / 16.0F;
    }

    public float zSize() {
        return this.visualZSize / 16.0F;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Texture", this.texture.toString());
        if (this.entityType != null) {
            tag.putString("EntityType", this.entityType.toString());
        }
        if (this.sourceEntityId != null) {
            tag.putUUID("SourceEntityId", this.sourceEntityId);
        }
        tag.putInt("SourceEntityNetworkId", this.sourceEntityNetworkId);
        tag.putString("PartName", this.partName);
        if (this.variantData != null) {
            tag.put("VariantData", this.variantData);
        }
        tag.putBoolean("Baby", this.baby);
        tag.putFloat("RenderScale", this.renderScale);
        tag.putFloat("RenderQx", this.renderQx);
        tag.putFloat("RenderQy", this.renderQy);
        tag.putFloat("RenderQz", this.renderQz);
        tag.putFloat("RenderQw", this.renderQw);
        tag.putString("Role", this.role.getSerializedName());
        tag.putBoolean("RenderAnchor", this.renderAnchor);
        tag.putFloat("VisualXSize", this.visualXSize);
        tag.putFloat("VisualYSize", this.visualYSize);
        tag.putFloat("VisualZSize", this.visualZSize);
        ListTag keepNameTags = new ListTag();
        for (String keepPartName : this.keepPartNames) {
            keepNameTags.add(net.minecraft.nbt.StringTag.valueOf(keepPartName));
        }
        tag.put("KeepPartNames", keepNameTags);
        ListTag quadTags = new ListTag();
        for (Quad quad : this.quads) {
            CompoundTag quadTag = new CompoundTag();
            quadTag.putFloat("NX", quad.normalX());
            quadTag.putFloat("NY", quad.normalY());
            quadTag.putFloat("NZ", quad.normalZ());
            ListTag vertexTags = new ListTag();
            for (Vertex vertex : quad.vertices()) {
                CompoundTag vertexTag = new CompoundTag();
                vertexTag.putFloat("X", vertex.x());
                vertexTag.putFloat("Y", vertex.y());
                vertexTag.putFloat("Z", vertex.z());
                vertexTag.putFloat("U", vertex.u());
                vertexTag.putFloat("V", vertex.v());
                vertexTags.add(vertexTag);
            }
            quadTag.put("Vertices", vertexTags);
            quadTags.add(quadTag);
        }
        tag.put("Quads", quadTags);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("Texture"));
        this.texture = parsed == null ? ResourceLocation.withDefaultNamespace("textures/block/light_blue_stained_glass.png") : parsed;
        this.entityType = tag.contains("EntityType", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("EntityType")) : null;
        this.sourceEntityId = tag.hasUUID("SourceEntityId") ? tag.getUUID("SourceEntityId") : null;
        this.sourceEntityNetworkId = tag.contains("SourceEntityNetworkId", Tag.TAG_INT) ? tag.getInt("SourceEntityNetworkId") : -1;
        this.partName = tag.getString("PartName");
        this.variantData = tag.contains("VariantData") ? sanitizedVariantData(tag.getCompound("VariantData")) : null;
        this.baby = tag.getBoolean("Baby");
        this.renderScale = tag.contains("RenderScale", Tag.TAG_FLOAT) ? tag.getFloat("RenderScale") : 1.0F;
        this.renderQx = tag.contains("RenderQx", Tag.TAG_FLOAT) ? tag.getFloat("RenderQx") : 0.0F;
        this.renderQy = tag.contains("RenderQy", Tag.TAG_FLOAT) ? tag.getFloat("RenderQy") : 0.0F;
        this.renderQz = tag.contains("RenderQz", Tag.TAG_FLOAT) ? tag.getFloat("RenderQz") : 0.0F;
        this.renderQw = tag.contains("RenderQw", Tag.TAG_FLOAT) ? tag.getFloat("RenderQw") : 1.0F;
        this.role = MobPartRole.byName(tag.getString("Role"));
        this.renderAnchor = !tag.contains("RenderAnchor", Tag.TAG_BYTE) || tag.getBoolean("RenderAnchor");
        this.visualXSize = tag.contains("VisualXSize", Tag.TAG_FLOAT) ? Math.max(1.0F, tag.getFloat("VisualXSize")) : 8.0F;
        this.visualYSize = tag.contains("VisualYSize", Tag.TAG_FLOAT) ? Math.max(1.0F, tag.getFloat("VisualYSize")) : 8.0F;
        this.visualZSize = tag.contains("VisualZSize", Tag.TAG_FLOAT) ? Math.max(1.0F, tag.getFloat("VisualZSize")) : 8.0F;
        List<String> loadedKeepNames = new ArrayList<>();
        if (tag.contains("KeepPartNames", Tag.TAG_LIST)) {
            ListTag keepNameTags = tag.getList("KeepPartNames", Tag.TAG_STRING);
            for (int i = 0; i < keepNameTags.size(); i++) {
                loadedKeepNames.add(keepNameTags.getString(i));
            }
        }
        this.keepPartNames = List.copyOf(loadedKeepNames);
        List<Quad> loadedQuads = new ArrayList<>();
        if (tag.contains("Quads", Tag.TAG_LIST)) {
            ListTag quadTags = tag.getList("Quads", Tag.TAG_COMPOUND);
            for (int i = 0; i < quadTags.size(); i++) {
                CompoundTag quadTag = quadTags.getCompound(i);
                List<Vertex> vertices = new ArrayList<>(4);
                ListTag vertexTags = quadTag.getList("Vertices", Tag.TAG_COMPOUND);
                for (int v = 0; v < vertexTags.size(); v++) {
                    CompoundTag vertexTag = vertexTags.getCompound(v);
                    vertices.add(new Vertex(
                            vertexTag.getFloat("X"),
                            vertexTag.getFloat("Y"),
                            vertexTag.getFloat("Z"),
                            vertexTag.getFloat("U"),
                            vertexTag.getFloat("V")
                    ));
                }
                if (vertices.size() == 4) {
                    loadedQuads.add(new Quad(List.copyOf(vertices), quadTag.getFloat("NX"), quadTag.getFloat("NY"), quadTag.getFloat("NZ")));
                }
            }
        }
        this.quads = List.copyOf(loadedQuads);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public record Quad(List<Vertex> vertices, float normalX, float normalY, float normalZ) {
    }

    public record Vertex(float x, float y, float z, float u, float v) {
    }

    private static CompoundTag sanitizedVariantData(CompoundTag variantData) {
        if (variantData == null) {
            return null;
        }
        CompoundTag copy = variantData.copy();
        copy.remove("Age");
        copy.remove("ForcedAge");
        copy.remove("InLove");
        return copy;
    }

    private final class GrabConstraint {
        private final UUID playerId;
        private PhysicsConstraintHandle constraintHandle;

        private GrabConstraint(UUID playerId) {
            this.playerId = playerId;
        }

        private void physicsTick(ServerSubLevel subLevel) {
            this.removeJoint();
            if (MobRagdollPartBlockEntity.this.level == null) {
                return;
            }

            Player player = MobRagdollPartBlockEntity.this.level.getPlayerByUUID(this.playerId);
            if (player == null || player.isDeadOrDying() || player.isSpectator()) {
                return;
            }

            SubLevel standingSubLevel = Sable.HELPER.getTrackingSubLevel(player);
            if (standingSubLevel != null
                    && (RagdollAssemblyHelper.isRagdollPart(standingSubLevel.getUniqueId())
                    || MobRagdollAssembly.isRagdollPart(standingSubLevel.getUniqueId()))) {
                return;
            }

            Vector3d constraintGoal = JOMLConversion.toJOML(player.getEyePosition().add(0, GRAB_ANCHOR_Y_OFFSET, 0).add(player.getLookAngle().scale(GRAB_HOLD_DISTANCE)));
            Vector3d constraintPosition = MobRagdollPartBlockEntity.this.grabCenter();
            double validRange = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue() + 2.0;
            double currentDistance = Sable.HELPER.distanceSquaredWithSubLevels(MobRagdollPartBlockEntity.this.level, constraintGoal, constraintPosition);
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
}
