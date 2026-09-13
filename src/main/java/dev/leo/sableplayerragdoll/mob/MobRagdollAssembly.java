package dev.leo.sableplayerragdoll.mob;

import dev.leo.sableplayerragdoll.physics.RagdollBlockLifetime;
import dev.leo.sableplayerragdoll.physics.RagdollBlockOwnership;
import dev.leo.sableplayerragdoll.physics.RagdollOwnedBlock;
import dev.leo.sableplayerragdoll.physics.RagdollRegistry;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.mob.block.MobPartRole;
import dev.leo.sableplayerragdoll.mob.block.MobRagdollPartBlock;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.mob.api.MobRagdollEndEvent;
import dev.leo.sableplayerragdoll.mob.api.MobRagdollLaunchOptions;
import dev.leo.sableplayerragdoll.mob.api.MobRagdollStartEvent;
import dev.leo.sableplayerragdoll.mob.network.MobRagdollLaunchRequestPacket;
import dev.leo.sableplayerragdoll.mob.network.MobRagdollSourceStatePacket;
import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollLaunchOptions;
import dev.leo.sableplayerragdoll.api.RagdollLimbOptions;
import dev.leo.sableplayerragdoll.api.RagdollPoseSnapshot;
import dev.leo.sableplayerragdoll.physics.SableConstraintCompat;
import dev.leo.sableplayerragdoll.physics.RagdollCleanup;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class MobRagdollAssembly {
    private static final Set<UUID> CONVERTED_ENTITIES = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, RagdollState> RAGDOLL_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, PhysicsConstraintHandle> RESTORED_HANDLES = new ConcurrentHashMap<>();
    private static final double JOINT_ANGULAR_STIFFNESS = 20.0;
    private static final double JOINT_ANGULAR_DAMPING = 20.0;
    private static final int GRAB_RESTORE_PROTECTION_TICKS = 200;
    private static final int DEFERRED_RESTORE_AFTER_RELEASE_TICKS = 30;
    private static final Map<UUID, Float> CLIENT_BODY_YAW = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> GRAB_COUNTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> GRAB_PROTECTED_UNTIL = new ConcurrentHashMap<>();
    private record DeferredRestore(ServerLevel level, long tick, MobRagdollEndEvent.Reason reason) {}
    private static final Map<UUID, DeferredRestore> DEFERRED_RESTORES = new ConcurrentHashMap<>();
    private static final Map<UUID, PhysicsConstraintHandle> JOINT_BY_CHILD = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingMobless> PENDING_MOBLESS = new ConcurrentHashMap<>();
    private static final int MOBLESS_DETACH_TIMEOUT_TICKS = 60;
    public static final int DEFAULT_MOBLESS_DURATION_TICKS = Integer.MAX_VALUE;

    private record PendingMobless(ServerLevel level, long deadlineTick) {
    }

    private MobRagdollAssembly() {
    }

    public static void setClientBodyYaw(UUID uuid, float bodyYaw) {
        CLIENT_BODY_YAW.put(uuid, bodyYaw);
    }

    public static void resetRuntimeState() {
        CONVERTED_ENTITIES.clear();
        PENDING_LAUNCHES.clear();
        SPAWN_QUEUE.clear();
        RAGDOLL_STATES.clear();
        RESTORED_HANDLES.clear();
        CLIENT_BODY_YAW.clear();
        RESTORED_UUIDS.clear();
        NEXT_IMPACT_DAMAGE_TICK.clear();
        NEXT_IMPACT_SOUND_TICK.clear();
        LAST_VELOCITIES.clear();
        GRAB_COUNTS.clear();
        GRAB_PROTECTED_UNTIL.clear();
        DEFERRED_RESTORES.clear();
        PENDING_MOBLESS.clear();
        JOINT_BY_CHILD.clear();
    }

    public static void spawn(ServerLevel level, LivingEntity entity, List<PartSpawn> parts) {
        spawn(level, entity, parts, Vec3.ZERO, Vec3.ZERO);
    }

    public static void spawn(ServerLevel level, LivingEntity entity, List<PartSpawn> parts,
                              Vec3 linearVelocity, Vec3 angularVelocity) {
        spawn(level, entity, parts, linearVelocity, angularVelocity, RAGDOLL_DURATION_TICKS);
    }

    public static void spawn(ServerLevel level, LivingEntity entity, List<PartSpawn> parts,
                              Vec3 linearVelocity, Vec3 angularVelocity, int durationTicks) {
        if (parts.isEmpty()) {
            return;
        }
        if (!MobRagdollWhitelist.isAllowed(level, entity.getType())) {
            return;
        }
        if (!CONVERTED_ENTITIES.add(entity.getUUID())) {
            return;
        }

        Float clientYaw = CLIENT_BODY_YAW.remove(entity.getUUID());
        float yaw = clientYaw != null ? clientYaw
                : (entity instanceof Mob mob ? mob.yBodyRot : entity.getYRot());
        double yawRadians = Math.toRadians(yaw);
        Vec3 right = new Vec3(Math.cos(yawRadians), 0.0, Math.sin(yawRadians));
        Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        Quaterniond baseOrientation = new Quaterniond().rotateY(Math.toRadians(180.0F - yaw));

        // Capture entity state before queueing — position and snapshot may change by drain time
        CompoundTag entitySnapshot = entity.saveWithoutId(new CompoundTag());
        ragdollPlayerPassengers(entity, linearVelocity);
        if (entity.isPassenger()) {
            entity.stopRiding();
        }
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        entity.setDeltaMovement(Vec3.ZERO);

        SPAWN_QUEUE.add(new PendingAssembly(
                level, entity.getUUID(), entity.getId(), List.copyOf(parts),
                entity.position(), entity.blockPosition(), right, forward, baseOrientation,
                linearVelocity, angularVelocity, durationTicks, entitySnapshot));
        drainSpawnQueue(level);
    }

    private static void ragdollPlayerPassengers(LivingEntity entity, Vec3 inheritedVelocity) {
        for (Entity passenger : List.copyOf(entity.getPassengers())) {
            if (passenger instanceof ServerPlayer player) {
                RagdollAPI.launch(player, inheritedVelocity, RagdollLaunchOptions.builder()
                        .autoSeat(true)
                        .build(),
                        new RagdollPoseSnapshot(RagdollLimbOptions.defaults(), player.yBodyRot));
                player.stopRiding();
            }
        }
    }

    public static boolean requestLaunch(ServerLevel level, LivingEntity entity, Vec3 linear, Vec3 angular, MobRagdollLaunchOptions options) {
        UUID uuid = entity.getUUID();
        if (entity.isRemoved() || CONVERTED_ENTITIES.contains(uuid) || PENDING_LAUNCHES.containsKey(uuid)) {
            return false;
        }
        if (!MobRagdollWhitelist.isAllowed(level, entity.getType())) {
            return false;
        }
        MobRagdollStartEvent startEvent = new MobRagdollStartEvent(entity, linear);
        NeoForge.EVENT_BUS.post(startEvent);
        if (startEvent.isCanceled()) {
            return false;
        }
        MobRagdollLaunchOptions resolved = options == null ? MobRagdollLaunchOptions.defaults() : options;
        PENDING_LAUNCHES.put(uuid, new PendingLaunch(startEvent.velocity(), angular, resolved, level.getGameTime(), level));
        PacketDistributor.sendToPlayersTrackingEntity(entity, new MobRagdollLaunchRequestPacket(entity.getId()));
        return true;
    }

    public static boolean consumePendingLaunch(ServerLevel level, LivingEntity entity, List<PartSpawn> parts) {
        PendingLaunch pending = PENDING_LAUNCHES.remove(entity.getUUID());
        if (pending == null) {
            return false;
        }
        spawn(level, entity, parts, pending.linear(), pending.angular(),
                pending.options().durationTicks());
        return true;
    }

    public static UUID spawnMobless(ServerLevel level, EntityType<?> type, Vec3 pos, Vec3 linear, int durationTicks) {
        if (type == null || !(type.create(level) instanceof LivingEntity living)) {
            return null;
        }
        living.moveTo(pos.x, pos.y, pos.z, living.getYRot(), living.getXRot());
        if (living instanceof Mob mob) {
            mob.setNoAi(true);
        }
        living.setSilent(true);
        living.setInvulnerable(true);
        if (!level.addFreshEntity(living)) {
            return null;
        }
        int duration = durationTicks > 0 ? durationTicks : DEFAULT_MOBLESS_DURATION_TICKS;
        if (!requestLaunch(level, living, linear, Vec3.ZERO, MobRagdollLaunchOptions.builder().durationTicks(duration).build())) {
            living.discard();
            return null;
        }
        PENDING_MOBLESS.put(living.getUUID(), new PendingMobless(level, level.getGameTime() + MOBLESS_DETACH_TIMEOUT_TICKS));
        return living.getUUID();
    }

    private static void processPendingMobless(ServerLevel level, long now) {
        if (PENDING_MOBLESS.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, PendingMobless>> it = PENDING_MOBLESS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, PendingMobless> e = it.next();
            if (e.getValue().level() != level) {
                continue;
            }
            UUID uuid = e.getKey();
            if (CONVERTED_ENTITIES.contains(uuid) && MobRagdollSavedData.get(level).getEntry(uuid) != null) {
                MobRagdollSavedData.get(level).markMobless(uuid);
                for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
                    var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
                    if (uuid.equals(identity.source())) {
                        identity.lifetime(identity.createdAt(), "TIMED", null, identity.expiresAt());
                        be.setChanged();
                    }
                }
                if (level.getEntity(uuid) instanceof Entity ent) {
                    ent.discard();
                }
                it.remove();
            } else if (now >= e.getValue().deadlineTick()) {
                PENDING_LAUNCHES.remove(uuid);
                if (level.getEntity(uuid) instanceof Entity ent) {
                    ent.discard();
                }
                it.remove();
            }
        }
    }

    public static UUID savedOwnerForPart(ServerLevel level, UUID partId) {
        for (var entry : MobRagdollSavedData.get(level).entries().entrySet()) {
            if (entry.getValue().partIds().containsValue(partId)) return entry.getKey();
        }
        return null;
    }

    public static boolean releaseForExternalRemoval(ServerLevel level, UUID partId) {
        for (PendingAssembly pending : List.copyOf(SPAWN_QUEUE)) {
            if (pending.level != level) continue;
            if (pending.assembled.stream().anyMatch(part -> part.subLevel().getUniqueId().equals(partId))) {
                SPAWN_QUEUE.remove(pending);
                CONVERTED_ENTITIES.remove(pending.entityUUID);
                cleanupPartialAssembly(level, pending);
                return true;
            }
        }
        UUID owner = savedOwnerForPart(level, partId);
        if (owner == null) {
            owner = ownerUuidForSubLevel(partId);
            if (owner == null || !ownsRagdoll(RAGDOLL_STATES.get(owner), level)) return false;
        }
        clearRestoreDeferral(owner);
        expireSavedRagdoll(level, owner, MobRagdollEndEvent.Reason.RELEASED);
        return true;
    }

    public static boolean removeBySubLevel(ServerLevel level, UUID subLevelId, boolean smokePuff) {
        if (smokePuff) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null && container.getSubLevel(subLevelId) instanceof ServerSubLevel ssl) {
                RagdollRegistry.emitRemovalPuff(level, ssl);
            }
        }
        UUID owner = ownerUuidForSubLevel(subLevelId);
        if (owner == null && (RAGDOLL_STATES.containsKey(subLevelId) || MobRagdollSavedData.get(level).getEntry(subLevelId) != null)) {
            owner = subLevelId;
        }
        if (owner != null) {
            discardRagdoll(level, owner);
            return true;
        }
        return removeLooseSubLevel(level, subLevelId);
    }

    public static UUID dismemberBySubLevel(ServerLevel level, UUID subLevelId) {
        UUID owner = ownerUuidForSubLevel(subLevelId);
        if (owner == null) {
            return null;
        }
        RagdollState state = RAGDOLL_STATES.get(owner);
        if (state == null) {
            return null;
        }
        SpawnedPart target = findPart(state, subLevelId);
        if (target == null || target == selectRoot(state.parts())) {
            return null;
        }
        PhysicsConstraintHandle handle = JOINT_BY_CHILD.remove(subLevelId);
        if (handle != null && handle.isValid()) {
            handle.remove();
        }
        List<SpawnedPart> remaining = new ArrayList<>(state.parts());
        remaining.remove(target);
        RAGDOLL_STATES.put(owner, new RagdollState(List.copyOf(remaining), state.spawnedAtTick(), state.preRagdollPos(), state.durationTicks()));
        RagdollBlockOwnership.sever(level, subLevelId);
        MobRagdollSavedData.get(level).removePart(owner, subLevelId);
        return subLevelId;
    }

    private static UUID ownerUuidForSubLevel(UUID subLevelId) {
        for (var entry : RAGDOLL_STATES.entrySet()) {
            if (findPart(entry.getValue(), subLevelId) != null) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static SpawnedPart findPart(RagdollState state, UUID subLevelId) {
        for (SpawnedPart part : state.parts()) {
            if (part.subLevel() != null && subLevelId.equals(part.subLevel().getUniqueId())) {
                return part;
            }
        }
        return null;
    }

    private static boolean removeLooseSubLevel(ServerLevel level, UUID subLevelId) {
        if (RagdollBlockOwnership.hasLimb(level, subLevelId)) {
            removeJoint(subLevelId);
            RagdollCleanup.removeLimb(level, subLevelId);
            return true;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !(container.getSubLevel(subLevelId) instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) {
            return false;
        }
        BlockPos center = subLevel.getPlot().getCenterBlock();
        if (!(subLevel.getLevel().getBlockState(center).getBlock() instanceof MobRagdollPartBlock)) {
            return false;
        }
        removeJoint(subLevelId);
        removeSubLevelIfPresent(container, subLevel);
        return true;
    }

    private static void removeJoint(UUID limb) {
        PhysicsConstraintHandle handle = JOINT_BY_CHILD.remove(limb);
        if (handle != null && handle.isValid()) handle.remove();
    }

    private static UUID limbId(SpawnedPart part) {
        return RagdollBlockOwnership.limbAt(
                part.subLevel().getLevel(), part.plotPos(), part.subLevel().getUniqueId());
    }

    private static void forgetJoints(RagdollState state) {
        if (state == null) {
            return;
        }
        for (SpawnedPart part : state.parts()) {
            if (part.subLevel() != null) {
                removeJoint(limbId(part));
            }
        }
    }

    private static Vec3 rootVelocity(RagdollState state) {
        if (state.parts().isEmpty()) {
            return Vec3.ZERO;
        }
        ServerSubLevel subLevel = selectRoot(state.parts()).subLevel();
        if (subLevel == null || subLevel.isRemoved()) {
            return Vec3.ZERO;
        }
        try {
            Vector3d velocity = new Vector3d();
            RigidBodyHandle.of(subLevel).getLinearVelocity(velocity);
            return new Vec3(velocity.x, velocity.y, velocity.z);
        } catch (Throwable ignored) {
            return Vec3.ZERO;
        }
    }

    private static Vec3 rootPosition(RagdollState state) {
        if (state.parts().isEmpty()) {
            return state.preRagdollPos();
        }
        ServerSubLevel subLevel = selectRoot(state.parts()).subLevel();
        if (subLevel == null || subLevel.isRemoved()) {
            return state.preRagdollPos();
        }
        try {
            return subLevel.logicalPose().transformPosition(Vec3.atCenterOf(subLevel.getPlot().getCenterBlock()));
        } catch (Throwable ignored) {
            return state.preRagdollPos();
        }
    }

    public static void despawn(ServerLevel level, LivingEntity entity) {
        despawn(level, entity, MobRagdollEndEvent.Reason.RELEASED);
    }

    public static void despawn(ServerLevel level, LivingEntity entity, MobRagdollEndEvent.Reason reason) {
        UUID uuid = entity.getUUID();
        if (deferRestoreIfProtected(level, uuid, reason)) {
            return;
        }
        if (!CONVERTED_ENTITIES.remove(uuid)) {
            return;
        }
        syncClientSourceState(entity, false);
        RagdollState state = RAGDOLL_STATES.remove(uuid);
        forgetJoints(state);
        RESTORED_UUIDS.remove(uuid);
        RESTORED_HANDLES.remove(uuid);
        Vec3 exitVelocity = state == null ? Vec3.ZERO : rootVelocity(state);
        NeoForge.EVENT_BUS.post(new MobRagdollEndEvent(entity, exitVelocity, reason));
        entity.stopRiding();
        showRagdollSource(entity);
        if (state != null) {
            Vec3 safe = rootPosition(state);
            entity.moveTo(safe.x, safe.y, safe.z, entity.getYRot(), entity.getXRot());
        }
        NEXT_IMPACT_DAMAGE_TICK.remove(uuid);
        NEXT_IMPACT_SOUND_TICK.remove(uuid);
        clearRestoreDeferral(uuid);
        MobRagdollSavedData savedData = MobRagdollSavedData.get(level);
        MobRagdollSavedData.Entry saved = savedData.getEntry(uuid);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (saved != null) {
            removeSavedSubLevels(level, container, saved);
        }
        savedData.removeEntry(uuid);
        if (state != null) {
            for (SpawnedPart spawned : state.parts()) {
                ServerSubLevel subLevel = spawned.subLevel();
                LAST_VELOCITIES.remove(subLevel);
                if (subLevel != null && !subLevel.isRemoved()) {
                    if (saved == null) RagdollCleanup.removeLimb(level, limbId(spawned));
                }
            }
        }
    }

    public static boolean isConverted(UUID uuid) {
        return CONVERTED_ENTITIES.contains(uuid);
    }

    public static boolean isActiveOrSavedRagdollSource(ServerLevel level, UUID uuid) {
        return CONVERTED_ENTITIES.contains(uuid) || MobRagdollSavedData.get(level).getEntry(uuid) != null;
    }

    public static boolean hasPendingLaunch(UUID uuid) {
        return PENDING_LAUNCHES.containsKey(uuid);
    }

    public static boolean isRagdollPart(UUID subLevelId) {
        for (RagdollState state : RAGDOLL_STATES.values()) {
            for (SpawnedPart part : state.parts()) {
                if (part.subLevel() != null && subLevelId.equals(part.subLevel().getUniqueId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void markGrabbed(ServerLevel level, UUID uuid) {
        long now = level.getGameTime();
        GRAB_COUNTS.merge(uuid, 1, Integer::sum);
        GRAB_PROTECTED_UNTIL.putIfAbsent(uuid, now + GRAB_RESTORE_PROTECTION_TICKS);
    }

    public static void markReleased(ServerLevel level, UUID uuid) {
        GRAB_COUNTS.computeIfPresent(uuid, (ignored, count) -> count <= 1 ? null : count - 1);
        if (!GRAB_COUNTS.containsKey(uuid) && DEFERRED_RESTORES.containsKey(uuid)) {
            long now = level.getGameTime();
            long protectedUntil = GRAB_PROTECTED_UNTIL.getOrDefault(uuid, now);
            DEFERRED_RESTORES.computeIfPresent(uuid, (ignored, deferred) -> new DeferredRestore(
                    deferred.level(), Math.max(now + DEFERRED_RESTORE_AFTER_RELEASE_TICKS, protectedUntil), deferred.reason()));
        }
    }

    private static boolean deferRestoreIfProtected(ServerLevel level, UUID uuid, MobRagdollEndEvent.Reason reason) {
        long now = level.getGameTime();
        long protectedUntil = GRAB_PROTECTED_UNTIL.getOrDefault(uuid, 0L);
        if (now >= protectedUntil) {
            return false;
        }

        DEFERRED_RESTORES.put(uuid, new DeferredRestore(level, protectedUntil, reason));
        return true;
    }

    private static void runDeferredRestores(ServerLevel level, long now) {
        List<UUID> due = new ArrayList<>();
        for (var entry : DEFERRED_RESTORES.entrySet()) {
            if (entry.getValue().level() == level && entry.getValue().tick() <= now) {
                due.add(entry.getKey());
            }
        }
        for (UUID uuid : due) {
            MobRagdollEndEvent.Reason reason = DEFERRED_RESTORES.get(uuid).reason();
            clearRestoreDeferral(uuid);
            if (level.getEntity(uuid) instanceof LivingEntity livingEntity) {
                despawn(level, livingEntity, reason);
            } else if (reason == MobRagdollEndEvent.Reason.EXPIRED) {
                expireSavedRagdoll(level, uuid);
            } else {
                discardRagdoll(level, uuid);
            }
        }
    }

    private static void clearRestoreDeferral(UUID uuid) {
        GRAB_COUNTS.remove(uuid);
        GRAB_PROTECTED_UNTIL.remove(uuid);
        DEFERRED_RESTORES.remove(uuid);
    }

    private static void hideRagdollSource(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        entity.setInvisible(true);
        entity.noPhysics = true;
        entity.refreshDimensions();
    }


    private static void showRagdollSource(LivingEntity entity) {
        entity.getPersistentData().remove(RagdollBlockLifetime.SOURCE_SESSION);
        if (entity instanceof Mob mob) {
            mob.setNoAi(false);
        }
        entity.setInvisible(false);
        entity.noPhysics = false;
        entity.refreshDimensions();
    }

    public static void hideLoadedRagdollSource(ServerLevel level, LivingEntity entity) {
        if (MobRagdollSavedData.get(level).getEntry(entity.getUUID()) == null) {
            return;
        }
        hideRagdollSource(entity);
    }

    public static InteractionResult interactWithPart(ServerLevel level, BlockPos partPos, Player player, InteractionHand hand) {
        LivingEntity target = sourceEntityForPart(level, partPos);
        return interactWithSource(level, target, player, hand);
    }

    public static InteractionResult interactWithPart(ServerLevel level, MobRagdollPartBlockEntity part, Player player, InteractionHand hand) {
        LivingEntity target = null;
        if (part.sourceEntityId() != null) {
            Entity entity = level.getEntity(part.sourceEntityId());
            target = entity instanceof LivingEntity living ? living : null;
        }
        if (target == null) {
            target = sourceEntityForPart(level, part.getBlockPos());
        }
        return interactWithSource(level, target, player, hand);
    }

    public static boolean attackPart(ServerLevel level, BlockPos partPos, Player player) {
        LivingEntity target = sourceEntityForPart(level, partPos);
        return attackSource(target, player);
    }

    public static boolean attackPart(ServerLevel level, MobRagdollPartBlockEntity part, Player player) {
        LivingEntity target = null;
        UUID sourceId = part.sourceEntityId();
        if (sourceId != null) {
            Entity entity = level.getEntity(sourceId);
            target = entity instanceof LivingEntity living ? living : null;
        }
        if (target == null) {
            target = sourceEntityForPart(level, part.getBlockPos());
            sourceId = target != null ? target.getUUID() : sourceId;
        }
        return attackSource(target, player);
    }

    public static final ThreadLocal<Boolean> RAGDOLL_PIPE_ACTIVE = ThreadLocal.withInitial(() -> false);

    private static InteractionResult interactWithSource(ServerLevel level, LivingEntity target, Player player, InteractionHand hand) {
        if (target == null || target.isRemoved() || !target.isAlive()) {
            return InteractionResult.PASS;
        }
        RAGDOLL_PIPE_ACTIVE.set(true);
        try {
            return target.interact(player, hand);
        } finally {
            RAGDOLL_PIPE_ACTIVE.set(false);
        }
    }

    private static boolean attackSource(LivingEntity target, Player player) {
        if (target == null || target.isRemoved() || !target.isAlive()) {
            return false;
        }
        RAGDOLL_PIPE_ACTIVE.set(true);
        try {
            player.attack(target);
        } finally {
            RAGDOLL_PIPE_ACTIVE.set(false);
        }
        return true;
    }

    private static LivingEntity sourceEntityForPart(ServerLevel level, BlockPos partPos) {
        for (var entry : RAGDOLL_STATES.entrySet()) {
            for (SpawnedPart part : entry.getValue().parts()) {
                if (part.plotPos().equals(partPos)) {
                    Entity entity = level.getEntity(entry.getKey());
                    return entity instanceof LivingEntity living ? living : null;
                }
            }
        }
        return null;
    }

    public static boolean isPendingOrConverted(UUID uuid) {
        return CONVERTED_ENTITIES.contains(uuid) || PENDING_LAUNCHES.containsKey(uuid);
    }

    public static long elapsedTicks(ServerLevel level, UUID uuid) {
        RagdollState state = RAGDOLL_STATES.get(uuid);
        return state == null ? -1L : level.getGameTime() - state.spawnedAtTick();
    }

    public static Vec3 currentVelocity(UUID uuid) {
        RagdollState state = RAGDOLL_STATES.get(uuid);
        return state == null ? Vec3.ZERO : rootVelocity(state);
    }

    public static boolean restoreFromSave(ServerLevel level, UUID triggerSubLevelId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container instanceof dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer serverContainer)) {
            return false;
        }
        MobRagdollSavedData savedData = MobRagdollSavedData.get(level);
        long now = level.getGameTime();
        List<UUID> expired = new ArrayList<>();
        if (savedData.entries().isEmpty()) return true;
        boolean handledTrigger = false;

        for (var entry : savedData.entries().entrySet()) {
            UUID uuid = entry.getKey();
            var saved = entry.getValue();
            if (triggerSubLevelId != null && !saved.partIds().containsValue(triggerSubLevelId)) {
                continue;
            }
            handledTrigger = true;

            if (now - saved.spawnedAtTick() >= saved.durationTicks()) {
                expired.add(uuid);
                continue;
            }
            PhysicsConstraintHandle existingHandle = RESTORED_HANDLES.get(uuid);
            var existingState = RAGDOLL_STATES.get(uuid);
            if (existingState != null && existingState.parts().stream().allMatch(part -> !part.subLevel().isRemoved())
                    && (existingHandle == null || existingHandle.isValid())) {
                RESTORED_UUIDS.add(uuid);
                continue;
            }
            forgetJoints(existingState);
            RAGDOLL_STATES.remove(uuid);
            RESTORED_UUIDS.remove(uuid);
            RESTORED_HANDLES.remove(uuid);

            Entity entity = level.getEntity(uuid);
            if (entity == null) {
                for (var e : level.getEntities().getAll()) {
                    if (e.getUUID().equals(uuid)) {
                        entity = e;
                        break;
                    }
                }
            }
            LivingEntity livingEntity = entity instanceof LivingEntity living ? living : null;
            if (livingEntity == null) {
                SablePlayerRagdoll.LOGGER.info("[mob-ragdoll] restoring saved ragdoll {} without loaded source entity", uuid);
            }

            List<SpawnedPart> spawnedParts = new ArrayList<>();
            int missingSubLevels = 0;
            for (var partEntry : saved.partInfos().entrySet()) {
                String partName = partEntry.getKey();
                UUID subLevelId = saved.partIds().get(partName);
                if (subLevelId == null) {
                    missingSubLevels++;
                    continue;
                }
                var block = RagdollBlockOwnership.findLimb(level, subLevelId);
                SubLevel subLevel = block == null ? serverContainer.getSubLevel(subLevelId)
                        : dev.ryanhcode.sable.Sable.HELPER.getContaining(level, block.getBlockPos());
                if (!(subLevel instanceof ServerSubLevel sl) || sl.isRemoved()) {
                    missingSubLevels++;
                    continue;
                }
                MobRagdollSavedData.PartInfo info = partEntry.getValue();
                PartSpawn ps = new PartSpawn(
                        info.role(), "", partName, List.of(), null,
                        null,
                        false,
                        1.0F,
                        info.centerX(), info.centerY(), info.centerZ(),
                        info.pivotX(), info.pivotY(), info.pivotZ(),
                        info.rotQx(), info.rotQy(), info.rotQz(), info.rotQw(),
                        0.0F, 0.0F, 0.0F, 1.0F,
                        8.0F, 8.0F, 8.0F,
                        "", List.of());
                BlockPos plotPos = block == null ? sl.getPlot().getCenterBlock() : block.getBlockPos();
                Vec3 worldCenter = sl.logicalPose().transformPosition(Vec3.atCenterOf(plotPos));
                spawnedParts.add(new SpawnedPart(ps, sl, worldCenter, plotPos, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0)));
            }

            if (missingSubLevels > 0) {
                SablePlayerRagdoll.LOGGER.info("[mob-ragdoll] delaying restore for {}: missing {}/{} saved sublevels",
                        uuid, missingSubLevels, saved.partInfos().size());
                return false;
            }
            if (spawnedParts.isEmpty()) {
                CONVERTED_ENTITIES.remove(uuid);
                if (livingEntity != null) {
                    showRagdollSource(livingEntity);
                }
                return false;
            }

            savedData.putEntry(uuid, saved);
            JointResult joints = attachJoints(level, spawnedParts);
            if (livingEntity != null) {
                hideRagdollSource(livingEntity);
            }
            CONVERTED_ENTITIES.add(uuid);
            RAGDOLL_STATES.put(uuid, new RagdollState(List.copyOf(spawnedParts),
                    saved.spawnedAtTick(), saved.preRagdollPos(), saved.durationTicks()));
            SablePlayerRagdoll.LOGGER.info("[mob-ragdoll] restored {} parts with {} joints for entity {} (source entity loaded={})",
                    spawnedParts.size(), joints.count(), uuid, livingEntity != null);
            if (joints.representative() != null) {
                RESTORED_HANDLES.put(uuid, joints.representative());
                RESTORED_UUIDS.add(uuid);
            }
        }

        for (UUID uuid : expired) {
            expireSavedRagdoll(level, uuid);
        }
        return handledTrigger;
    }

    public static boolean restoreFromSave(ServerLevel level) {
        return restoreFromSave(level, null);
    }

    private static final Set<UUID> RESTORED_UUIDS = ConcurrentHashMap.newKeySet();

    private static final int RAGDOLL_DURATION_TICKS = 80;
    private static final int PENDING_LAUNCH_TIMEOUT_TICKS = 40;
    private static final Map<UUID, PendingLaunch> PENDING_LAUNCHES = new ConcurrentHashMap<>();
    private static final ArrayDeque<PendingAssembly> SPAWN_QUEUE = new ArrayDeque<>();
    private static final int PARTS_PER_MOB_PER_TICK = Integer.MAX_VALUE;
    private static final double IMPACT_DAMAGE_THRESHOLD = 12.0;
    private static final double IMPACT_DAMAGE_MULTIPLIER = 0.75;
    private static final double IMPACT_DAMAGE_MAX = 20.0;
    private static final double IMPACT_FEEDBACK_THRESHOLD = 4.0;
    private static final int IMPACT_DAMAGE_COOLDOWN_TICKS = 10;
    private static final int IMPACT_SOUND_COOLDOWN_TICKS = 4;
    private static final Map<UUID, Long> NEXT_IMPACT_DAMAGE_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_IMPACT_SOUND_TICK = new ConcurrentHashMap<>();
    private static final Map<ServerSubLevel, Vector3d> LAST_VELOCITIES = new ConcurrentHashMap<>();

    public static void tickActiveRagdolls(ServerLevel level) {
        long now = level.getGameTime();
        drainSpawnQueue(level);
        runDeferredRestores(level, now);
        processPendingMobless(level, now);
        PENDING_LAUNCHES.values().removeIf(pending -> pending.level() == level
                && now - pending.requestedTick() > PENDING_LAUNCH_TIMEOUT_TICKS);
        var physics = SubLevelPhysicsSystem.get(level);
        // Runtime state drives the hidden source and impact feedback only. Blocks decide expiry.
        for (var entry : RAGDOLL_STATES.entrySet()) {
            var state = entry.getValue();
            if (!ownsRagdoll(state, level)) continue;
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity living) || !living.isAlive()) continue;
            if (physics != null) applyImpactDamage(level, entry.getKey(), state, physics, now);
            Vec3 position = rootPosition(state);
            living.moveTo(position.x, position.y, position.z, living.getYRot(), living.getXRot());
            living.setDeltaMovement(Vec3.ZERO);
        }
    }

    public static boolean expireFromBlock(ServerLevel level, UUID limb) {
        UUID source = savedOwnerForPart(level, limb);
        if (source == null) source = ownerUuidForSubLevel(limb);
        if (source == null) return true;
        if (deferRestoreIfProtected(level, source, MobRagdollEndEvent.Reason.EXPIRED)) return false;
        if (level.getEntity(source) instanceof LivingEntity living && !living.isAlive()) {
            discardRagdoll(level, source);
        } else {
            expireSavedRagdoll(level, source);
        }
        return true;
    }

    private static void drainSpawnQueue(ServerLevel level) {
        if (SPAWN_QUEUE.isEmpty()) return;
        List<PendingAssembly> forLevel = new ArrayList<>();
        for (PendingAssembly p : SPAWN_QUEUE) {
            if (p.level == level) forLevel.add(p);
        }
        if (forLevel.isEmpty()) return;

        for (PendingAssembly pending : forLevel) {
            Entity entity = level.getEntity(pending.entityUUID);
            if (!(entity instanceof LivingEntity livingEntity)) {
                SPAWN_QUEUE.remove(pending);
                CONVERTED_ENTITIES.remove(pending.entityUUID);
                cleanupPartialAssembly(level, pending);
                continue;
            }

            int budget = PARTS_PER_MOB_PER_TICK;
            while (pending.nextPartIndex < pending.parts.size() && budget > 0) {
                int i = pending.nextPartIndex;
                PartSpawn part = pending.parts.get(i);
                int maxYOffset = MobRagdollGeometry.maxAxisBlockOffset(part.ySize());
                int minYOffset = MobRagdollGeometry.minAxisBlockOffset(part.ySize());
                int safeY = level.getMaxBuildHeight() - 1 - maxYOffset - i * 8;
                safeY = Math.max(level.getMinBuildHeight() - minYOffset, safeY);
                BlockPos safePos = new BlockPos(pending.baseBlockPos.getX(), safeY, pending.baseBlockPos.getZ());
                AssembledPart assembled = assemblePart(level, safePos, part, pending.entityUUID, pending.entityNetworkId);
                if (assembled != null) {
                    RagdollBlockOwnership.assign(
                            assembled.subLevel(), pending.ragdollId, assembled.subLevel().getUniqueId(), part.partName());
                    Vec3 desired = pending.base
                            .add(pending.right.scale(part.xOffset()))
                            .add(0.0, part.yOffset(), 0.0)
                            .add(pending.forward.scale(-part.zOffset()));
                    Quaterniond partModelRot = new Quaterniond(
                            -part.rotQx(), -part.rotQy(), part.rotQz(), part.rotQw());
                    Quaterniond orientation = new Quaterniond(pending.baseOrientation).mul(partModelRot);
                    movePartTo(level, assembled.subLevel(), assembled.anchorPlotPos(), desired, orientation);
                    pending.assembled.add(new SpawnedPart(part, assembled.subLevel(), desired, assembled.anchorPlotPos(), pending.right, pending.forward));
                }
                pending.nextPartIndex++;
                budget--;
            }

            if (pending.nextPartIndex >= pending.parts.size()) {
                SPAWN_QUEUE.remove(pending);
                if (!pending.assembled.isEmpty()) {
                    finishAssembly(level, livingEntity, pending);
                } else {
                    CONVERTED_ENTITIES.remove(pending.entityUUID);
                    showRagdollSource(livingEntity);
                }
            }
        }
    }

    private static void finishAssembly(ServerLevel level, LivingEntity entity, PendingAssembly pending) {
        List<SpawnedPart> spawnedParts = pending.assembled;
        JointResult joints = attachJoints(level, spawnedParts);
        if (joints.representative() != null) {
            RESTORED_HANDLES.put(entity.getUUID(), joints.representative());
            RESTORED_UUIDS.add(entity.getUUID());
        }
        hideRagdollSource(entity);
        entity.getPersistentData().putUUID(RagdollBlockLifetime.SOURCE_SESSION, pending.ragdollId);
        for (SpawnedPart spawned : spawnedParts) {
            RagdollBlockLifetime.configure(
                    spawned.subLevel(), "MOB", entity.getUUID(), pending.durationTicks);
        }
        RAGDOLL_STATES.put(entity.getUUID(), new RagdollState(List.copyOf(spawnedParts), level.getGameTime(), entity.position(), pending.durationTicks));
        syncClientSourceState(entity, true);

        Map<String, UUID> partIds = new LinkedHashMap<>();
        Map<String, MobRagdollSavedData.PartInfo> partInfos = new LinkedHashMap<>();
        for (SpawnedPart spawned : spawnedParts) {
            PartSpawn ps = spawned.part();
            partIds.put(ps.partName(), spawned.subLevel().getUniqueId());
            partInfos.put(ps.partName(), new MobRagdollSavedData.PartInfo(
                    ps.role(), ps.pivotX(), ps.pivotY(), ps.pivotZ(),
                    (float) ps.xOffset(), (float) ps.yOffset(), (float) ps.zOffset(),
                    ps.rotQx(), ps.rotQy(), ps.rotQz(), ps.rotQw()));
        }
        MobRagdollSavedData.get(level).addEntry(
                entity.getUUID(),
                level.getGameTime(),
                pending.durationTicks,
                entity.position(),
                entity.getType().builtInRegistryHolder().key().location().toString(),
                pending.entitySnapshot,
                partInfos,
                partIds);

        if (pending.linearVelocity.lengthSqr() > 0.0 || pending.angularVelocity.lengthSqr() > 0.0) {
            SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
            if (physicsSystem != null) {
                for (SpawnedPart spawned : spawnedParts) {
                    RigidBodyHandle.of(spawned.subLevel()).addLinearAndAngularVelocity(
                            new Vector3d(pending.linearVelocity.x, pending.linearVelocity.y, pending.linearVelocity.z),
                            new Vector3d(pending.angularVelocity.x, pending.angularVelocity.y, pending.angularVelocity.z));
                }
            }
        }

        SablePlayerRagdoll.LOGGER.info("[mob-ragdoll] spawned {} Sable sublevels and {} joints for {}",
                spawnedParts.size(), joints.count(), entity.getType().builtInRegistryHolder().key().location());
    }

    private static void cleanupPartialAssembly(ServerLevel level, PendingAssembly pending) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        for (SpawnedPart sp : pending.assembled) {
            removeSubLevelIfPresent(container, sp.subLevel());
        }
    }

    private static boolean ownsRagdoll(RagdollState state, ServerLevel level) {
        if (state == null) return false;
        for (SpawnedPart part : state.parts()) {
            ServerSubLevel subLevel = part.subLevel();
            if (subLevel != null && subLevel.getLevel() == level) {
                return true;
            }
        }
        return false;
    }

    private static void discardRagdoll(ServerLevel level, UUID uuid) {
        if (level.getEntity(uuid) instanceof Entity entity) {
            syncClientSourceState(entity, false);
        }
        RagdollState state = RAGDOLL_STATES.remove(uuid);
        forgetJoints(state);
        CONVERTED_ENTITIES.remove(uuid);
        RESTORED_UUIDS.remove(uuid);
        RESTORED_HANDLES.remove(uuid);
        NEXT_IMPACT_DAMAGE_TICK.remove(uuid);
        NEXT_IMPACT_SOUND_TICK.remove(uuid);
        clearRestoreDeferral(uuid);

        MobRagdollSavedData savedData = MobRagdollSavedData.get(level);
        MobRagdollSavedData.Entry saved = savedData.getEntry(uuid);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (saved != null) {
            removeSavedSubLevels(level, container, saved);
        }
        savedData.removeEntry(uuid);

        if (state != null) {
            for (SpawnedPart spawned : state.parts()) {
                ServerSubLevel subLevel = spawned.subLevel();
                LAST_VELOCITIES.remove(subLevel);
                if (subLevel != null && !subLevel.isRemoved()) {
                    if (saved == null) RagdollCleanup.removeLimb(level, limbId(spawned));
                }
            }
        }

        if (level.getEntity(uuid) instanceof LivingEntity living && !living.isRemoved()) {
            living.kill();
        }
    }

    private static void expireSavedRagdoll(ServerLevel level, UUID uuid) {
        expireSavedRagdoll(level, uuid, MobRagdollEndEvent.Reason.EXPIRED);
    }

    private static void expireSavedRagdoll(ServerLevel level, UUID uuid, MobRagdollEndEvent.Reason reason) {
        if (deferRestoreIfProtected(level, uuid, reason)) {
            return;
        }
        MobRagdollSavedData savedData = MobRagdollSavedData.get(level);
        MobRagdollSavedData.Entry saved = savedData.getEntry(uuid);
        RagdollState state = RAGDOLL_STATES.remove(uuid);
        forgetJoints(state);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        CONVERTED_ENTITIES.remove(uuid);
        RESTORED_UUIDS.remove(uuid);
        RESTORED_HANDLES.remove(uuid);
        NEXT_IMPACT_DAMAGE_TICK.remove(uuid);
        NEXT_IMPACT_SOUND_TICK.remove(uuid);
        clearRestoreDeferral(uuid);

        LivingEntity target = level.getEntity(uuid) instanceof LivingEntity loaded ? loaded
                : (saved == null || saved.mobless() ? null : recreateEntity(level, uuid, saved));
        if (target != null) {
            syncClientSourceState(target, false);
            Vec3 exitVelocity = state == null ? Vec3.ZERO : rootVelocity(state);
            NeoForge.EVENT_BUS.post(new MobRagdollEndEvent(target, exitVelocity, reason));
            if (target.isPassenger()) {
                target.stopRiding();
            }
            showRagdollSource(target);
        }
        if (saved != null) {
            removeSavedSubLevels(level, container, saved);
        }
        savedData.removeEntry(uuid);
        if (state != null) {
            for (SpawnedPart spawned : state.parts()) {
                ServerSubLevel subLevel = spawned.subLevel();
                LAST_VELOCITIES.remove(subLevel);
                if (subLevel != null && !subLevel.isRemoved()) {
                    if (saved == null) RagdollCleanup.removeLimb(level, limbId(spawned));
                }
            }
        }
    }

    public static void syncClientSourceState(ServerPlayer player, Entity entity) {
        if (entity.level() instanceof ServerLevel level && isActiveOrSavedRagdollSource(level, entity.getUUID())) {
            PacketDistributor.sendToPlayer(player, new MobRagdollSourceStatePacket(entity.getId(), true));
        }
    }

    private static void syncClientSourceState(Entity entity, boolean hidden) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, new MobRagdollSourceStatePacket(entity.getId(), hidden));
    }

    private static void removeSavedSubLevels(ServerLevel level, SubLevelContainer container, MobRagdollSavedData.Entry saved) {
        RagdollCleanup.removeLimbs(level, new java.util.HashSet<>(saved.partIds().values()));
    }

    private static void removeSubLevelIfPresent(SubLevelContainer container, ServerSubLevel subLevel) {
        if (subLevel != null) RagdollCleanup.removePart(subLevel.getLevel(), subLevel);
    }

    private static LivingEntity recreateEntity(ServerLevel level, UUID uuid, MobRagdollSavedData.Entry saved) {
        if (saved.entityType() == null || saved.entityType().isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(saved.entityType());
        if (id == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        Entity entity = type.create(level);
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        CompoundTag tag = saved.entityData().copy();
        tag.remove("UUID");
        living.load(tag);
        living.setUUID(uuid);
        living.moveTo(saved.preRagdollPos().x, saved.preRagdollPos().y, saved.preRagdollPos().z, living.getYRot(), living.getXRot());
        if (level.addFreshEntity(living)) {
            SablePlayerRagdoll.LOGGER.info("[mob-ragdoll] recreated source entity {} for expired saved ragdoll", uuid);
            return living;
        }
        return null;
    }

    private static void applyImpactDamage(ServerLevel level, UUID uuid, RagdollState state,
                                           SubLevelPhysicsSystem physicsSystem, long now) {
        Long nextTick = NEXT_IMPACT_DAMAGE_TICK.get(uuid);
        if (nextTick != null && now < nextTick) {
            for (SpawnedPart spawned : state.parts()) {
                storeVelocity(spawned.subLevel());
            }
            return;
        }

        double maxDelta = 0.0;
        ServerSubLevel impactSubLevel = null;
        for (SpawnedPart spawned : state.parts()) {
            ServerSubLevel subLevel = spawned.subLevel();
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }
            Vector3d currentVelocity = new Vector3d();
            RigidBodyHandle.of(subLevel).getLinearVelocity(currentVelocity);
            Vector3d previous = LAST_VELOCITIES.get(subLevel);
            if (previous != null) {
                double delta = previous.sub(currentVelocity, new Vector3d()).length();
                if (delta > maxDelta) {
                    maxDelta = delta;
                    impactSubLevel = subLevel;
                }
            }
            LAST_VELOCITIES.put(subLevel, new Vector3d(currentVelocity));
        }

        if (maxDelta >= IMPACT_DAMAGE_THRESHOLD) {
            if (level.getEntity(uuid) instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
                float damage = (float) Math.min(IMPACT_DAMAGE_MAX,
                        (maxDelta - IMPACT_DAMAGE_THRESHOLD) * IMPACT_DAMAGE_MULTIPLIER);
                livingEntity.hurt(livingEntity.damageSources().flyIntoWall(), damage);
                if (livingEntity instanceof Creeper creeper) {
                    creeper.ignite();
                }
            }
            NEXT_IMPACT_DAMAGE_TICK.put(uuid, now + IMPACT_DAMAGE_COOLDOWN_TICKS);
        }

        if (maxDelta >= IMPACT_FEEDBACK_THRESHOLD && impactSubLevel != null) {
            if (NEXT_IMPACT_SOUND_TICK.getOrDefault(uuid, 0L) <= now) {
                NEXT_IMPACT_SOUND_TICK.put(uuid, now + IMPACT_SOUND_COOLDOWN_TICKS);
                Vec3 impactPos = impactSubLevel.logicalPose().transformPosition(
                        Vec3.atCenterOf(impactSubLevel.getPlot().getCenterBlock()));
                float volume = (float) Math.min(1.0, maxDelta / 20.0);
                float pitch = 0.8F + level.random.nextFloat() * 0.4F;
                boolean heavy = maxDelta >= IMPACT_DAMAGE_THRESHOLD;
                level.playSound(null, impactPos.x, impactPos.y, impactPos.z,
                        heavy ? net.minecraft.sounds.SoundEvents.PLAYER_BIG_FALL : net.minecraft.sounds.SoundEvents.PLAYER_SMALL_FALL,
                        net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch);
                int particleCount = (int) Math.min(20, maxDelta);
                for (int p = 0; p < particleCount; p++) {
                    level.sendParticles(
                            net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            impactPos.x + (level.random.nextDouble() - 0.5) * 0.5,
                            impactPos.y + level.random.nextDouble() * 0.5,
                            impactPos.z + (level.random.nextDouble() - 0.5) * 0.5,
                            1, 0.0, 0.0, 0.0, 0.01);
                }
            }
        }
    }

    private static final double KNOCKUP_STRENGTH = 4.0;

    public static void applyKnockup(UUID uuid) {
        RagdollState state = RAGDOLL_STATES.get(uuid);
        if (state == null) return;
        for (SpawnedPart spawned : state.parts()) {
            ServerSubLevel subLevel = spawned.subLevel();
            if (subLevel == null || subLevel.isRemoved()) continue;
            try {
                RigidBodyHandle.of(subLevel).addLinearAndAngularVelocity(
                        new Vector3d(0, KNOCKUP_STRENGTH, 0),
                        new Vector3d(
                                (Math.random() - 0.5) * 2.0,
                                (Math.random() - 0.5) * 2.0,
                                (Math.random() - 0.5) * 2.0));
            } catch (Throwable ignored) {}
        }
    }

    public static void applyKnockupForPart(BlockPos partPos) {
        for (var entry : RAGDOLL_STATES.entrySet()) {
            for (SpawnedPart spawned : entry.getValue().parts()) {
                if (spawned.plotPos().equals(partPos)) {
                    applyKnockup(entry.getKey());
                    return;
                }
            }
        }
    }

    private static void storeVelocity(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }
        Vector3d velocity = new Vector3d();
        RigidBodyHandle.of(subLevel).getLinearVelocity(velocity);
        LAST_VELOCITIES.put(subLevel, velocity);
    }

    private static JointResult attachJoints(ServerLevel level, List<SpawnedPart> parts) {
        SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
        if (physicsSystem == null || parts.size() < 2) {
            return new JointResult(0, null);
        }

        SpawnedPart root = selectRoot(parts);
        int attached = 0;
        PhysicsConstraintHandle representative = null;
        for (SpawnedPart child : parts) {
            if (child == root) {
                continue;
            }
            SpawnedPart parent = selectParent(child, parts, root);
            if (parent == null || parent.subLevel() == child.subLevel()) {
                continue;
            }

            Vec3 joint = child.part().pivotOffset();
            Vector3d parentAnchor = plotAnchor(parent, joint.subtract(parent.part().centerOffset()));
            Vector3d childAnchor = plotAnchor(child, joint.subtract(child.part().centerOffset()));
            Quaterniond parentRot = new Quaterniond(parent.subLevel().logicalPose().orientation());
            Quaterniond childRot = new Quaterniond(child.subLevel().logicalPose().orientation());
            Quaterniond parentFrame = parentRot.invert().mul(childRot);
            try {
                PhysicsConstraintConfiguration<?> config = SableConstraintCompat.generic(
                        parentAnchor,
                        childAnchor,
                        parentFrame,
                        new Quaterniond(),
                        Set.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z)
                );
                PhysicsConstraintHandle handle = SableConstraintCompat.addConstraint(physicsSystem.getPipeline(), parent.subLevel(), child.subLevel(), config);
                handle.setContactsEnabled(false);
                tuneAngularJoint(handle);
                JOINT_BY_CHILD.put(limbId(child), handle);
                if (representative == null) {
                    representative = handle;
                }
                attached++;
            } catch (Throwable error) {
                SablePlayerRagdoll.LOGGER.warn("[mob-ragdoll] failed to attach {} to {}: {}", child.part().role(), parent.part().role(), error.toString());
            }
        }

        return new JointResult(attached, representative);
    }

    private static void tuneAngularJoint(PhysicsConstraintHandle handle) {
        for (ConstraintJointAxis axis : Set.of(
                ConstraintJointAxis.ANGULAR_X,
                ConstraintJointAxis.ANGULAR_Y,
                ConstraintJointAxis.ANGULAR_Z)) {
            handle.setMotor(axis, 0.0, JOINT_ANGULAR_STIFFNESS, JOINT_ANGULAR_DAMPING, false, 0.0);
        }
    }

    private static SpawnedPart selectRoot(List<SpawnedPart> parts) {
        return parts.stream()
                .filter(part -> part.part().role() == MobPartRole.TORSO)
                .max(Comparator.comparingDouble(part -> part.part().volume()))
                .orElseGet(() -> parts.stream()
                        .max(Comparator.comparingDouble(part -> part.part().volume()))
                        .orElse(parts.getFirst()));
    }

    private static SpawnedPart selectParent(SpawnedPart child, List<SpawnedPart> parts, SpawnedPart root) {
        if (child.part().parentName() != null) {
            for (SpawnedPart candidate : parts) {
                if (candidate != child && child.part().parentName().equals(candidate.part().partName())) {
                    return candidate;
                }
            }
        }
        if (child.part().role() == MobPartRole.HEAD) {
            return nearest(child, parts, Set.of(MobPartRole.TORSO)).orElse(root);
        }
        if (child.part().role() == MobPartRole.TORSO) {
            return root;
        }
        return nearest(child, parts, Set.of(MobPartRole.TORSO)).orElse(root);
    }

    private static java.util.Optional<SpawnedPart> nearest(SpawnedPart child, List<SpawnedPart> parts, Set<MobPartRole> roles) {
        return parts.stream()
                .filter(part -> part != child)
                .filter(part -> roles.contains(part.part().role()))
                .min(Comparator.comparingDouble(part -> part.part().pivotOffset().distanceToSqr(child.part().pivotOffset())));
    }

    private static Vector3d plotAnchor(SpawnedPart part, Vec3 localOffset) {
        BlockPos plot = part.plotPos();
        Vector3d offset = new Vector3d(-localOffset.x, localOffset.y, localOffset.z);
        PartSpawn spawn = part.part();
        Quaterniond partModelRot = new Quaterniond(-spawn.rotQx(), -spawn.rotQy(), spawn.rotQz(), spawn.rotQw());
        if (isUsableRotation(partModelRot)) {
            partModelRot.normalize().invert().transform(offset);
        }
        return new Vector3d(plot.getX() + 0.5 + offset.x, plot.getY() + 0.5 + offset.y, plot.getZ() + 0.5 + offset.z);
    }

    private static boolean isUsableRotation(Quaterniond q) {
        return Double.isFinite(q.x) && Double.isFinite(q.y) && Double.isFinite(q.z) && Double.isFinite(q.w)
                && q.lengthSquared() > 1.0E-6;
    }

    private static AssembledPart assemblePart(ServerLevel level, BlockPos pos, PartSpawn part, UUID sourceEntityId, int sourceEntityNetworkId) {
        Set<BlockPos> blocks = MobRagdollGeometry.collisionBlocks(pos, part);
        Map<BlockPos, BlockState> previousStates = new LinkedHashMap<>();
        for (BlockPos blockPos : blocks) {
            previousStates.put(blockPos, level.getBlockState(blockPos));
            int xOffset = blockPos.getX() - pos.getX();
            int yOffset = blockPos.getY() - pos.getY();
            int zOffset = blockPos.getZ() - pos.getZ();
            BlockState partState = MobRagdollBlocks.MOB_RAGDOLL_PART.get().defaultBlockState()
                    .setValue(MobRagdollPartBlock.X_SIZE, MobRagdollGeometry.slicePixels(MobRagdollGeometry.collisionPixels(part.xSize()), xOffset))
                    .setValue(MobRagdollPartBlock.Y_SIZE, MobRagdollGeometry.slicePixels(MobRagdollGeometry.collisionPixels(part.ySize()), yOffset))
                    .setValue(MobRagdollPartBlock.Z_SIZE, MobRagdollGeometry.slicePixels(MobRagdollGeometry.collisionPixels(part.zSize()), zOffset));
            level.setBlock(blockPos, partState, 3);
            if (level.getBlockEntity(blockPos) instanceof MobRagdollPartBlockEntity blockEntity) {
                if (blockPos.equals(pos)) {
                    ResourceLocation texture = ResourceLocation.tryParse(part.texture());
                    blockEntity.configure(
                            texture == null ? ResourceLocation.withDefaultNamespace("textures/block/light_blue_stained_glass.png") : texture,
                            part.quads().stream()
                                    .map(quad -> new MobRagdollPartBlockEntity.Quad(
                                            quad.vertices().stream()
                                                    .map(vertex -> new MobRagdollPartBlockEntity.Vertex(vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v()))
                                                    .toList(),
                                            quad.normalX(),
                                            quad.normalY(),
                                            quad.normalZ()
                            ))
                                    .toList(),
                            ResourceLocation.tryParse(part.entityType()),
                            sourceEntityId,
                            sourceEntityNetworkId,
                            part.partName(),
                            part.keepPartNames(),
                            part.variantData(),
                            part.baby(),
                            part.renderScale(),
                            part.renderQx(),
                            part.renderQy(),
                            part.renderQz(),
                            part.renderQw(),
                            part.role(),
                            part.xSize(),
                            part.ySize(),
                            part.zSize()
                    );
                } else {
                    blockEntity.configureCollisionOnly();
                }
            }
        }

        try {
            ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, pos, blocks, BoundingBox3i.from(blocks));
            if (subLevel != null && !subLevel.isRemoved()) {
                for (BlockPos blockPos : blocks) {
                    level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
                }
                return new AssembledPart(subLevel, subLevel.getPlot().getCenterBlock());
            }
        } catch (Throwable error) {
            SablePlayerRagdoll.LOGGER.warn("[mob-ragdoll] failed to assemble {} part at {}: {}", part.role(), pos, error.toString());
        }

        previousStates.forEach((blockPos, previous) -> level.setBlock(blockPos, previous, 3));
        return null;
    }

    private static void movePartTo(ServerLevel level, ServerSubLevel subLevel, BlockPos anchorPlotPos, Vec3 desiredCenter, Quaterniond orientation) {
        SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
        if (physicsSystem == null) {
            return;
        }

        Vec3 currentCenter = subLevel.logicalPose().transformPosition(Vec3.atCenterOf(anchorPlotPos));
        Vec3 delta = desiredCenter.subtract(currentCenter);
        Vector3d position = new Vector3d(subLevel.logicalPose().position()).add(delta.x, delta.y, delta.z);
        subLevel.logicalPose().position().set(position);
        subLevel.logicalPose().orientation().set(orientation);
        physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(), subLevel.logicalPose().orientation());
        subLevel.updateLastPose();
    }

    public record PartSpawn(
            MobPartRole role,
            String entityType,
            String partName,
            List<String> keepPartNames,
            String parentName,
            CompoundTag variantData,
            boolean baby,
            float renderScale,
            double xOffset,
            double yOffset,
            double zOffset,
            float pivotX,
            float pivotY,
            float pivotZ,
            float rotQx,
            float rotQy,
            float rotQz,
            float rotQw,
            float renderQx,
            float renderQy,
            float renderQz,
            float renderQw,
            float xSize,
            float ySize,
            float zSize,
            String texture,
            List<Quad> quads
    ) {
        Vec3 centerOffset() {
            return new Vec3(this.xOffset, this.yOffset, this.zOffset);
        }

        Vec3 pivotOffset() {
            return new Vec3(this.pivotX, this.pivotY, this.pivotZ);
        }

        double volume() {
            return this.xSize * this.ySize * this.zSize;
        }
    }

    public record Quad(List<Vertex> vertices, float normalX, float normalY, float normalZ) {
    }

    public record Vertex(float x, float y, float z, float u, float v) {
    }

    private record SpawnedPart(PartSpawn part, ServerSubLevel subLevel, Vec3 worldCenter, BlockPos plotPos, Vec3 right, Vec3 forward) {
    }

    private record AssembledPart(ServerSubLevel subLevel, BlockPos anchorPlotPos) {
    }

    private record PendingLaunch(Vec3 linear, Vec3 angular, MobRagdollLaunchOptions options, long requestedTick, ServerLevel level) {
    }

    private record RagdollState(List<SpawnedPart> parts, long spawnedAtTick, Vec3 preRagdollPos, int durationTicks) {
    }

    private record JointResult(int count, PhysicsConstraintHandle representative) {
    }

    private static final class PendingAssembly {
        final UUID ragdollId = UUID.randomUUID();
        final ServerLevel level;
        final UUID entityUUID;
        final int entityNetworkId;
        final List<PartSpawn> parts;
        final Vec3 base;
        final BlockPos baseBlockPos;
        final Vec3 right;
        final Vec3 forward;
        final Quaterniond baseOrientation;
        final Vec3 linearVelocity;
        final Vec3 angularVelocity;
        final int durationTicks;
        final CompoundTag entitySnapshot;
        int nextPartIndex = 0;
        final List<SpawnedPart> assembled = new ArrayList<>();

        PendingAssembly(ServerLevel level, UUID entityUUID, int entityNetworkId, List<PartSpawn> parts,
                        Vec3 base, BlockPos baseBlockPos, Vec3 right, Vec3 forward, Quaterniond baseOrientation,
                        Vec3 linearVelocity, Vec3 angularVelocity, int durationTicks,
                        CompoundTag entitySnapshot) {
            this.level = level;
            this.entityUUID = entityUUID;
            this.entityNetworkId = entityNetworkId;
            this.parts = parts;
            this.base = base;
            this.baseBlockPos = baseBlockPos;
            this.right = right;
            this.forward = forward;
            this.baseOrientation = baseOrientation;
            this.linearVelocity = linearVelocity;
            this.angularVelocity = angularVelocity;
            this.durationTicks = durationTicks;
            this.entitySnapshot = entitySnapshot;
        }
    }
}
