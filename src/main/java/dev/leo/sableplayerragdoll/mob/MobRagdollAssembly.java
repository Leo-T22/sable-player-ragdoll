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
import dev.leo.sableplayerragdoll.api.RagdollWailingOptions;
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
import net.minecraft.util.RandomSource;
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
    private static final double JOINT_ANGULAR_STIFFNESS = 20.0;
    private static final double JOINT_ANGULAR_DAMPING = 20.0;
    private static final double WAILING_DAMPING = 8.0;
    private static final String WAILING_KEY = "Wailing";
    private static final String RECOVERY_KEY = "Recovery";
    private static final int RECOVERY_DURATION_TICKS = 24;
    private static final int RECOVERY_LEAD_TICKS = 8;
    private static final int RECOVERY_SOURCE_GRACE_TICKS = 20;
    private static final double RECOVERY_UPWARD_KICK = 2.5;
    private static final double RECOVERY_ANGULAR_SPEED = 16.0;
    private static final double RECOVERY_JOINT_STIFFNESS = 160.0;
    private static final double RECOVERY_JOINT_DAMPING = 36.0;
    private static final int GRAB_RESTORE_PROTECTION_TICKS = 200;
    private static final int DEFERRED_RESTORE_AFTER_RELEASE_TICKS = 30;
    private static final Map<UUID, Float> CLIENT_BODY_YAW = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> GRAB_COUNTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> GRAB_PROTECTED_UNTIL = new ConcurrentHashMap<>();
    private record DeferredRestore(ServerLevel level, long tick, MobRagdollEndEvent.Reason reason) {}
    private static final Map<UUID, DeferredRestore> DEFERRED_RESTORES = new ConcurrentHashMap<>();
    private static final Map<UUID, LiveJoint> JOINT_BY_CHILD = new ConcurrentHashMap<>();
    private static final Map<UUID, CachedAssemblyState> ASSEMBLY_STATE_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, JointCheckSchedule> JOINT_CHECK_SCHEDULES = new ConcurrentHashMap<>();
    private static final String MOBLESS_SOURCE = "sable_mobless_temporary_source";
    public static final int DEFAULT_MOBLESS_DURATION_TICKS = Integer.MAX_VALUE;

    private record LiveJoint(ServerLevel level, UUID session, UUID parentLimb,
                             ServerSubLevel parentBody, ServerSubLevel childBody,
                             BlockPos parentPos, BlockPos childPos, PhysicsConstraintHandle handle) {}
    private record CachedAssemblyState(ServerLevel level, long tick,
                                       MobRagdollAssemblyData.Entry data, RagdollState state) {}
    private record JointCheckSchedule(ServerLevel level, long nextTick, long lastSeenTick) {}

    private MobRagdollAssembly() {
    }

    public static void setClientBodyYaw(UUID uuid, float bodyYaw) {
        CLIENT_BODY_YAW.put(uuid, bodyYaw);
    }

    public static void resetRuntimeState() {
        PENDING_LAUNCHES.clear();
        SPAWN_QUEUE.clear();
        CLIENT_BODY_YAW.clear();
        NEXT_IMPACT_DAMAGE_TICK.clear();
        NEXT_IMPACT_SOUND_TICK.clear();
        LAST_VELOCITIES.clear();
        GRAB_COUNTS.clear();
        GRAB_PROTECTED_UNTIL.clear();
        DEFERRED_RESTORES.clear();
        ASSEMBLY_STATE_CACHE.clear();
        JOINT_CHECK_SCHEDULES.clear();
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
        spawn(level, entity, parts, linearVelocity, angularVelocity, durationTicks,
                MobRagdollLaunchOptions.DEFAULT_CORPSE_DURATION_TICKS, false, null, UUID.randomUUID());
    }

    private static void spawn(ServerLevel level, LivingEntity entity, List<PartSpawn> parts,
                              Vec3 linearVelocity, Vec3 angularVelocity, int durationTicks,
                              int corpseDurationTicks, boolean fallApartOnDeath,
                              RagdollWailingOptions wailing, UUID session) {
        if (parts.isEmpty()) {
            return;
        }
        if (!MobRagdollWhitelist.isAllowed(level, entity.getType())) {
            return;
        }
        if (isConverted(entity) || SPAWN_QUEUE.stream().anyMatch(pending -> pending.entityUUID.equals(entity.getUUID()))) return;
        MobRagdollSourceRecovery.begin(entity, session, level.getGameTime() + (long) durationTicks);
        fallApartOnDeath |= entity.getType().is(MobRagdollEntityTags.FALL_APART_ON_DEATH);

        Float clientYaw = CLIENT_BODY_YAW.remove(entity.getUUID());
        float yaw = clientYaw != null ? clientYaw
                : (entity instanceof Mob mob ? mob.yBodyRot : entity.getYRot());
        double yawRadians = Math.toRadians(yaw);
        Vec3 right = new Vec3(Math.cos(yawRadians), 0.0, Math.sin(yawRadians));
        Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        Quaterniond baseOrientation = new Quaterniond().rotateY(Math.toRadians(180.0F - yaw));

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
                linearVelocity, angularVelocity, durationTicks, corpseDurationTicks,
                fallApartOnDeath, wailing, session, level.getGameTime()));
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
        if (entity.isRemoved() || isPendingOrConverted(entity)) {
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
        PENDING_LAUNCHES.put(uuid, new PendingLaunch(startEvent.velocity(), angular, resolved, level.getGameTime(), level, UUID.randomUUID()));
        PacketDistributor.sendToPlayersTrackingEntity(entity,
                new MobRagdollLaunchRequestPacket(entity.getId(), !entity.isAlive()));
        return true;
    }

    public static boolean consumePendingLaunch(ServerLevel level, LivingEntity entity, List<PartSpawn> parts) {
        PendingLaunch pending = PENDING_LAUNCHES.remove(entity.getUUID());
        if (pending == null || pending.level() != level) {
            return false;
        }
        spawn(level, entity, parts, pending.linear(), pending.angular(),
                pending.options().durationTicks(), pending.options().corpseDurationTicks(),
                pending.options().fallApartOnDeath(), pending.options().wailing(), pending.session());
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
        living.getPersistentData().putLong(MOBLESS_SOURCE, level.getGameTime() + PENDING_LAUNCH_TIMEOUT_TICKS);
        if (!level.addFreshEntity(living)) {
            return null;
        }
        int duration = durationTicks > 0 ? durationTicks : DEFAULT_MOBLESS_DURATION_TICKS;
        if (!requestLaunch(level, living, linear, Vec3.ZERO, MobRagdollLaunchOptions.builder().durationTicks(duration).build())) {
            living.discard();
            return null;
        }
        return living.getUUID();
    }

    private static MobRagdollPartBlockEntity findMobPart(ServerLevel level, UUID id) {
        MobRagdollPartBlockEntity candidate = null;
        for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
            if (!(be instanceof MobRagdollPartBlockEntity mob) || !mob.renderAnchor()) continue;
            var identity = mob.ragdollIdentity();
            if (id.equals(identity.limb())) return mob;
            var body = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, mob.getBlockPos());
            if (body != null && id.equals(body.getUniqueId())) {
                if (candidate != null && !java.util.Objects.equals(candidate.ragdollIdentity().owner(), identity.owner())) return null;
                candidate = mob;
            }
        }
        return candidate;
    }

    public static boolean releaseForExternalRemoval(ServerLevel level, UUID partId) {
        var block = findMobPart(level, partId);
        if (block == null || block.ragdollIdentity().owner() == null) return false;
        terminate(level, block.ragdollIdentity().owner(), block.ragdollIdentity().source(),
                MobRagdollEndEvent.Reason.RELEASED, false);
        return true;
    }

    public static boolean removeBySubLevel(ServerLevel level, UUID id, boolean smokePuff) {
        var block = findMobPart(level, id);
        UUID session = block == null ? null : block.ragdollIdentity().owner();
        UUID source = block == null ? null : block.ragdollIdentity().source();
        if (session == null && block == null) {
            var entries = MobRagdollAssemblyData.loaded(level);
            var data = entries.get(id);
            if (data == null) data = entries.values().stream().filter(e -> id.equals(e.sourceId())).findFirst().orElse(null);
            if (data != null) { session = data.sessionId(); source = data.sourceId(); }
        }
        if (session == null) return removeLooseSubLevel(level, id);
        if (smokePuff && block != null && dev.ryanhcode.sable.Sable.HELPER.getContaining(level, block.getBlockPos()) instanceof ServerSubLevel body)
            RagdollRegistry.emitRemovalPuff(level, body);
        terminate(level, session, source, MobRagdollEndEvent.Reason.ENTITY_REMOVED, true);
        return true;
    }

    public static UUID dismemberBySubLevel(ServerLevel level, UUID id) {
        var target = findMobPart(level, id);
        if (target == null || target.ragdollIdentity().severed()) return null;
        var identity = target.ragdollIdentity();
        if (identity.parent() == null) return null;
        UUID limb = identity.limb();
        removeJoint(limb);
        invalidateAssemblyState(identity.owner());
        RagdollBlockOwnership.sever(level, limb);
        return limb;
    }

    private static boolean removeLooseSubLevel(ServerLevel level, UUID subLevelId) {
        if (findMobPart(level, subLevelId) != null && RagdollBlockOwnership.hasLimb(level, subLevelId)) {
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
        LiveJoint joint = JOINT_BY_CHILD.remove(limb);
        if (joint != null && joint.handle().isValid()) joint.handle().remove();
    }

    private static UUID limbId(SpawnedPart part) {
        return RagdollBlockOwnership.limbAt(
                part.subLevel().getLevel(), part.plotPos(), part.subLevel().getUniqueId());
    }

    private static void forgetJoints(UUID session) {
        for (var entry : List.copyOf(JOINT_BY_CHILD.entrySet())) {
            if (session.equals(entry.getValue().session())) removeJoint(entry.getKey());
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
            return subLevel.logicalPose().transformPosition(Vec3.atCenterOf(selectRoot(state.parts()).plotPos()));
        } catch (Throwable ignored) {
            return state.preRagdollPos();
        }
    }

    private static Vec3 sourcePosition(RagdollState state, LivingEntity source) {
        SpawnedPart root = selectRoot(state.parts());
        Vec3 center = root.part().centerOffset();
        float yaw = bodyYaw(source);
        double yawRadians = Math.toRadians(yaw);
        Vec3 right = new Vec3(Math.cos(yawRadians), 0.0, Math.sin(yawRadians));
        Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        Vec3 rootOffset = right.scale(center.x)
                .add(0.0, center.y, 0.0)
                .add(forward.scale(-center.z));
        Vec3 position = rootPosition(state).subtract(rootOffset);
        BlockPos feet = BlockPos.containing(position);
        double floor = root.subLevel().getLevel().getBlockFloorHeight(feet);
        double floorY = feet.getY() + floor;
        if (Double.isFinite(floorY) && floorY > position.y && floorY - position.y <= 1.0) {
            return new Vec3(position.x, floorY, position.z);
        }
        return position;
    }

    private static float bodyYaw(LivingEntity source) {
        return source instanceof Mob mob ? mob.yBodyRot : source.getYRot();
    }

    private static void restoreRotation(LivingEntity source, float yaw) {
        source.setYRot(yaw);
        source.yRotO = yaw;
        source.yBodyRot = yaw;
        source.yBodyRotO = yaw;
        source.setYHeadRot(yaw);
        source.yHeadRotO = yaw;
    }

    public static void despawn(ServerLevel level, LivingEntity entity) {
        despawn(level, entity, MobRagdollEndEvent.Reason.RELEASED);
    }

    public static void despawn(ServerLevel level, LivingEntity entity, MobRagdollEndEvent.Reason reason) {
        UUID session = sessionId(entity);
        if (session != null) releaseSession(level, entity, session, reason);
    }

    public static UUID sessionId(LivingEntity entity) {
        var pending = PENDING_LAUNCHES.get(entity.getUUID());
        if (pending != null && pending.level() == entity.level()) return pending.session();
        return MobRagdollSourceRecovery.session(entity);
    }

    public static void releaseSession(ServerLevel level, LivingEntity entity, UUID session, MobRagdollEndEvent.Reason reason) {
        if (session == null) return;
        if (MobRagdollSourceRecovery.active(entity) && MobRagdollSourceRecovery.matches(entity, session)
                && deferRestoreIfProtected(level, session, reason)) return;
        var pending = PENDING_LAUNCHES.get(entity.getUUID());
        if (pending != null && session.equals(pending.session())) PENDING_LAUNCHES.remove(entity.getUUID());
        SPAWN_QUEUE.removeIf(p -> session.equals(p.ragdollId));
        if (beginRecovery(level, session, entity, reason)) return;
        terminate(level, session, entity.getUUID(), reason, false);
    }

    public static boolean isConverted(Entity entity) {
        return entity.level().isClientSide()
                ? dev.leo.sableplayerragdoll.mob.client.MobRagdollClientState.isHidden(entity)
                : entity instanceof LivingEntity living && MobRagdollSourceRecovery.active(living);
    }

    public static boolean isActiveOrSavedRagdollSource(ServerLevel level, UUID uuid) {
        return level.getEntity(uuid) instanceof LivingEntity living && MobRagdollSourceRecovery.active(living);
    }

    public static boolean hasPendingLaunch(UUID uuid) {
        return PENDING_LAUNCHES.containsKey(uuid);
    }

    public static boolean isRagdollPart(ServerLevel level, UUID subLevelId) {
        return findMobPart(level, subLevelId) != null;
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
        for (var entry : List.copyOf(DEFERRED_RESTORES.entrySet())) {
            var deferred = entry.getValue();
            if (deferred.level() != level || now < deferred.tick()) continue;
            clearRestoreDeferral(entry.getKey());
            var data = MobRagdollAssemblyData.loaded(level).get(entry.getKey());
            LivingEntity source = data != null && level.getEntity(data.sourceId()) instanceof LivingEntity living
                    ? living : null;
            if (source == null || !beginRecovery(level, entry.getKey(), source, deferred.reason())) {
                terminate(level, entry.getKey(), data == null ? null : data.sourceId(), deferred.reason(), false);
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
        if (MobRagdollSourceRecovery.hasRecord(entity)) {
            MobRagdollSourceRecovery.restore(entity);
            return;
        }
        entity.getPersistentData().remove(RagdollBlockLifetime.SOURCE_SESSION);
        if (entity instanceof Mob mob) {
            mob.setNoAi(false);
        }
        entity.setInvisible(false);
        entity.noPhysics = false;
        entity.refreshDimensions();
    }

    public static void hideLoadedRagdollSource(ServerLevel level, LivingEntity entity, boolean fromDisk) {
        if (fromDisk && entity.getPersistentData().contains(MOBLESS_SOURCE)) {
            entity.discard();
            return;
        }
        if (!MobRagdollSourceRecovery.hasRecord(entity)
                && entity.getPersistentData().hasUUID(RagdollBlockLifetime.SOURCE_SESSION)) {
            UUID session = entity.getPersistentData().getUUID(RagdollBlockLifetime.SOURCE_SESSION);
            var saved = MobRagdollAssemblyData.loaded(level).get(session);
            showRagdollSource(entity);
            long deadline = saved == null ? level.getGameTime() : saved.spawnedAtTick() + saved.durationTicks();
            MobRagdollSourceRecovery.begin(entity, session, deadline);
        }
        if (MobRagdollSourceRecovery.hasRecord(entity)) {
            if (MobRagdollSourceRecovery.active(entity)) hideRagdollSource(entity);
            else recoverSource(level, entity);
        }
    }

    private static void recoverSource(ServerLevel level, LivingEntity source) {
        UUID session = MobRagdollSourceRecovery.session(source);
        if (session == null) return;
        terminate(level, session, source.getUUID(), MobRagdollEndEvent.Reason.EXPIRED, false);
        if (MobRagdollSourceRecovery.matches(source, session)) {
            showRagdollSource(source);
            syncClientSourceState(source, false);
        }
    }

    public static InteractionResult interactWithPart(ServerLevel level, BlockPos partPos, Player player, InteractionHand hand) {
        LivingEntity target = sourceEntityForPart(level, partPos);
        return interactWithSource(level, target, player, hand);
    }

    public static InteractionResult interactWithPart(ServerLevel level, MobRagdollPartBlockEntity part, Player player, InteractionHand hand) {
        return interactWithSource(level, sourceEntityForPart(level, part.getBlockPos()), player, hand);
    }

    public static boolean attackPart(ServerLevel level, BlockPos partPos, Player player) {
        LivingEntity target = sourceEntityForPart(level, partPos);
        return attackSource(target, player);
    }

    public static boolean attackPart(ServerLevel level, MobRagdollPartBlockEntity part, Player player) {
        return attackSource(sourceEntityForPart(level, part.getBlockPos()), player);
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

    public static LivingEntity sourceEntityForPart(ServerLevel level, BlockPos partPos) {
        if (!(level.getBlockEntity(partPos) instanceof MobRagdollPartBlockEntity block)) return null;
        var id = block.ragdollIdentity();
        if (id.severed() || id.source() == null || id.owner() == null) return null;
        var source = level.getEntity(id.source());
        return source instanceof LivingEntity living && MobRagdollSourceRecovery.active(living)
                && MobRagdollSourceRecovery.matches(living, id.owner()) ? living : null;
    }

    public static boolean isPendingOrConverted(LivingEntity entity) {
        return isConverted(entity) || (PENDING_LAUNCHES.containsKey(entity.getUUID())
                && PENDING_LAUNCHES.get(entity.getUUID()).level() == entity.level());
    }

    public static long elapsedTicks(ServerLevel level, UUID session) {
        var data = MobRagdollAssemblyData.loaded(level).get(session);
        return data == null ? -1 : level.getGameTime() - data.spawnedAtTick();
    }

    public static Vec3 currentVelocity(ServerLevel level, UUID session) {
        var state = state(level, session);
        return state == null ? Vec3.ZERO : rootVelocity(state);
    }

    public static void applyWailing(ServerLevel level, UUID session, RagdollWailingOptions options) {
        RagdollWailingOptions resolved = options == null ? RagdollWailingOptions.defaults() : options;
        for (var entry : PENDING_LAUNCHES.entrySet()) {
            PendingLaunch pending = entry.getValue();
            if (!session.equals(pending.session())) continue;
            var current = pending.options();
            var updated = new MobRagdollLaunchOptions(current.durationTicks(), current.corpseDurationTicks(),
                    current.fallApartOnDeath(), resolved);
            PENDING_LAUNCHES.replace(entry.getKey(), pending, new PendingLaunch(
                    pending.linear(), pending.angular(), updated, pending.requestedTick(), pending.level(), pending.session()));
            return;
        }
        var state = state(level, session);
        if (state == null) return;
        var root = rootBlock(level, state);
        if (root == null) return;
        long start = level.getGameTime() + resolved.startDelayTicks();
        CompoundTag wailing = new CompoundTag();
        wailing.putLong("StartTick", start);
        wailing.putLong("EndTick", start + resolved.durationTicks());
        wailing.putDouble("Stiffness", resolved.stiffness());
        wailing.putInt("IntervalTicks", resolved.intervalTicks());
        wailing.putLong("Seed", level.random.nextLong());
        CompoundTag sessionTag = root.ragdollIdentity().session();
        if (sessionTag.contains(RECOVERY_KEY)) return;
        sessionTag.put(WAILING_KEY, wailing);
        root.ragdollIdentity().session(sessionTag);
        root.setChanged();
    }

    public static void stopWailing(ServerLevel level, UUID session) {
        for (var entry : PENDING_LAUNCHES.entrySet()) {
            PendingLaunch pending = entry.getValue();
            if (!session.equals(pending.session())) continue;
            var current = pending.options();
            var updated = new MobRagdollLaunchOptions(current.durationTicks(), current.corpseDurationTicks(),
                    current.fallApartOnDeath(), null);
            PENDING_LAUNCHES.replace(entry.getKey(), pending, new PendingLaunch(
                    pending.linear(), pending.angular(), updated, pending.requestedTick(), pending.level(), pending.session()));
            return;
        }
        restoreMobMotors(session);
        var state = state(level, session);
        if (state == null) return;
        var root = rootBlock(level, state);
        if (root == null) return;
        CompoundTag sessionTag = root.ragdollIdentity().session();
        sessionTag.remove(WAILING_KEY);
        root.ragdollIdentity().session(sessionTag);
        root.setChanged();
    }

    private static RagdollState state(ServerLevel level, UUID session) {
        if (session == null) return null;
        var cached = ASSEMBLY_STATE_CACHE.get(session);
        long now = level.getGameTime();
        if (cached != null && cached.level() == level && cached.tick() == now) return cached.state();
        var data = MobRagdollAssemblyData.loaded(level).get(session);
        var resolved = data == null ? null : state(level, data);
        if (data != null) ASSEMBLY_STATE_CACHE.put(session, new CachedAssemblyState(level, now, data, resolved));
        return resolved;
    }

    private static CachedAssemblyState state(ServerLevel level, UUID session, CompoundTag assembly,
                                             java.util.Collection<net.minecraft.world.level.block.entity.BlockEntity> blocks) {
        long now = level.getGameTime();
        var cached = ASSEMBLY_STATE_CACHE.get(session);
        if (cached != null && cached.level() == level && cached.tick() == now) return cached;
        var data = MobRagdollAssemblyData.decode(assembly, session);
        var resolved = state(level, data, blocks);
        var replacement = new CachedAssemblyState(level, now, data, resolved);
        ASSEMBLY_STATE_CACHE.put(session, replacement);
        return replacement;
    }

    private static void invalidateAssemblyState(UUID session) {
        if (session == null) return;
        ASSEMBLY_STATE_CACHE.remove(session);
        JOINT_CHECK_SCHEDULES.remove(session);
    }

    private static boolean beginJointCheck(ServerLevel level, UUID session) {
        long now = level.getGameTime();
        var schedule = JOINT_CHECK_SCHEDULES.get(session);
        if (schedule != null && schedule.level() == level && now < schedule.nextTick()) {
            JOINT_CHECK_SCHEDULES.put(session, new JointCheckSchedule(level, schedule.nextTick(), now));
            return false;
        }
        JOINT_CHECK_SCHEDULES.put(session, new JointCheckSchedule(level, now + 10 + level.random.nextInt(11), now));
        return true;
    }

    private static RagdollState state(ServerLevel level, MobRagdollAssemblyData.Entry data) {
        return state(level, data, RagdollBlockOwnership.loadedBlocks(level));
    }

    private static RagdollState state(ServerLevel level, MobRagdollAssemblyData.Entry data,
                                      java.util.Collection<net.minecraft.world.level.block.entity.BlockEntity> blocks) {
        List<SpawnedPart> parts = new ArrayList<>();
        for (var be : blocks) {
            if (!(be instanceof MobRagdollPartBlockEntity mob) || !mob.renderAnchor()) continue;
            var id = mob.ragdollIdentity();
            if (be.isRemoved() || !level.hasChunkAt(be.getBlockPos()) || level.getBlockEntity(be.getBlockPos()) != be
                    || !data.sessionId().equals(id.owner()) || id.severed()) continue;
            var info = data.partInfos().get(id.kind());
            if (info == null) continue;
            var body = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, mob.getBlockPos());
            if (!(body instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) continue;
            PartGeometry geometry = new PartGeometry(info.role(), id.kind(), null,
                    new Vec3(info.centerX(), info.centerY(), info.centerZ()), new Vec3(info.pivotX(), info.pivotY(), info.pivotZ()),
                    info.rotQx(), info.rotQy(), info.rotQz(), info.rotQw(), mob.xSize() * mob.ySize() * mob.zSize());
            parts.add(new SpawnedPart(geometry, subLevel, mob.getBlockPos()));
        }
        return parts.isEmpty() ? null : new RagdollState(List.copyOf(parts), data.spawnedAtTick(), data.preRagdollPos(), data.durationTicks());
    }

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
        ASSEMBLY_STATE_CACHE.entrySet().removeIf(entry ->
                entry.getValue().level() == level && entry.getValue().tick() < now);
        JOINT_CHECK_SCHEDULES.entrySet().removeIf(entry ->
                entry.getValue().level() == level && entry.getValue().lastSeenTick() < now - 40);
        drainSpawnQueue(level);
        runDeferredRestores(level, now);
        PENDING_LAUNCHES.values().removeIf(p -> p.level() == level && now - p.requestedTick() > PENDING_LAUNCH_TIMEOUT_TICKS);
    }

    public static void tickSource(ServerLevel level, LivingEntity source) {
        if (source.getPersistentData().contains(MOBLESS_SOURCE)
                && level.getGameTime() >= source.getPersistentData().getLong(MOBLESS_SOURCE)) {
            source.discard();
            return;
        }
        if (!MobRagdollSourceRecovery.hasRecord(source)) return;
        if (!source.isAlive()) {
            retainCorpse(level, source);
        } else if (!MobRagdollSourceRecovery.active(source)) {
            UUID session = MobRagdollSourceRecovery.session(source);
            if (session == null || !beginRecovery(level, session, source, MobRagdollEndEvent.Reason.EXPIRED)) {
                recoverSource(level, source);
            }
        }
    }

    private static void retainCorpse(ServerLevel level, LivingEntity source) {
        UUID session = MobRagdollSourceRecovery.session(source);
        if (session == null) return;
        var data = MobRagdollAssemblyData.loaded(level).get(session);
        int corpseDuration = data == null
                ? MobRagdollLaunchOptions.DEFAULT_CORPSE_DURATION_TICKS
                : data.corpseDurationTicks();
        boolean fallApart = data != null && data.fallApartOnDeath();
        long now = level.getGameTime();
        long deadline = now + corpseDuration;
        var state = state(level, session);
        Vec3 velocity = state == null ? Vec3.ZERO : rootVelocity(state);

        clearRestoreDeferral(session);
        NEXT_IMPACT_DAMAGE_TICK.remove(source.getUUID());
        NEXT_IMPACT_SOUND_TICK.remove(source.getUUID());
        cancelRecovery(level, session);
        if (fallApart) forgetJoints(session);
        for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
            if (!(be instanceof MobRagdollPartBlockEntity mob)) continue;
            var identity = mob.ragdollIdentity();
            if (!session.equals(identity.owner()) || identity.severed()) continue;
            identity.lifetime(now, "TIMED", null, deadline);
            if (fallApart) identity.parent(null);
            be.setChanged();
        }

        MobRagdollSourceRecovery.clear(source);
        NeoForge.EVENT_BUS.post(new MobRagdollEndEvent(source, velocity, MobRagdollEndEvent.Reason.ENTITY_DEATH));
    }

    private static boolean beginRecovery(ServerLevel level, UUID session, LivingEntity source,
                                         MobRagdollEndEvent.Reason reason) {
        if (!source.isAlive() || !MobRagdollSourceRecovery.matches(source, session)) return false;
        RagdollState state = state(level, session);
        MobRagdollPartBlockEntity root = state == null ? null : rootBlock(level, state);
        if (root == null) return false;

        CompoundTag sessionTag = root.ragdollIdentity().session();
        if (sessionTag.contains(RECOVERY_KEY)) {
            return level.getGameTime() < sessionTag.getCompound(RECOVERY_KEY).getLong("EndTick");
        }

        SpawnedPart rootPart = selectRoot(state.parts());
        float yaw = bodyYaw(source);
        Quaterniond target = new Quaterniond().rotateY(Math.toRadians(180.0F - yaw));
        Quaterniond modelRotation = new Quaterniond(-rootPart.part().rotQx(), -rootPart.part().rotQy(),
                rootPart.part().rotQz(), rootPart.part().rotQw());
        if (isUsableRotation(modelRotation)) target.mul(modelRotation.normalize());

        long now = level.getGameTime();
        long end = now + RECOVERY_DURATION_TICKS;
        CompoundTag recovery = new CompoundTag();
        recovery.putLong("StartTick", now);
        recovery.putLong("EndTick", end);
        recovery.putString("Reason", reason.name());
        recovery.putDouble("TargetX", target.x);
        recovery.putDouble("TargetY", target.y);
        recovery.putDouble("TargetZ", target.z);
        recovery.putDouble("TargetW", target.w);
        recovery.putDouble("LiftSpeed", Math.max(0.6, Math.min(1.4, source.getBbHeight())));
        sessionTag.remove(WAILING_KEY);
        sessionTag.put(RECOVERY_KEY, recovery);
        root.ragdollIdentity().session(sessionTag);
        root.setChanged();

        for (SpawnedPart part : state.parts()) {
            RigidBodyHandle partHandle = RigidBodyHandle.of(part.subLevel());
            if (partHandle.isValid()) {
                partHandle.addLinearAndAngularVelocity(
                        new Vector3d(0.0, RECOVERY_UPWARD_KICK, 0.0), new Vector3d());
            }
        }
        tuneRecoveryMotors(session);
        for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
            if (!(be instanceof MobRagdollPartBlockEntity mob)) continue;
            var identity = mob.ragdollIdentity();
            if (!session.equals(identity.owner()) || identity.severed()) continue;
            identity.lifetime(identity.createdAt(), "RECOVERING", source.getUUID(), end);
            be.setChanged();
        }
        MobRagdollSourceRecovery.extend(source, end + RECOVERY_SOURCE_GRACE_TICKS);
        return true;
    }

    private static void tickRecovery(ServerLevel level, MobRagdollPartBlockEntity rootBlock,
                                     RigidBodyHandle handle) {
        if (!handle.isValid()) return;
        CompoundTag recovery = rootBlock.ragdollIdentity().session().getCompound(RECOVERY_KEY);
        long start = recovery.getLong("StartTick");
        long end = recovery.getLong("EndTick");
        double duration = Math.max(1.0, end - start);
        double progress = Math.max(0.0, Math.min(1.0, (level.getGameTime() - start) / duration));

        Quaterniond current = new Quaterniond();
        var containing = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, rootBlock.getBlockPos());
        if (!(containing instanceof ServerSubLevel rootBody) || rootBody.isRemoved()) return;
        current.set(rootBody.logicalPose().orientation()).normalize();
        Quaterniond target = new Quaterniond(recovery.getDouble("TargetX"), recovery.getDouble("TargetY"),
                recovery.getDouble("TargetZ"), recovery.getDouble("TargetW")).normalize();
        Quaterniond error = target.mul(new Quaterniond(current).conjugate()).normalize();
        if (error.w < 0.0) error.set(-error.x, -error.y, -error.z, -error.w);

        Vector3d desiredAngular = new Vector3d(error.x, error.y, error.z);
        double sinHalfAngle = desiredAngular.length();
        if (sinHalfAngle > 1.0E-6) {
            double angle = 2.0 * Math.atan2(sinHalfAngle, Math.max(0.0, error.w));
            desiredAngular.mul(angle * RECOVERY_ANGULAR_SPEED / sinHalfAngle);
            if (desiredAngular.lengthSquared() > RECOVERY_ANGULAR_SPEED * RECOVERY_ANGULAR_SPEED) {
                desiredAngular.normalize(RECOVERY_ANGULAR_SPEED);
            }
        } else {
            desiredAngular.zero();
        }

        Vector3d currentLinear = handle.getLinearVelocity(new Vector3d());
        Vector3d currentAngular = handle.getAngularVelocity(new Vector3d());
        double verticalSpeed = progress < 0.55
                ? Math.max(currentLinear.y, recovery.getDouble("LiftSpeed"))
                : currentLinear.y;
        Vector3d desiredLinear = new Vector3d(currentLinear.x * 0.75, verticalSpeed, currentLinear.z * 0.75);
        handle.addLinearAndAngularVelocity(desiredLinear.sub(currentLinear), desiredAngular.sub(currentAngular));
    }

    private static CompoundTag recoveryTag(ServerLevel level, UUID session) {
        RagdollState state = state(level, session);
        MobRagdollPartBlockEntity root = state == null ? null : rootBlock(level, state);
        if (root == null || !root.ragdollIdentity().session().contains(RECOVERY_KEY)) return null;
        return root.ragdollIdentity().session().getCompound(RECOVERY_KEY);
    }

    private static MobRagdollEndEvent.Reason recoveryReason(CompoundTag recovery) {
        try {
            return MobRagdollEndEvent.Reason.valueOf(recovery.getString("Reason"));
        } catch (IllegalArgumentException ignored) {
            return MobRagdollEndEvent.Reason.EXPIRED;
        }
    }

    private static void cancelRecovery(ServerLevel level, UUID session) {
        RagdollState state = state(level, session);
        MobRagdollPartBlockEntity root = state == null ? null : rootBlock(level, state);
        if (root == null) return;
        CompoundTag sessionTag = root.ragdollIdentity().session();
        if (!sessionTag.contains(RECOVERY_KEY)) return;
        sessionTag.remove(RECOVERY_KEY);
        root.ragdollIdentity().session(sessionTag);
        root.setChanged();
        restoreMobMotors(session);
    }

    public static void tickPart(ServerLevel level, MobRagdollPartBlockEntity block,
                                RigidBodyHandle handle) {
        var id = block.ragdollIdentity();
        if (id.owner() == null || id.severed() || !block.renderAnchor()) return;
        var tag = id.assembly();
        if (!tag.hasUUID("EntityId")) return;
        CachedAssemblyState cached = ASSEMBLY_STATE_CACHE.get(id.owner());
        if (cached == null || cached.level() != level || cached.tick() != level.getGameTime()) {
            var family = dev.leo.sableplayerragdoll.physics.RagdollRelationships.members(block);
            if (family.isEmpty()) family = RagdollBlockOwnership.loadedBlocks(level);
            cached = state(level, id.owner(), tag, family);
        }
        var data = cached.data();
        var state = cached.state();
        if (state == null) return;
        if (beginJointCheck(level, id.owner())) {
            if (state.parts().size() == data.partIds().size()) persistHierarchy(level, state.parts());
            attachJoints(level, state.parts());
            if (id.session().getBoolean("MobRoot") && id.session().contains(RECOVERY_KEY)) {
                tuneRecoveryMotors(id.owner());
            }
        }
        if (id.session().getBoolean("MobRoot") && "MOB".equals(id.lifetime())
                && id.expiresAt() - level.getGameTime() <= RECOVERY_LEAD_TICKS
                && id.source() != null && level.getEntity(id.source()) instanceof LivingEntity source) {
            beginRecovery(level, id.owner(), source, MobRagdollEndEvent.Reason.EXPIRED);
        }
        if (id.session().getBoolean("MobRoot")) {
            if (id.session().contains(RECOVERY_KEY)) tickRecovery(level, block, handle);
            else tickWailing(level, id.owner(), state, block);
        }
        if (!id.session().getBoolean("MobRoot") || id.source() == null) return;
        if (!(level.getEntity(id.source()) instanceof LivingEntity source)
                || !MobRagdollSourceRecovery.matches(source, id.owner()) || !MobRagdollSourceRecovery.active(source)) return;
        var physics = SubLevelPhysicsSystem.get(level);
        if (physics != null) applyImpactDamage(level, source.getUUID(), state, physics, level.getGameTime());
        Vec3 position = sourcePosition(state, source);
        source.moveTo(position.x, position.y, position.z, source.getYRot(), source.getXRot());
        source.setDeltaMovement(Vec3.ZERO);
    }

    public static boolean expireFromBlock(ServerLevel level, UUID limb) {
        var block = findMobPart(level, limb);
        if (block == null || block.ragdollIdentity().severed()) return true;
        var id = block.ragdollIdentity();
        if (id.owner() != null) {
            CompoundTag recovery = recoveryTag(level, id.owner());
            if (recovery != null) {
                terminate(level, id.owner(), id.source(), recoveryReason(recovery), false);
                return true;
            }
            if (id.source() != null && level.getEntity(id.source()) instanceof LivingEntity source
                    && beginRecovery(level, id.owner(), source, MobRagdollEndEvent.Reason.EXPIRED)) {
                return false;
            }
            if (id.source() == null && "TIMED".equals(id.lifetime())
                    && dev.ryanhcode.sable.Sable.HELPER.getContaining(level, block.getBlockPos()) instanceof ServerSubLevel body) {
                RagdollRegistry.emitRemovalPuff(level, body);
            }
            terminate(level, id.owner(), id.source(), MobRagdollEndEvent.Reason.EXPIRED, false);
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
            if (!(entity instanceof LivingEntity livingEntity)
                    || !MobRagdollSourceRecovery.matches(livingEntity, pending.ragdollId)) {
                SPAWN_QUEUE.remove(pending);
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
                    for (var be : RagdollBlockOwnership.blocks(assembled.subLevel())) {
                        var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
                        identity.lifetime(pending.createdAt, "MOB", pending.entityUUID, pending.createdAt + (long) pending.durationTicks);
                        be.setChanged();
                    }
                    Vec3 desired = pending.base
                            .add(pending.right.scale(part.xOffset()))
                            .add(0.0, part.yOffset(), 0.0)
                            .add(pending.forward.scale(-part.zOffset()));
                    Quaterniond partModelRot = new Quaterniond(
                            -part.rotQx(), -part.rotQy(), part.rotQz(), part.rotQw());
                    Quaterniond orientation = new Quaterniond(pending.baseOrientation).mul(partModelRot);
                    movePartTo(level, assembled.subLevel(), assembled.anchorPlotPos(), desired, orientation);
                    pending.assembled.add(new SpawnedPart(PartGeometry.from(part), assembled.subLevel(), assembled.anchorPlotPos()));
                }
                pending.nextPartIndex++;
                budget--;
            }

            if (pending.nextPartIndex >= pending.parts.size()) {
                SPAWN_QUEUE.remove(pending);
                if (!pending.assembled.isEmpty()) {
                    finishAssembly(level, livingEntity, pending);
                } else {
                    terminate(level, pending.ragdollId, pending.entityUUID, MobRagdollEndEvent.Reason.RELEASED, false);
                }
            }
        }
    }

    private static void finishAssembly(ServerLevel level, LivingEntity entity, PendingAssembly pending) {
        invalidateAssemblyState(pending.ragdollId);
        List<SpawnedPart> spawnedParts = pending.assembled;
        boolean mobless = entity.getPersistentData().contains(MOBLESS_SOURCE);
        boolean permanent = mobless && pending.durationTicks == DEFAULT_MOBLESS_DURATION_TICKS;
        Map<String, UUID> partIds = new LinkedHashMap<>();
        Map<String, MobRagdollAssemblyData.PartInfo> partInfos = new LinkedHashMap<>();
        for (SpawnedPart spawned : spawnedParts) {
            PartGeometry ps = spawned.part();
            partIds.put(ps.partName(), limbId(spawned));
            partInfos.put(ps.partName(), new MobRagdollAssemblyData.PartInfo(ps.role(), (float) ps.pivotOffset().x, (float) ps.pivotOffset().y, (float) ps.pivotOffset().z,
                    (float) ps.centerOffset().x, (float) ps.centerOffset().y, (float) ps.centerOffset().z, ps.rotQx(), ps.rotQy(), ps.rotQz(), ps.rotQw()));
            for (var be : RagdollBlockOwnership.blocks(spawned.subLevel())) {
                var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
                if (!pending.ragdollId.equals(identity.owner())) continue;
                identity.lifetime(pending.createdAt, permanent ? "PERMANENT" : mobless ? "TIMED" : "MOB",
                        mobless ? null : entity.getUUID(), permanent ? -1 : pending.createdAt + (long) pending.durationTicks);
                be.setChanged();
            }
        }
        MobRagdollAssemblyData.write(level, new MobRagdollAssemblyData.Entry(pending.ragdollId, entity.getUUID(), pending.createdAt,
                pending.durationTicks, pending.corpseDurationTicks, pending.fallApartOnDeath,
                pending.base, partInfos, partIds, mobless));
        dev.leo.sableplayerragdoll.physics.RagdollRelationships.wire(spawnedParts.stream()
                .flatMap(part -> RagdollBlockOwnership.blocks(part.subLevel()).stream()).toList());
        persistHierarchy(level, spawnedParts);
        attachJoints(level, spawnedParts);
        if (pending.wailing != null) applyWailing(level, pending.ragdollId, pending.wailing);
        if (pending.linearVelocity.lengthSqr() > 0 || pending.angularVelocity.lengthSqr() > 0) {
            for (SpawnedPart spawned : spawnedParts) RigidBodyHandle.of(spawned.subLevel()).addLinearAndAngularVelocity(
                    new Vector3d(pending.linearVelocity.x, pending.linearVelocity.y, pending.linearVelocity.z),
                    new Vector3d(pending.angularVelocity.x, pending.angularVelocity.y, pending.angularVelocity.z));
        }
        if (mobless) {
            entity.discard();
        } else {
            hideRagdollSource(entity);
            syncClientSourceState(entity, true);
            if (!entity.isAlive()) retainCorpse(level, entity);
        }
    }

    private static void cleanupPartialAssembly(ServerLevel level, PendingAssembly pending) {
        terminate(level, pending.ragdollId, pending.entityUUID, MobRagdollEndEvent.Reason.RELEASED, false);
    }

    private static void terminate(ServerLevel level, UUID session, UUID sourceId, MobRagdollEndEvent.Reason reason, boolean discard) {
        if (session == null) return;
        var state = state(level, session);
        clearRestoreDeferral(session);
        if (sourceId != null) {
            NEXT_IMPACT_DAMAGE_TICK.remove(sourceId);
            NEXT_IMPACT_SOUND_TICK.remove(sourceId);
        }
        if (state != null) for (var part : state.parts()) LAST_VELOCITIES.remove(part.subLevel());
        forgetJoints(session);
        if (sourceId != null && level.getEntity(sourceId) instanceof LivingEntity source
                && MobRagdollSourceRecovery.matches(source, session)) {
            Vec3 velocity = state == null ? Vec3.ZERO : rootVelocity(state);
            if (!discard && state != null) {
                float yaw = bodyYaw(source);
                Vec3 position = sourcePosition(state, source);
                source.moveTo(position.x, position.y, position.z, yaw, source.getXRot());
                restoreRotation(source, yaw);
            }
            //restore before callbacks
            showRagdollSource(source);
            syncClientSourceState(source, false);
            if (discard) source.kill();
            NeoForge.EVENT_BUS.post(new MobRagdollEndEvent(source, velocity, reason));
        }
        for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
            if (!(be instanceof MobRagdollPartBlockEntity mob) || !session.equals(mob.ragdollIdentity().owner())) continue;
            level.setBlock(be.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        }
        invalidateAssemblyState(session);
    }

    public static void syncClientSourceState(ServerPlayer player, Entity entity) {
        if (entity.level() instanceof ServerLevel level && isActiveOrSavedRagdollSource(level, entity.getUUID())) {
            PacketDistributor.sendToPlayer(player, new MobRagdollSourceStatePacket(entity.getId(), true));
        }
    }

    private static void syncClientSourceState(Entity entity, boolean hidden) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, new MobRagdollSourceStatePacket(entity.getId(), hidden));
    }

    private static void removeSubLevelIfPresent(SubLevelContainer container, ServerSubLevel subLevel) {
        if (subLevel != null) RagdollCleanup.removePart(subLevel.getLevel(), subLevel);
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

    public static void explodeCreeperRagdoll(ServerLevel level, Creeper creeper, Vec3 center, float radius) {
        UUID session = sessionId(creeper);
        if (session == null || !MobRagdollSourceRecovery.matches(creeper, session)) return;
        RagdollState state = state(level, session);
        if (state == null) {
            retainCorpse(level, creeper);
            return;
        }

        forgetJoints(session);
        for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
            if (!(be instanceof MobRagdollPartBlockEntity mob)) continue;
            var identity = mob.ragdollIdentity();
            if (!session.equals(identity.owner()) || identity.severed()) continue;
            identity.parent(null);
            be.setChanged();
        }

        double baseSpeed = Math.max(5.0, radius * 2.2);
        Set<ServerSubLevel> launchedBodies = new java.util.HashSet<>();
        for (SpawnedPart part : state.parts()) {
            ServerSubLevel body = part.subLevel();
            if (body.isRemoved() || !launchedBodies.add(body)) continue;
            Vec3 partCenter = body.logicalPose().transformPosition(Vec3.atCenterOf(part.plotPos()));
            Vec3 offset = partCenter.subtract(center);
            if (offset.lengthSqr() < 1.0E-6) {
                offset = new Vec3(level.random.nextDouble() - 0.5, 0.25,
                        level.random.nextDouble() - 0.5);
            }
            double attenuation = Math.max(0.45, 1.0 - offset.length() / Math.max(1.0, radius * 2.5));
            Vec3 impulse = offset.add(0.0, 0.35, 0.0).normalize().scale(baseSpeed * attenuation);
            Vector3d angular = new Vector3d(
                    level.random.nextDouble() * 12.0 - 6.0,
                    level.random.nextDouble() * 12.0 - 6.0,
                    level.random.nextDouble() * 12.0 - 6.0);
            RigidBodyHandle handle = RigidBodyHandle.of(body);
            if (handle.isValid()) {
                handle.addLinearAndAngularVelocity(new Vector3d(impulse.x, impulse.y, impulse.z), angular);
            }
        }
        retainCorpse(level, creeper);
    }

    public static void applyKnockup(ServerLevel level, UUID session) {
        RagdollState state = state(level, session);
        if (state == null) return;
        Set<ServerSubLevel> bodies = new java.util.HashSet<>();
        for (SpawnedPart spawned : state.parts()) {
            ServerSubLevel body = spawned.subLevel();
            if (body.isRemoved() || !bodies.add(body)) continue;
            RigidBodyHandle.of(body).addLinearAndAngularVelocity(new Vector3d(0, KNOCKUP_STRENGTH, 0),
                    new Vector3d((Math.random() - 0.5) * 2, (Math.random() - 0.5) * 2, (Math.random() - 0.5) * 2));
        }
    }

    public static void applyKnockupForPart(ServerLevel level, BlockPos partPos) {
        if (level.getBlockEntity(partPos) instanceof MobRagdollPartBlockEntity block)
            applyKnockup(level, block.ragdollIdentity().owner());
    }

    private static void storeVelocity(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }
        Vector3d velocity = new Vector3d();
        RigidBodyHandle.of(subLevel).getLinearVelocity(velocity);
        LAST_VELOCITIES.put(subLevel, velocity);
    }

    private static MobRagdollPartBlockEntity rootBlock(ServerLevel level, RagdollState state) {
        if (state.parts().isEmpty()) return null;
        var block = level.getBlockEntity(selectRoot(state.parts()).plotPos());
        return block instanceof MobRagdollPartBlockEntity mob ? mob : null;
    }

    private static void tickWailing(ServerLevel level, UUID session, RagdollState state,
                                    MobRagdollPartBlockEntity root) {
        CompoundTag sessionTag = root.ragdollIdentity().session();
        if (!sessionTag.contains(WAILING_KEY)) return;
        CompoundTag wailing = sessionTag.getCompound(WAILING_KEY);
        long now = level.getGameTime();
        if (now >= wailing.getLong("EndTick")) {
            restoreMobMotors(session);
            sessionTag.remove(WAILING_KEY);
            root.ragdollIdentity().session(sessionTag);
            root.setChanged();
            return;
        }
        long start = wailing.getLong("StartTick");
        int interval = Math.max(1, wailing.getInt("IntervalTicks"));
        if (now < start || (now - start) % interval != 0) return;

        long step = (now - start) / interval;
        RandomSource random = RandomSource.create(wailing.getLong("Seed") ^ step * 0x9E3779B97F4A7C15L);
        Map<UUID, MobPartRole> roles = new java.util.HashMap<>();
        for (var part : state.parts()) roles.put(limbId(part), part.part().role());
        double stiffness = wailing.getDouble("Stiffness");
        for (var entry : JOINT_BY_CHILD.entrySet()) {
            LiveJoint joint = entry.getValue();
            if (!session.equals(joint.session()) || !joint.handle().isValid()) continue;
            MobPartRole role = roles.getOrDefault(entry.getKey(), MobPartRole.OTHER);
            if (role == MobPartRole.TORSO) continue;
            Vector3d target = randomWailingTarget(role, random);
            joint.handle().setMotor(ConstraintJointAxis.ANGULAR_X, target.x, stiffness, WAILING_DAMPING, false, 0.0);
            joint.handle().setMotor(ConstraintJointAxis.ANGULAR_Y, target.y, stiffness, WAILING_DAMPING, false, 0.0);
            joint.handle().setMotor(ConstraintJointAxis.ANGULAR_Z, target.z, stiffness, WAILING_DAMPING, false, 0.0);
        }
    }

    private static Vector3d randomWailingTarget(MobPartRole role, RandomSource random) {
        double pitch;
        double yaw;
        double roll;
        switch (role) {
            case HEAD -> { pitch = 18.0; yaw = 25.0; roll = 16.0; }
            case ARM, WING -> { pitch = 95.0; yaw = 35.0; roll = 80.0; }
            case LEG -> { pitch = 55.0; yaw = 20.0; roll = 45.0; }
            case TAIL, OTHER -> { pitch = 35.0; yaw = 25.0; roll = 35.0; }
            case TORSO -> { pitch = 0.0; yaw = 0.0; roll = 0.0; }
            default -> throw new IllegalStateException("Unexpected role: " + role);
        }
        return new Vector3d(randomRadians(random, pitch), randomRadians(random, yaw), randomRadians(random, roll));
    }

    private static double randomRadians(RandomSource random, double degrees) {
        return Math.toRadians((random.nextDouble() * 2.0 - 1.0) * degrees);
    }

    private static void restoreMobMotors(UUID session) {
        for (LiveJoint joint : JOINT_BY_CHILD.values()) {
            if (!session.equals(joint.session()) || !joint.handle().isValid()) continue;
            tuneAngularJoint(joint.handle());
        }
    }

    private static void tuneRecoveryMotors(UUID session) {
        for (LiveJoint joint : JOINT_BY_CHILD.values()) {
            if (!session.equals(joint.session()) || !joint.handle().isValid()) continue;
            for (ConstraintJointAxis axis : Set.of(
                    ConstraintJointAxis.ANGULAR_X,
                    ConstraintJointAxis.ANGULAR_Y,
                    ConstraintJointAxis.ANGULAR_Z)) {
                joint.handle().setMotor(axis, 0.0, RECOVERY_JOINT_STIFFNESS, RECOVERY_JOINT_DAMPING, false, 0.0);
            }
        }
    }

    private static void persistHierarchy(ServerLevel level, List<SpawnedPart> parts) {
        if (parts.isEmpty()) return;
        SpawnedPart root = selectRoot(parts);
        for (SpawnedPart child : parts) {
            var childBlock = level.getBlockEntity(child.plotPos());
            if (!(childBlock instanceof RagdollOwnedBlock owned)) continue;
            if (owned.ragdollIdentity().session().getBoolean("MobJointInitialized")) continue;
            SpawnedPart parent = child == root ? null : selectParent(child, parts, root);
            CompoundTag joint = new CompoundTag();
            joint.putBoolean("MobJointInitialized", true);
            joint.putBoolean("MobRoot", parent == null);
            if (parent != null) {
                Vec3 pivot = child.part().pivotOffset();
                putVector(joint, "ParentOffset", plotAnchor(parent, pivot.subtract(parent.part().centerOffset()))
                        .sub(Vec3.atCenterOf(parent.plotPos()).x, Vec3.atCenterOf(parent.plotPos()).y, Vec3.atCenterOf(parent.plotPos()).z));
                putVector(joint, "ChildOffset", plotAnchor(child, pivot.subtract(child.part().centerOffset()))
                        .sub(Vec3.atCenterOf(child.plotPos()).x, Vec3.atCenterOf(child.plotPos()).y, Vec3.atCenterOf(child.plotPos()).z));
                Quaterniond frame = new Quaterniond(parent.subLevel().logicalPose().orientation()).invert()
                        .mul(child.subLevel().logicalPose().orientation());
                joint.putDouble("FrameX", frame.x); joint.putDouble("FrameY", frame.y);
                joint.putDouble("FrameZ", frame.z); joint.putDouble("FrameW", frame.w);
            }
            UUID childLimb = limbId(child);
            for (var be : RagdollBlockOwnership.loadedBlocks(level)) {
                if (!(be instanceof MobRagdollPartBlockEntity mob) || !childLimb.equals(mob.ragdollIdentity().limb())) continue;
                mob.ragdollIdentity().parent(parent == null ? null : limbId(parent));
                mob.ragdollIdentity().session(joint);
                be.setChanged();
            }
        }
    }

    private static void putVector(CompoundTag tag, String key, Vector3d value) {
        CompoundTag vector = new CompoundTag();
        vector.putDouble("X", value.x); vector.putDouble("Y", value.y); vector.putDouble("Z", value.z);
        tag.put(key, vector);
    }

    private static Vector3d anchor(CompoundTag tag, String key, BlockPos pos) {
        var vector = tag.getCompound(key);
        return new Vector3d(pos.getX() + 0.5 + vector.getDouble("X"), pos.getY() + 0.5 + vector.getDouble("Y"),
                pos.getZ() + 0.5 + vector.getDouble("Z"));
    }

    private static boolean jointMatches(ServerLevel level, UUID childLimb, LiveJoint joint) {
        if (!level.hasChunkAt(joint.childPos()) || !level.hasChunkAt(joint.parentPos())) return false;
        if (!(level.getBlockEntity(joint.childPos()) instanceof MobRagdollPartBlockEntity child)
                || !(level.getBlockEntity(joint.parentPos()) instanceof MobRagdollPartBlockEntity parent)) return false;
        return childLimb.equals(child.ragdollIdentity().limb())
                && joint.parentLimb().equals(parent.ragdollIdentity().limb())
                && joint.parentLimb().equals(child.ragdollIdentity().parent())
                && joint.session().equals(child.ragdollIdentity().owner())
                && joint.session().equals(parent.ragdollIdentity().owner())
                && dev.ryanhcode.sable.Sable.HELPER.getContaining(level, child.getBlockPos()) == joint.childBody()
                && dev.ryanhcode.sable.Sable.HELPER.getContaining(level, parent.getBlockPos()) == joint.parentBody();
    }

    private static void attachJoints(ServerLevel level, List<SpawnedPart> parts) {
        var system = SubLevelPhysicsSystem.get(level);
        if (system == null || parts.isEmpty()) return;
        Map<UUID, SpawnedPart> byLimb = new java.util.HashMap<>();
        for (var part : parts) byLimb.put(limbId(part), part);
        for (SpawnedPart child : parts) {
            if (!(level.getBlockEntity(child.plotPos()) instanceof MobRagdollPartBlockEntity block)) continue;
            var id = block.ragdollIdentity();
            if (id.severed() || id.parent() == null) continue;
            SpawnedPart parent = byLimb.get(id.parent());
            LiveJoint existing = JOINT_BY_CHILD.get(id.limb());
            if (existing != null && existing.handle().isValid() && jointMatches(level, id.limb(), existing)) continue;
            removeJoint(id.limb());
            if (parent == null || parent.subLevel() == child.subLevel()) continue;
            var joint = id.session();
            if (!joint.getBoolean("MobJointInitialized")) continue;
            try {
                var config = SableConstraintCompat.generic(anchor(joint, "ParentOffset", parent.plotPos()),
                        anchor(joint, "ChildOffset", child.plotPos()),
                        new Quaterniond(joint.getDouble("FrameX"), joint.getDouble("FrameY"), joint.getDouble("FrameZ"), joint.getDouble("FrameW")),
                        new Quaterniond(), Set.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z));
                var handle = SableConstraintCompat.addConstraint(system.getPipeline(), parent.subLevel(), child.subLevel(), config);
                handle.setContactsEnabled(false);
                tuneAngularJoint(handle);
                JOINT_BY_CHILD.put(id.limb(), new LiveJoint(level, id.owner(), id.parent(), parent.subLevel(), child.subLevel(),
                        parent.plotPos(), child.plotPos(), handle));
            } catch (RuntimeException error) {
                SablePlayerRagdoll.LOGGER.warn("[mob-ragdoll] failed to restore joint for {}: {}", id.limb(), error.toString());
            }
        }
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
        for (var part : parts) {
            if (part.subLevel().getLevel().getBlockEntity(part.plotPos()) instanceof RagdollOwnedBlock block
                    && block.ragdollIdentity().session().getBoolean("MobRoot")) return part;
        }
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
        PartGeometry spawn = part.part();
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

    private record PartGeometry(MobPartRole role, String partName, String parentName, Vec3 centerOffset, Vec3 pivotOffset,
                                float rotQx, float rotQy, float rotQz, float rotQw, double volume) {
        static PartGeometry from(PartSpawn part) {
            return new PartGeometry(part.role(), part.partName(), part.parentName(), part.centerOffset(), part.pivotOffset(),
                    part.rotQx(), part.rotQy(), part.rotQz(), part.rotQw(), part.volume());
        }
    }

    private record SpawnedPart(PartGeometry part, ServerSubLevel subLevel, BlockPos plotPos) {
    }

    private record AssembledPart(ServerSubLevel subLevel, BlockPos anchorPlotPos) {
    }

    private record PendingLaunch(Vec3 linear, Vec3 angular, MobRagdollLaunchOptions options, long requestedTick, ServerLevel level, UUID session) {
    }

    private record RagdollState(List<SpawnedPart> parts, long spawnedAtTick, Vec3 preRagdollPos, int durationTicks) {
    }

    private static final class PendingAssembly {
        final UUID ragdollId;
        final long createdAt;
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
        final int corpseDurationTicks;
        final boolean fallApartOnDeath;
        final RagdollWailingOptions wailing;
        int nextPartIndex = 0;
        final List<SpawnedPart> assembled = new ArrayList<>();

        PendingAssembly(ServerLevel level, UUID entityUUID, int entityNetworkId, List<PartSpawn> parts,
                        Vec3 base, BlockPos baseBlockPos, Vec3 right, Vec3 forward, Quaterniond baseOrientation,
                        Vec3 linearVelocity, Vec3 angularVelocity, int durationTicks,
                        int corpseDurationTicks, boolean fallApartOnDeath, RagdollWailingOptions wailing,
                        UUID session, long createdAt) {
            this.ragdollId = session;
            this.createdAt = createdAt;
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
            this.corpseDurationTicks = corpseDurationTicks;
            this.fallApartOnDeath = fallApartOnDeath;
            this.wailing = wailing;
        }
    }
}
