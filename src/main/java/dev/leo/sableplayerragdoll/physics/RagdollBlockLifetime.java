package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.UUID;

public final class RagdollBlockLifetime {
    public static final String SOURCE_SESSION = "sable_player_ragdoll_session";
    public static final int CREATION_GRACE_TICKS = 40;
    private RagdollBlockLifetime() {}

    public static void configure(ServerSubLevel part, String mode, UUID source, int ticks) {
        long now = part.getLevel().getGameTime();
        for (var be : RagdollBlockOwnership.blocks(part)) {
            ((RagdollOwnedBlock) be).ragdollIdentity().lifetime(now, mode, source, ticks < 0 ? -1 : now + ticks);
            be.setChanged();
        }
    }

    public static void playerPolicy(ServerSubLevel root, CompoundTag session) {
        UUID owner = RagdollBlockOwnership.ownerOf(root);
        if (owner == null) return;
        for (var be : RagdollBlockOwnership.loadedBlocks(root.getLevel())) {
            var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
            if (!owner.equals(identity.owner())) continue;
            if (session.getBoolean("expiring") && !owner.equals(identity.limb())) continue;
            applyPlayerPolicy(be, session);
        }
    }

    private static void applyPlayerPolicy(BlockEntity be, CompoundTag session) {
        var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
        identity.session(session);
        long created = session.getLong("startTick");
        UUID source = session.hasUUID("playerId") ? session.getUUID("playerId") : null;
        String mode = source != null ? "RIDER" : "TIMED";
        int ticks = (RagdollSettings.expireAfterDuration() ? RagdollSettings.ragdollDurationTicks()
                : RagdollSettings.step1BodyLifetimeTicks()) * 3;
        if (session.getBoolean("nonPlayer")) {
            switch (session.getString("despawnMode")) {
                case "AFTER_TICKS" -> ticks = session.getInt("despawnTicks");
                case "BELOW_SPEED" -> mode = "SPEED";
                default -> mode = "PERMANENT";
            }
        }
        identity.lifetime(created, mode, source, mode.equals("TIMED") ? created + ticks : -1);
        be.setChanged();
    }

    public static void tick(BlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level) || be.isRemoved()) return;
        RagdollRelationships.withBlock(be, () -> tickOwned(be, level));
    }

    private static void tickOwned(BlockEntity be, ServerLevel level) {
        var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
        var containing = Sable.HELPER.getContaining(level, be.getBlockPos());
        ServerSubLevel part = containing instanceof ServerSubLevel server ? server : null;
        if (identity.createdAt() < 0) {
            migrate(be, part);
        }
        if ("RIDER".equals(identity.lifetime()) && !validRider(level, identity)) {
            remove(be, level, part, identity);
            return;
        }
        long now = level.getGameTime();

        boolean timedOut = ("TIMED".equals(identity.lifetime()) || "MOB".equals(identity.lifetime()))
                && identity.expiresAt() >= 0 && now >= identity.expiresAt();
        if (identity.orphanExpired(now) || timedOut) {
            expireBlock(be, level, part, identity);
            return;
        }
        RagdollRelationships.check(be);
        if (!identity.ready(now)) return;
        if (part != null && be instanceof RagdollPartBlockEntity && identity.owner() != null
                && identity.owner().equals(identity.limb())
                && level.getGameTime() - identity.createdAt() >= CREATION_GRACE_TICKS) {
            RagdollBlockOwnership.withOwner(identity.owner(), () -> RagdollSessionManager.tickRoot(level, part));
        }
        long age = level.getGameTime() - identity.createdAt();
        // Stagger the independent checks; never depend on physics bodies being awake.
        if (now < identity.nextPolicyCheck) return;
        identity.nextPolicyCheck = now + 10 + level.random.nextInt(11);
        if (age < CREATION_GRACE_TICKS) return;
        boolean keep = switch (identity.lifetime()) {
            case "PERMANENT" -> true;
            case "TIMED" -> identity.expiresAt() >= 0 && level.getGameTime() < identity.expiresAt();
            case "RIDER" -> validRider(level, identity);
            case "MOB" -> validMob(level, identity);
            case "SPEED" -> {
                var root = identity.owner() == null ? part : RagdollBlockOwnership.root(level, identity.owner());
                yield root != null && speed(root) > identity.session().getDouble("despawnSpeed");
            }
            default -> false;
        };
        if (be instanceof RagdollPartBlockEntity && !identity.severed()) {
            var root = identity.owner() == null ? null : RagdollBlockOwnership.root(level, identity.owner());
            // Parent absence is enforced by this block's fixed orphan deadline.
            // A root with no identity is malformed, not a permanent orphan.
            keep &= identity.owner() != null;
            if (identity.owner() != null && identity.owner().equals(identity.limb()))
                keep &= !identity.session().getBoolean("expiring");
            if (root == null && identity.owner() != null) RagdollAssemblyHelper.clearRuntime(identity.owner());
        }
        if (keep) return;
        expireBlock(be, level, part, identity);
    }

    private static void expireBlock(BlockEntity be, ServerLevel level, ServerSubLevel part, RagdollBlockIdentity identity) {
        if (!identity.severed() && be instanceof MobRagdollPartBlockEntity
                && identity.limb() != null) {
            if (!MobRagdollAssembly.expireFromBlock(level, identity.limb())) return;
        }
        remove(be, level, part, identity);
    }

    private static void remove(BlockEntity be, ServerLevel level, ServerSubLevel part, RagdollBlockIdentity identity) {
        if (!be.isRemoved()) {
            if (be instanceof RagdollPartBlockEntity && identity.owner() != null
                    && identity.owner().equals(identity.limb())) {
                RagdollAssemblyHelper.clearRuntime(identity.owner());
                if (part != null) RagdollBlockOwnership.withOwner(identity.owner(), () -> RagdollSessionManager.unregister(part));
            }
            be.setChanged();
            level.setBlock(be.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static boolean validRider(ServerLevel level, RagdollBlockIdentity identity) {
        if (identity.source() == null || identity.owner() == null) return false;
        var source = level.getEntity(identity.source());
        if (!(source instanceof LivingEntity living) || !living.isAlive() || !source.isPassenger()) return false;
        var tag = source.getVehicle().getPersistentData();
        if (!tag.hasUUID(SOURCE_SESSION)) {
            var containing = Sable.HELPER.getContaining(level, source.getVehicle().blockPosition());
            if (containing instanceof ServerSubLevel part) {
                for (var be : RagdollBlockOwnership.blocks(part)) {
                    var candidate = ((RagdollOwnedBlock) be).ragdollIdentity();
                    if (identity.owner().equals(candidate.owner()) && identity.source().equals(candidate.source())) {
                        tag.putUUID(SOURCE_SESSION, identity.owner());
                        break;
                    }
                }
            }
        }
        return tag.hasUUID(SOURCE_SESSION) && identity.owner().equals(tag.getUUID(SOURCE_SESSION));
    }

    private static boolean validMob(ServerLevel level, RagdollBlockIdentity identity) {
        if (identity.source() == null || identity.owner() == null || level.getGameTime() >= identity.expiresAt()) return false;
        var source = level.getEntity(identity.source());
        // A temporarily unloaded source is permitted only until the block's fixed deadline.
        if (source == null) return true;
        var tag = source.getPersistentData();
        return source.isAlive() && tag.hasUUID(SOURCE_SESSION) && identity.owner().equals(tag.getUUID(SOURCE_SESSION));
    }

    private static double speed(ServerSubLevel part) {
        var system = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(part.getLevel());
        if (system == null || part.isRemoved()) return 0.0;
        var handle = system.getPhysicsHandle(part);
        return handle == null || !handle.isValid() ? 0.0 : handle.getLinearVelocity().length();
    }

    private static void migrate(BlockEntity be, ServerSubLevel part) {
        var level = (ServerLevel) be.getLevel();
        var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
        identity.lifetime(level.getGameTime(), "UNBOUND", null, -1);
        if (identity.severed()) identity.lifetime(level.getGameTime(), "TIMED", null, level.getGameTime() + 1200);
        be.setChanged();
    }
}
